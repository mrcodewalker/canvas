package com.example.canvas.bench

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import com.example.canvas.history.ColdFileCodec
import com.example.canvas.history.ColdStore
import com.example.canvas.history.HotStore
import com.example.canvas.history.TierConfig
import com.example.canvas.history.TierPolicy
import com.example.canvas.history.TieredHistory
import com.example.canvas.history.WarmStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Random

class TieredBenchmarkE6Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDraw300LargeStrokesNoOOM() = runTest {
        val historyDir = tempFolder.newFolder("history_300")
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(historyDir)

        // Low budgets for test environment: Hot = 500 KB, Warm = 50 KB
        val hotBudget = 500_000L
        val warmBudget = 50_000L
        val config = TierConfig(
            hotBudgetBytes = hotBudget,
            warmBudgetBytes = warmBudget,
            coldMaxBytes = 500L * 1024 * 1024,
            coldMaxSteps = 1000
        )
        val policy = TierPolicy(config, hotStore, warmStore, coldStore)

        val totalStrokes = 300
        val random = Random(42L)

        // Generate pseudorandom bytes so compressed size is realistic (~5,000 bytes)
        val samplePatchBytes = 50_000
        val randomBytes = ByteArray(samplePatchBytes)
        random.nextBytes(randomBytes)
        val sampleCompressed = PatchCodec.compress(randomBytes)

        for (step in 1..totalStrokes) {
            val rect = Rect()
            rect.left = random.nextInt(100)
            rect.top = random.nextInt(100)
            rect.right = rect.left + 500
            rect.bottom = rect.top + 500

            val patch = Patch(
                id = step.toLong(),
                rect = rect,
                state = PatchState.HOT,
                sizeBytes = samplePatchBytes.toLong(),
                compressedBytes = sampleCompressed.compressedBytes.clone(),
                rawLength = samplePatchBytes,
                crc32 = sampleCompressed.crc32
            )

            hotStore.add(patch)

            // Asynchronously enforce budgets
            policy.enforceBudgets()
        }

        // 1. All 300 steps must be stored across the 3 tiers
        val totalStored = hotStore.count + warmStore.count + coldStore.count
        assertEquals("Total stored steps must equal 300", 300, totalStored)

        // 2. HotStore memory must remain bounded under budget (non-linear growth)
        assertTrue("Hot RAM (${hotStore.totalBytes}) must not exceed budget ($hotBudget)", hotStore.totalBytes <= hotBudget + samplePatchBytes)

        // 3. WarmStore memory must remain bounded under budget
        assertTrue("Warm RAM (${warmStore.totalBytes}) must not exceed budget ($warmBudget)", warmStore.totalBytes <= warmBudget + sampleCompressed.compressedBytes.size)

        // 4. Cold tier must hold the majority of older steps safely swapped to disk
        assertTrue("Cold store count (${coldStore.count}) must be greater than 200", coldStore.count > 200)
        assertTrue("Cold files must exist on disk", coldStore.totalBytes > 0)
    }

    @Test
    fun testE6CorrectnessOnTiered() = runTest {
        val historyDir = tempFolder.newFolder("history_e6")
        val width = 256
        val height = 256
        val canvasBuffer = IntArray(width * height) { 0xFFFFFFFF.toInt() } // Pure white
        val initialCopy = canvasBuffer.clone()

        val initialBytes = intArrayToByteArray(initialCopy)
        val initialCrc = PatchCodec.computeCrc32(initialBytes)

        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(historyDir)

        // Small budgets so patches are demoted across HOT -> WARM -> COLD
        val config = TierConfig(
            hotBudgetBytes = 2_000L,
            warmBudgetBytes = 200L,
            coldMaxBytes = 100_000_000L,
            coldMaxSteps = 1000
        )
        val policy = TierPolicy(config, hotStore, warmStore, coldStore)

        val undoStack = ArrayDeque<Patch>()
        val random = Random(123L)
        val strokeCount = 40

        // Phase 1: Draw 40 strokes
        for (step in 1..strokeCount) {
            val patchW = 20 + random.nextInt(30)
            val patchH = 20 + random.nextInt(30)
            val left = random.nextInt(width - patchW)
            val top = random.nextInt(height - patchH)

            val rect = Rect()
            rect.left = left
            rect.top = top
            rect.right = left + patchW
            rect.bottom = top + patchH

            // Capture before patch pixels
            val beforePixels = IntArray(patchW * patchH)
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val idx = (top + y) * width + (left + x)
                    beforePixels[y * patchW + x] = canvasBuffer[idx]
                }
            }

            val rawBytes = intArrayToByteArray(beforePixels)
            val comp = PatchCodec.compress(rawBytes)

            val patch = Patch(
                id = step.toLong(),
                rect = rect,
                state = PatchState.HOT,
                sizeBytes = rawBytes.size.toLong(),
                compressedBytes = comp.compressedBytes,
                rawLength = rawBytes.size,
                crc32 = comp.crc32
            )

            undoStack.addLast(patch)
            hotStore.add(patch)

            // Apply degradation policy
            policy.enforceBudgets()

            // Draw new color on canvas (overwrite)
            val color = random.nextInt() or 0xFF000000.toInt()
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val idx = (top + y) * width + (left + x)
                    canvasBuffer[idx] = color
                }
            }
        }

        // Verify that patches have successfully transitioned across tiers
        assertTrue("Cold store must have received demoted patches", coldStore.count > 0)

        // Phase 2: Undo all strokes in reverse order
        while (undoStack.isNotEmpty()) {
            val patch = undoStack.removeLast()
            val rect = patch.rect
            val patchW = rect.right - rect.left
            val patchH = rect.bottom - rect.top

            // Resolve raw bytes based on current tier representation
            val restoredBytes = when (patch.state) {
                PatchState.HOT, PatchState.COMPRESSING, PatchState.WARM, PatchState.WRITING -> {
                    PatchCodec.decompress(patch.compressedBytes!!, patch.rawLength, patch.crc32)
                }
                PatchState.COLD -> {
                    val payload = coldStore.readPayload(patch)
                    PatchCodec.decompress(payload.compressedBytes, payload.rawLength, payload.crc32)
                }
            }

            val restoredPixels = byteArrayToIntArray(restoredBytes)

            // Overwrite canvas with restored pixels (SRC mode)
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val idx = (rect.top + y) * width + (rect.left + x)
                    canvasBuffer[idx] = restoredPixels[y * patchW + x]
                }
            }
        }

        // Phase 3: Assert E6 correctness
        assertArrayEquals("Canvas buffer must match initial white state after complete undo", initialCopy, canvasBuffer)

        val finalBytes = intArrayToByteArray(canvasBuffer)
        val finalCrc = PatchCodec.computeCrc32(finalBytes)
        assertEquals("E6 CRC32 mismatch", initialCrc, finalCrc)
    }

    private fun intArrayToByteArray(ints: IntArray): ByteArray {
        val bytes = ByteArray(ints.size * 4)
        for (i in ints.indices) {
            val v = ints[i]
            bytes[i * 4] = (v shr 24).toByte()
            bytes[i * 4 + 1] = (v shr 16).toByte()
            bytes[i * 4 + 2] = (v shr 8).toByte()
            bytes[i * 4 + 3] = v.toByte()
        }
        return bytes
    }

    private fun byteArrayToIntArray(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size / 4)
        for (i in ints.indices) {
            val b0 = bytes[i * 4].toInt() and 0xFF
            val b1 = bytes[i * 4 + 1].toInt() and 0xFF
            val b2 = bytes[i * 4 + 2].toInt() and 0xFF
            val b3 = bytes[i * 4 + 3].toInt() and 0xFF
            ints[i] = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
        return ints
    }
}
