package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TierWatermarkAndCoolingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeRect(): Rect {
        val r = Rect()
        r.left = 0
        r.top = 0
        r.right = 20
        r.bottom = 20
        return r
    }

    @Test
    fun demotionStartsAt80PercentWatermarkInsteadOfWaitingFor100Percent() = runTest {
        val historyDir = tempFolder.newFolder("watermark_80")
        val hot = HotStore()
        val warm = WarmStore()
        val cold = ColdStore(historyDir)

        // Hot budget = 10,000 bytes -> 80% watermark = 8,000 bytes
        val config = TierConfig(
            hotBudgetBytes = 10_000L,
            warmBudgetBytes = 10_000L,
            coldMaxBytes = 500_000L,
            infiniteUndo = true,
            autoBudgets = false,
            demotionWatermark = 0.80,
            largeStrokeRatio = 0.90 // isolate watermark check from oversized check
        )
        val policy = TierPolicy(config, hot, warm, cold)

        val raw = ByteArray(1600) { (it % 37).toByte() }
        val comp = PatchCodec.compress(raw)

        // Add 3 patches of 3,000 bytes = 9,000 bytes (90% of 10,000 budget: < 100%, but > 80%!)
        for (i in 1..3) {
            hot.add(
                Patch(
                    id = i.toLong(),
                    rect = makeRect(),
                    state = PatchState.HOT,
                    sizeBytes = 3_000L,
                    compressedBytes = comp.compressedBytes.clone(),
                    rawLength = raw.size,
                    crc32 = comp.crc32
                )
            )
        }
        assertEquals(9_000L, hot.totalBytes)

        policy.enforceBudgets()

        // Must demote down to <= 8,000 bytes (80%) rather than staying at 9,000 bytes!
        assertTrue("Hot (${hot.totalBytes}) must drop <= 80% watermark (8000)", hot.totalBytes <= 8_000L)
        assertEquals(2, hot.count)
        assertEquals(1, warm.count)
    }

    @Test
    fun oversizedStrokeProgressivelyDemotesEvenBelow80PercentTotal() = runTest {
        val historyDir = tempFolder.newFolder("oversized_stroke")
        val hot = HotStore()
        val warm = WarmStore()
        val cold = ColdStore(historyDir)

        // Hot budget = 10,000 bytes; 80% watermark = 8,000; largeStrokeRatio = 0.45 (4,500 bytes)
        val config = TierConfig(
            hotBudgetBytes = 10_000L,
            warmBudgetBytes = 10_000L,
            infiniteUndo = true,
            autoBudgets = false,
            demotionWatermark = 0.80,
            largeStrokeRatio = 0.45
        )
        val policy = TierPolicy(config, hot, warm, cold)

        val raw = ByteArray(1600) { (it % 19).toByte() }
        val comp = PatchCodec.compress(raw)

        // Single stroke of 6,000 bytes (60% of budget: below 80% watermark, but > 45% largeStrokeRatio!)
        hot.add(
            Patch(
                id = 1L,
                rect = makeRect(),
                state = PatchState.HOT,
                sizeBytes = 6_000L,
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
        )

        policy.enforceBudgets()

        assertEquals("Oversized stroke should be pushed down to Warm", 0, hot.count)
        assertEquals(1, warm.count)
    }

    @Test
    fun idleThermalCoolingGraduallyPushesHotToWarmAndWarmToCold() = runTest {
        val historyDir = tempFolder.newFolder("idle_cooling")
        var nowMs = 1_000_000L
        val hot = HotStore()
        val warm = WarmStore()
        val cold = ColdStore(historyDir)

        val config = TierConfig(
            hotBudgetBytes = 50_000L,
            warmBudgetBytes = 50_000L,
            infiniteUndo = true,
            autoBudgets = false,
            idleCoolingEnabled = true,
            hotCoolAfterMs = 3_000L,
            warmCoolAfterMs = 8_000L
        )
        val policy = TierPolicy(config, hot, warm, cold, clockMs = { nowMs })

        val raw = ByteArray(1200) { (it % 23).toByte() }
        val comp = PatchCodec.compress(raw)

        for (i in 1..3) {
            hot.add(
                Patch(
                    id = i.toLong(),
                    rect = makeRect(),
                    state = PatchState.HOT,
                    sizeBytes = 2_000L,
                    compressedBytes = comp.compressedBytes.clone(),
                    rawLength = raw.size,
                    crc32 = comp.crc32,
                    createdAtMs = nowMs
                )
            )
        }

        // Before idle threshold (1s): nothing cools yet
        nowMs += 1_000L
        policy.coolStep(idleMs = 1_000L)
        assertEquals(3, hot.count)
        assertEquals(0, warm.count)

        // Idle for 10s (past hotCoolAfterMs * 3): each tick cools 1 stroke Hot -> Warm
        repeat(3) {
            nowMs += 3_500L
            policy.coolStep(idleMs = 10_000L)
        }
        assertEquals("All Hot patches cooled into Warm/Cold", 0, hot.count)
        assertTrue(warm.count + cold.count == 3)

        // Idle for 25s (past warmCoolAfterMs * 3): remaining Warm patches cool down to Cold disk
        var guard = 6
        while (policy.coolStep(idleMs = 25_000L) && guard-- > 0) {
            nowMs += 2_000L
        }
        assertEquals(0, hot.count)
        assertEquals(0, warm.count)
        assertEquals("All 3 steps preserved in Cold with 0 RAM", 3, cold.count)
    }

    @Test
    fun clearNowImmediatelyPurgesUndoRedoAndDeletesColdCacheFiles() = runTest {
        val historyDir = tempFolder.newFolder("clear_now_exit")
        val history = TieredHistory(
            historyDir = historyDir,
            tierConfig = TierConfig(
                hotBudgetBytes = 500L,
                warmBudgetBytes = 100L,
                infiniteUndo = true,
                autoBudgets = false,
                asyncDemotion = false
            )
        )

        val raw = ByteArray(1600) { (it % 29).toByte() }
        val comp = PatchCodec.compress(raw)
        for (i in 1..4) {
            history.commit(
                Patch(
                    id = i.toLong(),
                    rect = makeRect(),
                    state = PatchState.HOT,
                    sizeBytes = 1600L,
                    compressedBytes = comp.compressedBytes.clone(),
                    rawLength = raw.size,
                    crc32 = comp.crc32
                )
            )
        }
        assertTrue("Cold files must exist before exit clear", history.stats().coldSteps > 0)
        assertTrue(historyDir.listFiles { _, name -> name.endsWith(".chrf") }?.isNotEmpty() == true)

        // Simulate Save Artwork or App Exit cache wipe
        history.clearNow()

        val after = history.stats()
        assertEquals(0, after.totalSteps)
        assertEquals(0, after.redoSteps)
        assertEquals(0L, after.hotBytes)
        assertEquals(0L, after.warmBytes)
        assertEquals(0L, after.coldBytes)
        val lingering = historyDir.listFiles { _, name -> name.endsWith(".chrf") || name.endsWith(".tmp") }
        assertTrue("All Cold swap files must be synchronously deleted", lingering.isNullOrEmpty())
    }

    @Test
    fun rapidBurst200StrokesWithAsyncDemotionAndOversizedStrokesNeverRaces() = kotlinx.coroutines.runBlocking {
        val historyDir = tempFolder.newFolder("burst_200_stress")
        val history = TieredHistory(
            historyDir = historyDir,
            tierConfig = TierConfig(
                hotBudgetBytes = 8_000L,
                warmBudgetBytes = 4_000L,
                infiniteUndo = true,
                autoBudgets = false,
                asyncDemotion = true,
                demotionWatermark = 0.80,
                largeStrokeRatio = 0.40,
                idleCoolingEnabled = true,
                hotCoolAfterMs = 200L,
                warmCoolAfterMs = 400L
            )
        )

        val raw = ByteArray(2400) { (it % 41).toByte() }
        val comp = PatchCodec.compress(raw)

        // Rapidly commit 220 strokes across async demotion + backpressure thresholds
        for (i in 1..220) {
            history.commit(
                Patch(
                    id = i.toLong(),
                    rect = makeRect(),
                    state = PatchState.HOT,
                    sizeBytes = if (i % 3 == 0) 5_000L else 2_400L,
                    compressedBytes = comp.compressedBytes.clone(),
                    rawLength = raw.size,
                    crc32 = comp.crc32
                )
            )
        }
        history.settle()
        val st = history.stats()
        assertEquals(220, st.totalSteps)
        assertTrue(st.hotBytes <= 8_000L)
        history.clearNow()
    }
}
