package com.example.canvas.core

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Result of compressing raw patch pixel bytes.
 */
data class CompressedPatch(
    val compressedBytes: ByteArray,
    val rawLength: Int,
    val crc32: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressedPatch
        if (!compressedBytes.contentEquals(other.compressedBytes)) return false
        if (rawLength != other.rawLength) return false
        if (crc32 != other.crc32) return false
        return true
    }

    override fun hashCode(): Int {
        var result = compressedBytes.contentHashCode()
        result = 31 * result + rawLength
        result = 31 * result + crc32.hashCode()
        return result
    }
}

/**
 * Lossless codec for compressing and decompressing patch pixels using Deflater / Inflater.
 */
object PatchCodec {

    const val DEFAULT_COMPRESSION_LEVEL = Deflater.BEST_SPEED // Level 1 (fastest, low CPU overhead)

    // Compressed size / raw size of the previous call: sizes the next output buffer. Strokes on a blank canvas
    // compress 30-170x, so allocating the worst case (as large as the input) wasted megabytes per call.
    @Volatile
    private var lastRatio = 0.25

    /**
     * Compresses [length] bytes of [rawBytes] using Deflater.
     * Guaranteed 100% lossless.
     *
     * (Cutting big patches into slices compressed on several cores was tried and measured on a 4-core device:
     * compression barely improved, decompression got slower and the whole run took longer, so it was removed.)
     */
    fun compress(
        rawBytes: ByteArray,
        level: Int = DEFAULT_COMPRESSION_LEVEL,
        length: Int = rawBytes.size
    ): CompressedPatch {
        val crc = CRC32()
        crc.update(rawBytes, 0, length)
        val crcValue = crc.value

        val deflater = Deflater(level)
        deflater.setInput(rawBytes, 0, length)
        deflater.finish()

        // zlib's compressBound() is the hard ceiling; start from what the last call needed and grow if short
        val bound = length + (length shr 12) + (length shr 14) + (length shr 25) + 64
        var out = ByteArray(minOf(bound, maxOf(4096, (length * lastRatio * 1.5).toInt() + 64)))
        var written = 0
        while (!deflater.finished()) {
            if (written == out.size) out = out.copyOf(minOf(bound, out.size * 2))
            val count = deflater.deflate(out, written, out.size - written)
            if (count == 0 && deflater.needsInput()) break
            written += count
        }
        deflater.end()
        if (length > 0) lastRatio = written.toDouble() / length

        return CompressedPatch(
            compressedBytes = if (written == out.size) out else out.copyOf(written),
            rawLength = length,
            crc32 = crcValue
        )
    }

    /**
     * Decompresses deflated bytes back into the exact original raw byte array using Inflater.
     * Validates CRC32 checksum against [expectedCrc32] if provided.
     */
    fun decompress(
        compressedBytes: ByteArray,
        rawLength: Int,
        expectedCrc32: Long = 0L
    ): ByteArray {
        val decompressed = ByteArray(rawLength)
        inflateInto(compressedBytes, decompressed, rawLength, expectedCrc32)
        return decompressed
    }

    private fun inflateInto(compressedBytes: ByteArray, dest: ByteArray, rawLength: Int, expectedCrc32: Long) {
        val inflater = Inflater()
        inflater.setInput(compressedBytes)

        var offset = 0
        while (!inflater.finished() && offset < rawLength) {
            val count = inflater.inflate(dest, offset, rawLength - offset)
            if (count == 0 && inflater.needsInput()) break
            offset += count
        }
        inflater.end()

        if (offset != rawLength) {
            throw IllegalStateException("Decompression size mismatch: expected $rawLength bytes but got $offset")
        }

        if (expectedCrc32 != 0L) {
            val crc = CRC32()
            crc.update(dest, 0, rawLength)
            if (crc.value != expectedCrc32) {
                throw IllegalStateException("CRC32 mismatch after decompression: expected $expectedCrc32 but got ${crc.value}")
            }
        }
    }

    /**
     * Compresses an Android Bitmap's raw ARGB_8888 pixel buffer into a [CompressedPatch].
     */
    fun compressBitmap(
        bitmap: Bitmap,
        level: Int = DEFAULT_COMPRESSION_LEVEL
    ): CompressedPatch {
        val byteCount = bitmap.byteCount
        val raw = BufferPool.acquire(byteCount)
        try {
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(raw, 0, byteCount))
            return compress(raw, level, byteCount)
        } finally {
            BufferPool.release(raw)
        }
    }

    /**
     * Compresses the same pixels at every level in [levels] and reports how long each took: the fair way to
     * compare levels. Returns, per level, the result and the nanoseconds spent compressing (CRC included).
     */
    fun compressBitmapAtLevels(bitmap: Bitmap, levels: List<Int>): Map<Int, Pair<CompressedPatch, Long>> {
        val byteCount = bitmap.byteCount
        val raw = BufferPool.acquire(byteCount)
        try {
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(raw, 0, byteCount))
            val out = LinkedHashMap<Int, Pair<CompressedPatch, Long>>()
            for (level in levels) {
                val t0 = System.nanoTime()
                val c = compress(raw, level, byteCount)
                out[level] = c to (System.nanoTime() - t0)
            }
            return out
        } finally {
            BufferPool.release(raw)
        }
    }

    /**
     * Decompresses deflated bytes into a newly created Android Bitmap.
     */
    fun decompressToBitmap(
        compressedBytes: ByteArray,
        width: Int,
        height: Int,
        rawLength: Int,
        expectedCrc32: Long = 0L
    ): Bitmap {
        // inflate into a pooled buffer and let the bitmap read straight from it: no pixel-sized temporary array
        val raw = BufferPool.acquire(rawLength)
        try {
            inflateInto(compressedBytes, raw, rawLength, expectedCrc32)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(raw, 0, rawLength))
            return bitmap
        } finally {
            BufferPool.release(raw)
        }
    }

    /**
     * Helper to compute CRC32 of any byte array.
     */
    fun computeCrc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }
}
