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
     * Safely recycles the bitmap reference if it exists and is not already recycled.
     */
    fun recycle() {
        try {
            if (bitmap != null && !bitmap!!.isRecycled) {
                bitmap!!.recycle()
            }
        } catch (_: Throwable) {}
        bitmap = null
    }
}
