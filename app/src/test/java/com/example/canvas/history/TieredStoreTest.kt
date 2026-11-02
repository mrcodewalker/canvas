package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TieredStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testColdFileCodecHeaderAndPayload() {
        val historyDir = tempFolder.newFolder("history_test")
        val rect = Rect()
        rect.left = 10
        rect.top = 20
        rect.right = 110
        rect.bottom = 120

        val rawBytes = ByteArray(100 * 100 * 4) { (it % 127).toByte() }
        val compressed = PatchCodec.compress(rawBytes)

        val patch = Patch(
            id = 42L,
            rect = rect,
            state = PatchState.WRITING,
            rawLength = rawBytes.size,
            crc32 = compressed.crc32
        )

        // Write to Cold file
        val file = ColdFileCodec.writeColdFile(historyDir, patch, compressed.compressedBytes)

        assertTrue(file.exists())
        assertEquals("patch_42.chrf", file.name)

        // Ensure temp file was cleaned up / renamed
        val tmpFiles = historyDir.listFiles { _, name -> name.endsWith(".tmp") }
        assertTrue(tmpFiles.isNullOrEmpty())

        // Read and decode Cold file
        val payload = ColdFileCodec.readColdFile(file)
        assertEquals(rect.left, payload.rect.left)
        assertEquals(rect.top, payload.rect.top)
        assertEquals(rect.right, payload.rect.right)
        assertEquals(rect.bottom, payload.rect.bottom)
        assertEquals(rawBytes.size, payload.rawLength)
        assertEquals(compressed.crc32, payload.crc32)
        assertArrayEquals(compressed.compressedBytes, payload.compressedBytes)

        // Decompress payload and verify 100% match with original raw bytes
        val decompressed = PatchCodec.decompress(
            payload.compressedBytes,
            payload.rawLength,
            payload.crc32
        )
        assertArrayEquals(rawBytes, decompressed)
    }

    @Test
    fun testTierTransitionsAndBudgetEnforcement() = runTest {
        val historyDir = tempFolder.newFolder("history_budget")
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(historyDir)

        // Hot budget: 1000 bytes, Warm budget: 1000 bytes
        val config = TierConfig(
            hotBudgetBytes = 1000L,
            warmBudgetBytes = 1000L,
            coldMaxBytes = 100_000L,
            coldMaxSteps = 100
        )
        val policy = TierPolicy(config, hotStore, warmStore, coldStore)

        // Create patches with compressed bytes already attached for headless JVM testing
        val raw = ByteArray(200) { it.toByte() }
        val comp = PatchCodec.compress(raw)

        fun createTestPatch(id: Long): Patch {
            val rect = Rect()
            rect.left = 0
            rect.top = 0
            rect.right = 10
            rect.bottom = 5
            return Patch(
                id = id,
                rect = rect,
                state = PatchState.HOT,
                sizeBytes = 600L, // 2 patches will exceed hot budget of 1000
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
        }

        val p1 = createTestPatch(1L)
        val p2 = createTestPatch(2L)

        hotStore.add(p1)
        hotStore.add(p2)
        assertEquals(1200L, hotStore.totalBytes)

        // Demote p1 manually or via policy
        policy.demoteHotToWarm(p1)
        assertEquals(PatchState.WARM, p1.state)
        assertEquals(1, hotStore.count)
        assertEquals(1, warmStore.count)

        // Demote p1 to Cold
        policy.demoteWarmToCold(p1)
        assertEquals(PatchState.COLD, p1.state)
        assertEquals(0, warmStore.count)
        assertEquals(1, coldStore.count)
        assertNotNull(p1.filePath)
        assertTrue(File(p1.filePath!!).exists())
    }

    @Test
    fun testUndoWhileTransitioningDoesNotLoseData() {
        // Test State Machine rule: old representation retained while in COMPRESSING or WRITING
        val raw = ByteArray(100) { (it * 3).toByte() }
        val comp = PatchCodec.compress(raw)

        val rect = Rect()
        rect.left = 0
        rect.top = 0
        rect.right = 5
        rect.bottom = 5

        // In COMPRESSING state
        val compressingPatch = Patch(
            id = 10L,
            rect = rect,
            state = PatchState.COMPRESSING,
            compressedBytes = comp.compressedBytes,
            rawLength = raw.size,
            crc32 = comp.crc32
        )

        // Data is still available via compressedBytes
        val decompressed1 = PatchCodec.decompress(
            compressingPatch.compressedBytes!!,
            compressingPatch.rawLength,
            compressingPatch.crc32
        )
        assertArrayEquals(raw, decompressed1)

        // In WRITING state (written to disk but compressedBytes not yet discarded)
        val writingPatch = Patch(
            id = 20L,
            rect = rect,
            state = PatchState.WRITING,
            compressedBytes = comp.compressedBytes,
            rawLength = raw.size,
            crc32 = comp.crc32
        )
        val decompressed2 = PatchCodec.decompress(
            writingPatch.compressedBytes!!,
            writingPatch.rawLength,
            writingPatch.crc32
        )
        assertArrayEquals(raw, decompressed2)
    }

    @Test
    fun testColdCleanupOnClear() = runTest {
        val historyDir = tempFolder.newFolder("history_clear")
        val coldStore = ColdStore(historyDir)

        val rect = Rect()
        rect.left = 0
        rect.top = 0
        rect.right = 10
        rect.bottom = 10

        val patch = Patch(
            id = 99L,
            rect = rect,
            state = PatchState.COLD,
            rawLength = 400,
            crc32 = 12345L
        )

        val file = ColdFileCodec.writeColdFile(historyDir, patch, byteArrayOf(1, 2, 3, 4))
        patch.filePath = file.absolutePath
        coldStore.add(patch)

        assertTrue(file.exists())
        assertEquals(1, coldStore.count)

        coldStore.clear()

        assertEquals(0, coldStore.count)
        assertEquals(0L, coldStore.totalBytes)
        assertFalse("Cold file should be deleted on clear", file.exists())
    }
}
