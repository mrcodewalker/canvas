package com.example.canvas.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

/**
 * Represents a discrete image region patch captured before a canvas modification.
 *
 * @param id Unique monotonic sequence identifier.
 * @param rect Canvas coordinates bounded by this patch.
 * @param bitmap Raw uncompressed bitmap (Hot representation).
 * @param compressedBytes Deflated lossless byte array (Warm representation).
 * @param filePath Absolute path on filesystem (Cold representation).
 * @param sizeBytes Byte size of current representation.
 * @param crc32 Checksum for integrity verification.
 */
data class Patch(
    val id: Long,
    val rect: Rect,
    var bitmap: Bitmap? = null,
    var compressedBytes: ByteArray? = null,
    var filePath: String? = null,
    var sizeBytes: Long = 0L,
    var crc32: Long = 0L
) {
    companion object {
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
