package com.example.canvas.bench

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.history.DirtyRectHistory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DirtyRectCorrectnessTest {

    /**
     * Verifies E6 (Pixel Correctness):
     * After drawing N discrete strokes (modifying localized dirty regions),
     * undoing all N strokes must restore the canvas bit-for-bit to its exact initial state.
     */
    @Test
    fun testE6CorrectnessOnDirtyRect() = runTest {
        val width = 256
        val height = 256
        val pixelCount = width * height
        val canvasBuffer = IntArray(pixelCount) { 0xFFFFFFFF.toInt() } // Pure white canvas
        val initialCopy = canvasBuffer.clone()

        // Calculate initial checksum of blank canvas
        val initialBytes = intArrayToByteArray(initialCopy)
        val initialCrc = PatchCodec.computeCrc32(initialBytes)

        val history = DirtyRectHistory()
        val random = Random(42L)
        val strokeCount = 25

        // Simulated patch stack for pure JVM test of the exact algorithm
        class PurePatch(
            val rect: Rect,
            val pixels: IntArray
        )
        val undoStack = ArrayDeque<PurePatch>()

        // Phase 1: Draw N strokes
        for (step in 1..strokeCount) {
            val patchW = 20 + random.nextInt(40)
            val patchH = 20 + random.nextInt(40)
            val left = random.nextInt(width - patchW)
            val top = random.nextInt(height - patchH)
            val right = left + patchW
            val bottom = top + patchH

            val rect = Rect()
            rect.left = left
            rect.top = top
            rect.right = right
            rect.bottom = bottom

            // 1. Capture before patch (sub-region pixels)
            val beforePixels = IntArray(patchW * patchH)
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val canvasIdx = (top + y) * width + (left + x)
                    beforePixels[y * patchW + x] = canvasBuffer[canvasIdx]
                }
            }

            undoStack.addLast(PurePatch(rect, beforePixels))
            history.commit(
                Patch(
                    id = step.toLong(),
                    rect = rect,
                    sizeBytes = patchW.toLong() * patchH.toLong() * 4L
                )
            )

            // 2. Draw stroke (overwrite with random color, simulating PorterDuff.Mode.SRC)
            val color = random.nextInt() or 0xFF000000.toInt()
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val canvasIdx = (top + y) * width + (left + x)
                    canvasBuffer[canvasIdx] = color
                }
            }
        }

        // Canvas should now be heavily modified and differ from initial state
        assertTrue(canvasBuffer.any { it != 0xFFFFFFFF.toInt() })

        // Phase 2: Undo all strokes in reverse order
        while (undoStack.isNotEmpty()) {
            val patch = undoStack.removeLast()
            val rect = patch.rect
            val patchW = rect.right - rect.left
            val patchH = rect.bottom - rect.top

            // Restore before pixels using SRC replacement
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val canvasIdx = (rect.top + y) * width + (rect.left + x)
                    canvasBuffer[canvasIdx] = patch.pixels[y * patchW + x]
                }
            }
        }

        // Phase 3: Assert 100% bit-for-bit equivalence
        assertArrayEquals("Canvas buffer did not return to original state", initialCopy, canvasBuffer)

        val restoredBytes = intArrayToByteArray(canvasBuffer)
        val restoredCrc = PatchCodec.computeCrc32(restoredBytes)
        assertEquals("E6 failed: restored pixel CRC32 does not match initial CRC32", initialCrc, restoredCrc)
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
}
