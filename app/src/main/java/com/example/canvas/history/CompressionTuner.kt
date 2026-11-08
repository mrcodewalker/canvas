package com.example.canvas.history

import java.util.zip.Deflater

/**
 * Picks the Deflate level **per patch**, from what was measured on this user's own strokes.
 *
 * Warm data is compressed fast (level 1) because that happens while drawing. When a patch moves to Cold the
 * question is whether squeezing it harder is worth the CPU. That depends on the patch: a stroke of flat
 * colour on a blank canvas is already tiny at level 1 and gains nothing from level 9, a dense scribble gains
 * a lot. So patches are grouped by how well level 1 compressed them (`class = log2(raw / warm)`) and, for
 * every class and every higher level, the tuner keeps the average gain and the CPU cost per raw byte.
 *
 * A level is chosen when `bytes saved * (cost of writing + reading a byte) > CPU time`; otherwise the patch
 * is written as it is. Levels never tried for a class are tried first, which is how the tables fill up.
 * Candidate levels are the named zlib levels (default 6 and best 9) up to the maximum the user allows.
 */
class CompressionTuner {

    private class Stat {
        var n = 0
        var warmBytes = 0.0
        var savedBytes = 0.0
        var rawBytes = 0.0
        var cpuNs = 0.0
    }

    private val stats = HashMap<Long, Stat>()

    private fun key(cls: Int, level: Int) = cls.toLong() * 100 + level

    /** How compressible the patch already proved to be at the Warm level (0 = incompressible). */
    fun classOf(rawBytes: Int, warmBytes: Int): Int {
        if (warmBytes <= 0 || rawBytes <= 0) return 0
        val factor = (rawBytes / warmBytes).coerceAtLeast(1)       // exact integer log2: no floating point error
        return 31 - Integer.numberOfLeadingZeros(factor)
    }

    @Synchronized
    fun record(cls: Int, level: Int, rawBytes: Long, warmBytes: Long, outBytes: Long, compressNs: Long) {
        val s = stats.getOrPut(key(cls, level)) { Stat() }
        s.n++
        s.warmBytes += warmBytes
        s.savedBytes += (warmBytes - outBytes).coerceAtLeast(0L)
        s.rawBytes += rawBytes
        s.cpuNs += compressNs.coerceAtLeast(0L)
    }

    /** Levels worth considering above [baseline], up to [maxLevel]. */
    private fun candidates(baseline: Int, maxLevel: Int): List<Int> =
        listOf(6, Deflater.BEST_COMPRESSION).filter { it > baseline && it <= maxLevel }
            .ifEmpty { if (maxLevel > baseline) listOf(maxLevel) else emptyList() }

    /**
     * @param ioNsPerByte what one byte costs to write and read back on this device (measured); null if unknown
     * @param inflateNsPerRaw measured cost of inflating the Warm bytes first (paid by any recompression)
     * @param squeeze true when space is short: take the level that saves the most, whatever the CPU
     * @return the level to compress at, or [baseline] to write the Warm bytes unchanged
     */
    @Synchronized
    fun choose(
        cls: Int,
        warmBytes: Long,
        rawBytes: Long,
        baseline: Int,
        maxLevel: Int,
        ioNsPerByte: Double?,
        inflateNsPerRaw: Double?,
        squeeze: Boolean
    ): Int {
        val levels = candidates(baseline, maxLevel)
        if (levels.isEmpty()) return baseline

        // 1. learn: try the lowest level this class has never seen
        for (l in levels) {
            if ((stats[key(cls, l)]?.n ?: 0) == 0) return l
        }

        // 2. exploit: expected net benefit of each level against writing the bytes unchanged
        var best = baseline
        var bestNet = 0.0
        var bestGain = 0.0
        var gainLevel = baseline
        for (l in levels) {
            val s = stats.getValue(key(cls, l))
            val gainFraction = if (s.warmBytes > 0) s.savedBytes / s.warmBytes else 0.0
            val cpuPerRaw = if (s.rawBytes > 0) s.cpuNs / s.rawBytes else 0.0
            val saved = warmBytes * gainFraction
            val net = saved * (ioNsPerByte ?: 0.0) - rawBytes * (cpuPerRaw + (inflateNsPerRaw ?: 0.0))
            if (net > bestNet) {
                bestNet = net
                best = l
            }
            if (saved > bestGain) {
                bestGain = saved
                gainLevel = l
            }
        }
        return if (squeeze && bestGain > 0.0) gainLevel else best
    }

    /** Level that saved the most bytes for this class so far (used when space must be freed urgently). */
    @Synchronized
    fun mostCompressingLevel(cls: Int, baseline: Int, maxLevel: Int): Int {
        var level = if (maxLevel > baseline) maxLevel else baseline
        var bestFraction = -1.0
        for (l in candidates(baseline, maxLevel)) {
            val s = stats[key(cls, l)] ?: continue
            val f = if (s.warmBytes > 0) s.savedBytes / s.warmBytes else 0.0
            if (f > bestFraction) {
                bestFraction = f
                level = l
            }
        }
        return level
    }

    @Synchronized
    fun reset() = stats.clear()
}
