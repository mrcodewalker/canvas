package com.example.canvas.core

import org.junit.Test
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Not an assertion test: prints compress/decompress cost and ratio of the codec on stroke-like pixels, so the
 * default level is chosen from measurements. Run with `--tests "*CodecMicroBenchmarkTest" -i` and read the table.
 * (JVM numbers: use them to compare options with each other, not as device timings.)
 */
class CodecMicroBenchmarkTest {

    /** A blank canvas with a few anti-aliased diagonal strokes: what a "before" patch of a drawing looks like. */
    private fun strokePixels(w: Int, h: Int, strokes: Int): ByteArray {
        val px = ByteArray(w * h * 4)
        for (i in px.indices step 4) { px[i] = -1; px[i + 1] = -1; px[i + 2] = -1; px[i + 3] = -1 }   // opaque white
        val rnd = java.util.Random(11)
        repeat(strokes) {
            var x = rnd.nextInt(w / 2).toFloat()
            var y = rnd.nextInt(h / 2).toFloat()
            val dx = 1f
            val dy = 0.6f + rnd.nextFloat()
            while (x < w - 2 && y < h - 2) {
                for (t in -4..4) {
                    val yy = (y + t).toInt().coerceIn(0, h - 1)
                    val xx = x.toInt()
                    val a = (255 - (kotlin.math.abs(t) * 50)).coerceIn(0, 255)
                    val o = (yy * w + xx) * 4
                    val v = (255 - a).toByte()
                    px[o] = v; px[o + 1] = v; px[o + 2] = v
                }
                x += dx; y += dy
            }
        }
        return px
    }

    private data class Row(val name: String, val ratio: Double, val compressMBs: Double, val inflateMBs: Double)

    private fun measure(name: String, raw: ByteArray, level: Int, strategy: Int): Row {
        fun compressOnce(): ByteArray {
            val d = Deflater(level)
            d.setStrategy(strategy)
            d.setInput(raw); d.finish()
            val out = ByteArray(raw.size + 64)
            var n = 0
            while (!d.finished()) n += d.deflate(out, n, out.size - n)
            d.end()
            return out.copyOf(n)
        }
        fun inflateOnce(c: ByteArray) {
            val inf = Inflater()
            inf.setInput(c)
            val dst = ByteArray(raw.size)
            var off = 0
            while (!inf.finished() && off < dst.size) off += inf.inflate(dst, off, dst.size - off)
            inf.end()
        }
        val c = compressOnce()
        repeat(3) { compressOnce(); inflateOnce(c) }        // warm-up
        val reps = 12
        val t0 = System.nanoTime()
        repeat(reps) { compressOnce() }
        val tc = (System.nanoTime() - t0) / reps.toDouble()
        val t1 = System.nanoTime()
        repeat(reps) { inflateOnce(c) }
        val ti = (System.nanoTime() - t1) / reps.toDouble()
        val mb = raw.size / (1024.0 * 1024.0)
        return Row(name, raw.size.toDouble() / c.size, mb / (tc / 1e9), mb / (ti / 1e9))
    }

    @Test
    fun printCodecTable() {
        for ((label, raw) in listOf(
            "tile 256x256, 3 strokes" to strokePixels(256, 256, 3),
            "patch 1024x512, 12 strokes" to strokePixels(1024, 512, 12)
        )) {
            println("=== $label (${raw.size / 1024} KiB raw)")
            println(String.format("%-28s %9s %14s %14s", "setting", "ratio", "compress MB/s", "inflate MB/s"))
            val rows = ArrayList<Row>()
            for (lvl in listOf(1, 2, 3, 4, 6, 9)) rows.add(measure("level $lvl", raw, lvl, Deflater.DEFAULT_STRATEGY))
            rows.add(measure("level 1 FILTERED", raw, 1, Deflater.FILTERED))
            rows.add(measure("level 1 HUFFMAN_ONLY", raw, 1, Deflater.HUFFMAN_ONLY))
            for (r in rows) println(String.format("%-28s %8.1fx %14.0f %14.0f", r.name, r.ratio, r.compressMBs, r.inflateMBs))
        }
    }
}
