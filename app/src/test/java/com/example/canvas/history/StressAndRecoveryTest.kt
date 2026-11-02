package com.example.canvas.history

import android.content.ComponentCallbacks2
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

@Suppress("DEPRECATION")
class StressAndRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testStressCompleteEvictsAllHotPatches() = runTest {
        val historyDir = tempFolder.newFolder("history_stress")
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(historyDir)

        val config = TierConfig(
            hotBudgetBytes = 100_000L,
            warmBudgetBytes = 100_000L,
            coldMaxBytes = 10_000_000L,
            coldMaxSteps = 1000
        )
        val policy = TierPolicy(config, hotStore, warmStore, coldStore)

        val raw = ByteArray(1000) { (it % 50).toByte() }
        val comp = PatchCodec.compress(raw)

        val patchList = mutableListOf<Patch>()
        // Commit 10 patches to HotStore
        for (i in 1..10) {
            val rect = Rect()
            rect.left = 0
            rect.top = 0
            rect.right = 10
            rect.bottom = 10
            val p = Patch(
                id = i.toLong(),
                rect = rect,
                state = PatchState.HOT,
                sizeBytes = 1000L,
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            hotStore.add(p)
            patchList.add(p)
        }

        assertEquals(10, hotStore.count)
        assertEquals(10_000L, hotStore.totalBytes)

        // Trigger severe Memory Stress (TRIM_MEMORY_COMPLETE = 80)
        policy.applyTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        // Hot must be completely cleared down to 0
        assertEquals("Hot store count must be 0 after severe stress", 0, hotStore.count)
        assertEquals("Hot store bytes must be 0 after severe stress", 0L, hotStore.totalBytes)

        // Total patches across Warm + Cold must still equal exactly 10 (zero data loss)
        val remainingCount = warmStore.count + coldStore.count
        assertEquals(10, remainingCount)

        // Continue adding patches after stress (Recovery)
        val newPatch = Patch(
            id = 11L,
            rect = Rect(0, 0, 10, 10),
            state = PatchState.HOT,
            sizeBytes = 1000L,
            compressedBytes = comp.compressedBytes.clone(),
            rawLength = raw.size,
            crc32 = comp.crc32
        )
        hotStore.add(newPatch)
        assertEquals(1, hotStore.count)
        assertEquals(1000L, hotStore.totalBytes)
    }

    @Test
    fun testModerateAndMildTrimMemoryLevels() = runTest {
        val historyDir = tempFolder.newFolder("history_trim_levels")
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(historyDir)

        val config = TierConfig(
            hotBudgetBytes = 10_000L,
            warmBudgetBytes = 10_000L
        )
        val policy = TierPolicy(config, hotStore, warmStore, coldStore)

        val raw = ByteArray(1000) { 0 }
        val comp = PatchCodec.compress(raw)

        // Populate 8 KB in Hot (under 10 KB budget)
        for (i in 1..8) {
            hotStore.add(
                Patch(
                    id = i.toLong(),
                    rect = Rect(0, 0, 10, 10),
                    state = PatchState.HOT,
                    sizeBytes = 1000L,
                    compressedBytes = comp.compressedBytes.clone(),
                    rawLength = raw.size,
                    crc32 = comp.crc32
                )
            )
        }
        assertEquals(8, hotStore.count)
        assertEquals(8000L, hotStore.totalBytes)

        // Mild trim (level 20 UI_HIDDEN): reduces Hot budget to 50% (5,000 bytes)
        policy.applyTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertTrue(hotStore.totalBytes <= 5000L)
        assertTrue(hotStore.count <= 5)

        // Moderate trim (level 60 MODERATE): reduces Hot budget to 25% (2,500 bytes)
        policy.applyTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        assertTrue(hotStore.totalBytes <= 2500L)
        assertTrue(hotStore.count <= 2)
    }
}
