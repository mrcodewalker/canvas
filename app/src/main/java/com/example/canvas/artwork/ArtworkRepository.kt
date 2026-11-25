package com.example.canvas.artwork

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Metadata of a saved artwork stored on disk.
 *
 * @param id Stable unique identifier.
 * @param title User-facing painting name (e.g. "Tranh vẽ #1").
 * @param fileName Full-resolution PNG file name inside [ArtworkRepository.artworksDir].
 * @param thumbFileName Downscaled preview PNG file name inside [ArtworkRepository.artworksDir].
 * @param createdAtMs Creation timestamp (epoch ms).
 * @param updatedAtMs Last saved timestamp (epoch ms).
 * @param width Canvas width in pixels.
 * @param height Canvas height in pixels.
 * @param sizeBytes File size of the saved full-resolution image on disk.
 * @param autoCapturedOnExit True when automatically captured as the final canvas snapshot upon exiting the app.
 */
data class SavedArtwork(
    val id: String,
    val title: String,
    val fileName: String,
    val thumbFileName: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val autoCapturedOnExit: Boolean = false
)

/**
 * Abstraction for encoding/decoding artwork images to/from disk so pure JVM unit tests can verify
 * repository lifecycle, atomic writes, gallery management, and exit auto-capture without Android bitmap stubs.
 */
interface ArtworkImageStore {
    fun writeFullAndThumbnail(bitmap: Bitmap?, fullFile: File, thumbFile: File, thumbSizePx: Int): Pair<Int, Int>
    fun readMutableBitmap(file: File): Bitmap?
    fun readThumbnailBitmap(file: File): Bitmap?
}

object AndroidPngImageStore : ArtworkImageStore {
    override fun writeFullAndThumbnail(
        bitmap: Bitmap?,
        fullFile: File,
        thumbFile: File,
        thumbSizePx: Int
    ): Pair<Int, Int> {
        val bmp = requireNotNull(bitmap) { "Bitmap must not be null when saving PNG" }
        writePngAtomic(bmp, fullFile)
        val scaleW = thumbSizePx.coerceAtLeast(64)
        val scaleH = ((bmp.height.toLong() * scaleW) / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(64)
        val thumb = Bitmap.createScaledBitmap(bmp, scaleW, scaleH, true)
        try {
            writePngAtomic(thumb, thumbFile)
        } finally {
            if (thumb !== bmp && !thumb.isRecycled) {
                thumb.recycle()
            }
        }
        return bmp.width to bmp.height
    }

    private fun writePngAtomic(bmp: Bitmap, dest: File) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        BufferedOutputStream(FileOutputStream(tmp)).use { out ->
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("PNG compression failed for ${dest.name}")
            }
            out.flush()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    override fun readMutableBitmap(file: File): Bitmap? {
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    override fun readThumbnailBitmap(file: File): Bitmap? {
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}

/**
 * Manages persistent saved artworks, thumbnails, active session tracking, and MediaStore PNG export.
 * Uses a self-contained binary index (`artworks_index.bin`) so metadata serialization is fast, atomic,
 * and independent of Android runtime stubs.
 */
class ArtworkRepository(
    val artworksDir: File,
    private val imageStore: ArtworkImageStore = AndroidPngImageStore,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val INDEX_FILE = "artworks_index.bin"
        private const val MAGIC = 0x43484152 // "CHAR"
        private const val VERSION = 1
        const val THUMB_SIZE_PX = 384

        fun create(context: Context): ArtworkRepository {
            return ArtworkRepository(File(context.filesDir, "artworks"))
        }
    }

    private val lock = Any()
    private val indexFile = File(artworksDir, INDEX_FILE)

    init {
        artworksDir.mkdirs()
        cleanupTempFiles()
    }

    private fun cleanupTempFiles() {
        artworksDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".tmp")) {
                try { f.delete() } catch (_: Throwable) {}
            }
        }
    }

    private data class IndexState(
        val activeId: String?,
        val items: MutableList<SavedArtwork>
    )

    private fun readIndexLocked(): IndexState {
        if (!indexFile.exists()) return IndexState(null, mutableListOf())
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(indexFile))).use { inp ->
                val magic = inp.readInt()
                val version = inp.readInt()
                if (magic != MAGIC || version != VERSION) return IndexState(null, mutableListOf())
                val hasActive = inp.readBoolean()
                val activeId = if (hasActive) inp.readUTF() else null
                val count = inp.readInt().coerceIn(0, 10_000)
                val items = ArrayList<SavedArtwork>(count)
                repeat(count) {
                    val id = inp.readUTF()
                    val title = inp.readUTF()
                    val fileName = inp.readUTF()
                    val thumbName = inp.readUTF()
                    val created = inp.readLong()
                    val updated = inp.readLong()
                    val w = inp.readInt()
                    val h = inp.readInt()
                    val size = inp.readLong()
                    val autoCap = inp.readBoolean()
                    if (File(artworksDir, fileName).exists()) {
                        items.add(
                            SavedArtwork(
                                id = id,
                                title = title,
                                fileName = fileName,
                                thumbFileName = thumbName,
                                createdAtMs = created,
                                updatedAtMs = updated,
                                width = w,
                                height = h,
                                sizeBytes = size,
                                autoCapturedOnExit = autoCap
                            )
                        )
                    }
                }
                IndexState(activeId?.takeIf { id -> items.any { it.id == id } }, items)
            }
        } catch (_: Throwable) {
            IndexState(null, mutableListOf())
        }
    }

    private fun writeIndexLocked(state: IndexState) {
        artworksDir.mkdirs()
        val tmp = File(artworksDir, "$INDEX_FILE.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            if (state.activeId != null) {
                out.writeBoolean(true)
                out.writeUTF(state.activeId)
            } else {
                out.writeBoolean(false)
            }
            out.writeInt(state.items.size)
            for (a in state.items) {
                out.writeUTF(a.id)
                out.writeUTF(a.title)
                out.writeUTF(a.fileName)
                out.writeUTF(a.thumbFileName)
                out.writeLong(a.createdAtMs)
                out.writeLong(a.updatedAtMs)
                out.writeInt(a.width)
                out.writeInt(a.height)
                out.writeLong(a.sizeBytes)
                out.writeBoolean(a.autoCapturedOnExit)
            }
            out.flush()
        }
        if (indexFile.exists()) indexFile.delete()
        if (!tmp.renameTo(indexFile)) {
            tmp.copyTo(indexFile, overwrite = true)
            tmp.delete()
        }
    }

    fun listArtworks(): List<SavedArtwork> = synchronized(lock) {
        readIndexLocked().items.sortedByDescending { it.updatedAtMs }
    }

    fun getArtwork(id: String): SavedArtwork? = synchronized(lock) {
        readIndexLocked().items.firstOrNull { it.id == id }
    }

    var activeArtworkId: String?
        get() = synchronized(lock) { readIndexLocked().activeId }
        set(value) = synchronized(lock) {
            val state = readIndexLocked()
            val valid = value?.takeIf { id -> state.items.any { it.id == id } }
            writeIndexLocked(state.copy(activeId = valid))
        }

    fun nextDefaultTitle(): String = synchronized(lock) {
        val existing = readIndexLocked().items.map { it.title }.toSet()
        var idx = existing.size + 1
        while ("Tranh vẽ #$idx" in existing) idx++
        "Tranh vẽ #$idx"
    }

    /**
     * Captures [bitmap] to disk as a PNG + thumbnail and updates the gallery index.
     * When [existingId] matches a saved artwork, overwrites that artwork in place; otherwise creates a new entry.
     */
    fun saveArtwork(
        bitmap: Bitmap,
        existingId: String? = null,
        title: String? = null,
        autoCapturedOnExit: Boolean = false
    ): SavedArtwork = saveArtworkInternal(bitmap, existingId, title, autoCapturedOnExit)

    internal fun saveArtworkInternal(
        bitmap: Bitmap?,
        existingId: String? = null,
        title: String? = null,
        autoCapturedOnExit: Boolean = false
    ): SavedArtwork = synchronized(lock) {
        val now = clockMs()
        val state = readIndexLocked()
        val prevIdx = existingId?.let { id -> state.items.indexOfFirst { it.id == id } } ?: -1
        val prev = if (prevIdx >= 0) state.items[prevIdx] else null

        val id = prev?.id ?: UUID.randomUUID().toString().replace("-", "").take(12)
        val fileName = prev?.fileName ?: "art_$id.png"
        val thumbName = prev?.thumbFileName ?: "thumb_$id.png"
        val fullFile = File(artworksDir, fileName)
        val thumbFile = File(artworksDir, thumbName)

        val (w, h) = imageStore.writeFullAndThumbnail(bitmap, fullFile, thumbFile, THUMB_SIZE_PX)
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: prev?.title
            ?: nextTitleFromList(state.items, autoCapturedOnExit)

        val saved = SavedArtwork(
            id = id,
            title = cleanTitle,
            fileName = fileName,
            thumbFileName = thumbName,
            createdAtMs = prev?.createdAtMs ?: now,
            updatedAtMs = now,
            width = w,
            height = h,
            sizeBytes = fullFile.length(),
            autoCapturedOnExit = autoCapturedOnExit
        )
        if (prevIdx >= 0) {
            state.items[prevIdx] = saved
        } else {
            state.items.add(saved)
        }
        writeIndexLocked(IndexState(activeId = saved.id, items = state.items))
        saved
    }

    private fun nextTitleFromList(items: List<SavedArtwork>, autoCaptured: Boolean): String {
        val titles = items.map { it.title }.toSet()
        var n = items.size + 1
        val prefix = if (autoCaptured) "Bản vẽ tự lưu #" else "Tranh vẽ #"
        while ("$prefix$n" in titles) n++
        return "$prefix$n"
    }

    fun loadArtworkBitmap(id: String): Bitmap? = synchronized(lock) {
        val item = readIndexLocked().items.firstOrNull { it.id == id } ?: return null
        imageStore.readMutableBitmap(File(artworksDir, item.fileName))
    }

    fun loadThumbnailBitmap(id: String): Bitmap? = synchronized(lock) {
        val item = readIndexLocked().items.firstOrNull { it.id == id } ?: return null
        val thumbFile = File(artworksDir, item.thumbFileName)
        imageStore.readThumbnailBitmap(if (thumbFile.exists()) thumbFile else File(artworksDir, item.fileName))
    }

    fun renameArtwork(id: String, newTitle: String): SavedArtwork? = synchronized(lock) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return null
        val state = readIndexLocked()
        val idx = state.items.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = state.items[idx].copy(
            title = trimmed,
            updatedAtMs = clockMs(),
            autoCapturedOnExit = false
        )
        state.items[idx] = updated
        writeIndexLocked(state)
        updated
    }

    fun duplicateArtwork(id: String): SavedArtwork? = synchronized(lock) {
        val state = readIndexLocked()
        val src = state.items.firstOrNull { it.id == id } ?: return null
        val srcFull = File(artworksDir, src.fileName)
        if (!srcFull.exists()) return null

        val newId = UUID.randomUUID().toString().replace("-", "").take(12)
        val newFullName = "art_$newId.png"
        val newThumbName = "thumb_$newId.png"
        val dstFull = File(artworksDir, newFullName)
        srcFull.copyTo(dstFull, overwrite = true)
        val srcThumb = File(artworksDir, src.thumbFileName)
        if (srcThumb.exists()) {
            srcThumb.copyTo(File(artworksDir, newThumbName), overwrite = true)
        }
        val now = clockMs()
        val copy = SavedArtwork(
            id = newId,
            title = "${src.title} (Bản sao)",
            fileName = newFullName,
            thumbFileName = newThumbName,
            createdAtMs = now,
            updatedAtMs = now,
            width = src.width,
            height = src.height,
            sizeBytes = dstFull.length(),
            autoCapturedOnExit = false
        )
        state.items.add(copy)
        writeIndexLocked(state)
        copy
    }

    fun deleteArtwork(id: String): Boolean = synchronized(lock) {
        val state = readIndexLocked()
        val idx = state.items.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val victim = state.items.removeAt(idx)
        try { File(artworksDir, victim.fileName).delete() } catch (_: Throwable) {}
        try { File(artworksDir, victim.thumbFileName).delete() } catch (_: Throwable) {}
        val nextActive = if (state.activeId == id) null else state.activeId
        writeIndexLocked(IndexState(activeId = nextActive, items = state.items))
        true
    }

    fun artworkFile(id: String): File? = synchronized(lock) {
        val item = readIndexLocked().items.firstOrNull { it.id == id } ?: return null
        File(artworksDir, item.fileName).takeIf { it.exists() }
    }

    /**
     * Exports [bitmap] as a lossless PNG into the device's public Pictures/ChronosCanvas gallery.
     */
    fun exportPngToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        return try {
            val safeName = title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().ifEmpty { "Chronos_Art" }
            val fileName = "${safeName}_${clockMs()}.png"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ChronosCanvas")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
