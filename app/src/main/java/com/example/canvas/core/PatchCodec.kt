package com.example.canvas.core

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
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

    /**
     * Compresses raw byte array using Deflater.
     * Guaranteed 100% lossless.
     */
    fun compress(
        rawBytes: ByteArray,
        level: Int = DEFAULT_COMPRESSION_LEVEL
    ): CompressedPatch {
        val crc = CRC32()
        crc.update(rawBytes)
        val crcValue = crc.value

        val deflater = Deflater(level)
        deflater.setInput(rawBytes)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(rawBytes.size / 2)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        return CompressedPatch(
            compressedBytes = outputStream.toByteArray(),
            rawLength = rawBytes.size,
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
        val inflater = Inflater()
        inflater.setInput(compressedBytes)

        val decompressed = ByteArray(rawLength)
        var offset = 0
        while (!inflater.finished() && offset < rawLength) {
            val count = inflater.inflate(decompressed, offset, rawLength - offset)
            if (count == 0 && inflater.needsInput()) break
            offset += count
        }
        inflater.end()

        if (offset != rawLength) {
            throw IllegalStateException("Decompression size mismatch: expected $rawLength bytes but got $offset")
        }

        if (expectedCrc32 != 0L) {
            val crc = CRC32()
            crc.update(decompressed)
            if (crc.value != expectedCrc32) {
                throw IllegalStateException("CRC32 mismatch after decompression: expected $expectedCrc32 but got ${crc.value}")
            }
        }

        return decompressed
    }

    /**
     * Compresses an Android Bitmap's raw ARGB_8888 pixel buffer into a [CompressedPatch].
     */
    fun compressBitmap(
        bitmap: Bitmap,
        level: Int = DEFAULT_COMPRESSION_LEVEL
    ): CompressedPatch {
        val byteCount = bitmap.byteCount
        val buffer = ByteBuffer.allocate(byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return compress(buffer.array(), level)
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
        val rawBytes = decompress(compressedBytes, rawLength, expectedCrc32)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rawBytes))
        return bitmap
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
