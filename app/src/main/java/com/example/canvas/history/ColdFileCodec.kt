package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class ColdFilePayload(
    val rect: Rect,
    val rawLength: Int,
    val crc32: Long,
    val compressedBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColdFilePayload
        if (rect != other.rect) return false
        if (rawLength != other.rawLength) return false
        if (crc32 != other.crc32) return false
        if (!compressedBytes.contentEquals(other.compressedBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = rect.hashCode()
        result = 31 * result + rawLength
        result = 31 * result + crc32.hashCode()
        result = 31 * result + compressedBytes.contentHashCode()
        return result
    }
}

/**
 * Encodes and decodes Cold tier files on disk.
 * Header format:
 * - MAGIC: 4 bytes ("CHRN" = 0x4348524E)
 * - VERSION: 2 bytes (Short = 1)
 * - X: 4 bytes (Int)
 * - Y: 4 bytes (Int)
 * - W: 4 bytes (Int)
 * - H: 4 bytes (Int)
 * - RAW_LEN: 4 bytes (Int)
 * - COMP_LEN: 4 bytes (Int)
 * - CRC32: 8 bytes (Long)
 * Total header = 36 bytes, followed immediately by [COMP_LEN] compressed payload bytes.
 */
object ColdFileCodec {

    private const val MAGIC: Int = 0x4348524E // "CHRN"
    private const val VERSION: Short = 1

    /**
     * Atomically writes compressed patch bytes to a Cold file using a temporary file
     * and renaming to prevent partial/corrupted files.
     */
    fun writeColdFile(
        historyDir: File,
        patch: Patch,
        compressedBytes: ByteArray
    ): File {
        if (!historyDir.exists()) {
            historyDir.mkdirs()
        }

        val targetFile = File(historyDir, "patch_${patch.id}.chrf")
        val tempFile = File(historyDir, "patch_${patch.id}_${System.nanoTime()}.tmp")

        val rectW = patch.rect.right - patch.rect.left
        val rectH = patch.rect.bottom - patch.rect.top

        DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 16384)).use { out ->
            out.writeInt(MAGIC)
            out.writeShort(VERSION.toInt())
            out.writeInt(patch.rect.left)
            out.writeInt(patch.rect.top)
            out.writeInt(rectW)
            out.writeInt(rectH)
            out.writeInt(patch.rawLength)
            out.writeInt(compressedBytes.size)
            out.writeLong(patch.crc32)
            out.write(compressedBytes)
            out.flush()
        }

        // Atomic rename
        if (!tempFile.renameTo(targetFile)) {
            // Fallback for filesystems where renameTo might fail if target exists
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }

        return targetFile
    }

    /**
     * Reads and decodes a Cold file, validating its magic header and checksum metadata.
     */
    fun readColdFile(file: File): ColdFilePayload {
        if (!file.exists()) {
            throw IOException("Cold patch file not found: ${file.absolutePath}")
        }

        DataInputStream(BufferedInputStream(FileInputStream(file), 16384)).use { input ->
            val magic = input.readInt()
            if (magic != MAGIC) {
                throw IOException("Invalid Cold file magic: 0x${Integer.toHexString(magic)}")
            }

            val version = input.readShort()
            if (version != VERSION) {
                throw IOException("Unsupported Cold file version: $version")
            }

            val x = input.readInt()
            val y = input.readInt()
            val w = input.readInt()
            val h = input.readInt()
            val rawLen = input.readInt()
            val compLen = input.readInt()
            val crc32 = input.readLong()

            val payload = ByteArray(compLen)
            input.readFully(payload)

            val rect = Rect()
            rect.left = x
            rect.top = y
            rect.right = x + w
            rect.bottom = y + h

            return ColdFilePayload(
                rect = rect,
                rawLength = rawLen,
                crc32 = crc32,
                compressedBytes = payload
            )
        }
    }
}
