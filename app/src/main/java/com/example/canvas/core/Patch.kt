package com.example.canvas.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

/**
 * State machine stages for a patch in Tiered storage:
 * HOT -> COMPRESSING -> WARM -> WRITING -> COLD.
 */
enum class PatchState {
    HOT,
    COMPRESSING,
    WARM,
    WRITING,
    COLD
}

/**
 * Represents a discrete image region patch captured before a canvas modification.
 *
 * @param id Unique monotonic sequence identifier.
 * @param rect Canvas coordinates bounded by this patch.
 * @param state Current life-cycle state.
 * @param bitmap Raw uncompressed bitmap (Hot representation).
 * @param compressedBytes Deflated lossless byte array (Warm representation).
 * @param filePath Absolute path on filesystem (Cold representation).
 * @param sizeBytes Byte size of current representation.
 * @param rawLength Uncompressed pixel byte length (width * height * 4).
 * @param crc32 Checksum for integrity verification.
 * @param groupId Patches of one stroke captured as several dirty tiles share the same non-zero
 *   groupId and are undone/redone atomically. 0 means a standalone patch.
 */
data class Patch(
    val id: Long,
    val rect: Rect,
    @Volatile var state: PatchState = PatchState.HOT,
    @Volatile var bitmap: Bitmap? = null,
    @Volatile var compressedBytes: ByteArray? = null,
    @Volatile var filePath: String? = null,
    @Volatile var sizeBytes: Long = 0L,
    @Volatile var rawLength: Int = 0,
    @Volatile var crc32: Long = 0L,
    /** True once the Cold file was already squeezed at the high compression level (or gains nothing). */
    @Volatile var deepCompressed: Boolean = false,
    val groupId: Long = 0L,
    /** What was drawn by this step; lets redo replay it on a changed canvas. Null = pixels only. */
    val op: StrokeOp? = null,
    /** 1-based number of the stroke this patch belongs to (for display); 0 = unknown. */
    @Volatile var strokeNo: Int = 0
) {
    /** Deflate level the Warm bytes of this patch were compressed with (0 = not compressed yet). */
    @Volatile var warmLevel: Int = 0

    /**
     * How many times this patch was already written to Cold. A patch that comes back (undo/redo near the
     * Warm/Cold boundary) is not squeezed harder again: the first squeeze already paid for itself or did not,
     * and repeating it would only burn CPU while the patch flips between the two tiers.
     */
    @Volatile var coldTrips: Int = 0

    /**
     * The compressed form of exactly these pixels, kept when the patch was undone: redo captures the very same
     * pixels again, so the new patch can take this encoding instead of compressing (and later writing) them again.
     * Null when unknown or when the pixels may have changed.
     */
    @Volatile var reuse: Encoding? = null

    /**
     * A Cold file that still holds exactly this patch's compressed bytes although the patch lives in RAM now
     * (it was read back by undo or prefetch). Going back to Cold then costs nothing: no compression, no write.
     */
    @Volatile var keptFile: String? = null

    /** A finished encoding of a patch's pixels, with what the next tier move needs to know about it. */
    class Encoding(
        val bytes: ByteArray,
        val rawLength: Int,
        val crc32: Long,
        val level: Int,
        val deep: Boolean,
        val coldTrips: Int,
        /** Cold file with the same bytes, if one is still on disk. Owned by whoever holds the encoding. */
        val filePath: String? = null
    )

    /** Unique per instance (not part of equals/hashCode): lets the stores find a patch in O(1). */
    val uid: Long = uidCounter.incrementAndGet()

    companion object {
        private val uidCounter = java.util.concurrent.atomic.AtomicLong()
        private val srcPaint by lazy {
            Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }
        }
    }

    /**
     * Applies this patch onto the target canvas using [PorterDuff.Mode.SRC]
     * to strictly overwrite pixels without alpha blending.
     */
    fun applyTo(targetCanvas: Canvas) {
        val bmp = bitmap ?: return
        targetCanvas.drawBitmap(bmp, rect.left.toFloat(), rect.top.toFloat(), srcPaint)
    }

    /**
     * Gives the pixels back to the system but keeps [reuse]: for a redo step whose stroke can be replayed, the
     * pixels are only a cache, while the encoding (small) still describes the pixels an undo will need again.
     */
    fun freePixels() {
        try {
            if (bitmap != null && !bitmap!!.isRecycled) {
                bitmap!!.recycle()
            }
        } catch (_: Throwable) {}
        bitmap = null
    }

    /**
     * Safely recycles the bitmap reference if it exists and is not already recycled.
     */
    fun recycle() {
        try {
            if (bitmap != null && !bitmap!!.isRecycled) {
                bitmap!!.recycle()
            }
        } catch (_: Throwable) {}
        bitmap = null
        // pixels freed: the encoding can no longer be trusted to match them, and its file is of no use
        reuse?.filePath?.let { try { java.io.File(it).delete() } catch (_: Throwable) {} }
        reuse = null
    }
}
