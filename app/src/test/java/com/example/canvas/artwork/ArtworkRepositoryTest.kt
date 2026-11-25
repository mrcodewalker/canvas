package com.example.canvas.artwork

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtworkRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Pure-file fake image store for JVM unit tests where android.graphics.Bitmap is a stub. */
    private class FakeImageStore : ArtworkImageStore {
        var writeCounter = 0

        override fun writeFullAndThumbnail(
            bitmap: Bitmap?,
            fullFile: File,
            thumbFile: File,
            thumbSizePx: Int
        ): Pair<Int, Int> {
            writeCounter++
            fullFile.parentFile?.mkdirs()
            fullFile.writeBytes("PNG_PAYLOAD_$writeCounter".toByteArray())
            thumbFile.writeBytes("THUMB_$writeCounter".toByteArray())
            return 2048 to 2048
        }

        override fun readMutableBitmap(file: File): Bitmap? = null
        override fun readThumbnailBitmap(file: File): Bitmap? = null
    }

    @Test
    fun saveListRenameDuplicateAndDeleteLifecycle() {
        val dir = tempFolder.newFolder("artworks_repo")
        var now = 1_700_000_000_000L
        val store = FakeImageStore()
        val repo = ArtworkRepository(dir, imageStore = store, clockMs = { now })

        // 1. Save new artwork
        val first = repo.saveArtworkInternal(null, title = "Phong cảnh biển")
        assertEquals("Phong cảnh biển", first.title)
        assertFalse(first.autoCapturedOnExit)
        assertEquals(first.id, repo.activeArtworkId)
        assertTrue(File(dir, first.fileName).exists())

        // 2. Auto-capture on app exit creates/updates entry and persists across restart
        now += 5_000L
        val autoExit = repo.saveArtworkInternal(null, existingId = null, title = null, autoCapturedOnExit = true)
        assertTrue(autoExit.autoCapturedOnExit)
        assertEquals(autoExit.id, repo.activeArtworkId)

        // Reload repository from disk (simulating app relaunch)
        val reloadedRepo = ArtworkRepository(dir, imageStore = store, clockMs = { now })
        assertEquals(autoExit.id, reloadedRepo.activeArtworkId)
        val all = reloadedRepo.listArtworks()
        assertEquals(2, all.size)
        assertEquals(autoExit.id, all[0].id) // newest first

        // 3. Overwrite existing artwork clears autoCaptured flag when explicitly saved
        now += 5_000L
        val overwritten = reloadedRepo.saveArtworkInternal(
            null,
            existingId = autoExit.id,
            title = "Bức tranh hoàn thiện",
            autoCapturedOnExit = false
        )
        assertEquals(autoExit.id, overwritten.id)
        assertEquals("Bức tranh hoàn thiện", overwritten.title)
        assertFalse(overwritten.autoCapturedOnExit)
        assertEquals(2, reloadedRepo.listArtworks().size)

        // 4. Rename & Duplicate
        now += 2_000L
        val renamed = reloadedRepo.renameArtwork(first.id, "Biển hoàng hôn")
        assertNotNull(renamed)
        assertEquals("Biển hoàng hôn", renamed!!.title)

        now += 2_000L
        val dup = reloadedRepo.duplicateArtwork(first.id)
        assertNotNull(dup)
        assertEquals(3, reloadedRepo.listArtworks().size)

        // 5. Delete active artwork clears activeArtworkId and removes disk files
        assertTrue(reloadedRepo.deleteArtwork(overwritten.id))
        assertNull(reloadedRepo.activeArtworkId)
        assertFalse(File(dir, overwritten.fileName).exists())
        assertEquals(2, reloadedRepo.listArtworks().size)
    }
}
