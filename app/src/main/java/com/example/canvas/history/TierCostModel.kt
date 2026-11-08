package com.example.canvas.history

/**
 * Measured costs of every step of a Cold undo, kept separately so the policies can reason about them:
 *  - **read** (disk -> RAM bytes), **write** (RAM bytes -> disk),
 *  - **decompress** (bytes -> pixels), **apply** (draw patch onto the canvas),
 *  - **recompress** (Warm L1 -> Cold L9) together with how many bytes it actually saved.
 *
 * Rates are totals ns / totals bytes, i.e. parameter-free running averages weighted by patch size.
 */
class TierCostModel {

    private class Rate {
        var bytes = 0.0
        var ns = 0.0
        fun add(b: Long, t: Long) {
            if (b <= 0L) return
            bytes += b
            ns += t.coerceAtLeast(0L)
        }
        fun nsPerByte(): Double? = if (bytes > 0.0) ns / bytes else null
        fun clear() { bytes = 0.0; ns = 0.0 }
    }

    private val compress = Rate()   // Hot -> Warm, per raw byte
    private val read = Rate()
    private val write = Rate()
    private val decompress = Rate()   // per raw byte
    private val apply = Rate()        // per raw byte
    private val recompress = Rate()   // per raw byte
    private var warmBytesSeen = 0.0
    private var bytesSavedByRecompress = 0.0

    @Synchronized fun recordCompress(rawBytes: Long, ns: Long) = compress.add(rawBytes, ns)
    @Synchronized fun recordRead(bytes: Long, ns: Long) = read.add(bytes, ns)
    @Synchronized fun recordWrite(bytes: Long, ns: Long) = write.add(bytes, ns)
    @Synchronized fun recordDecompress(rawBytes: Long, ns: Long) = decompress.add(rawBytes, ns)
    @Synchronized fun recordApply(rawBytes: Long, ns: Long) = apply.add(rawBytes, ns)

    /** One finished Warm -> Cold recompression: it cost [ns] and turned [warmBytes] into [coldBytes]. */
    @Synchronized
    fun recordRecompress(rawBytes: Long, warmBytes: Long, coldBytes: Long, ns: Long) {
        recompress.add(rawBytes, ns)
        warmBytesSeen += warmBytes
        bytesSavedByRecompress += (warmBytes - coldBytes).coerceAtLeast(0L)
    }

    private var warmRaw = 0.0
    private var warmComp = 0.0

    /** A Hot -> Warm compression finished: [rawBytes] became [compressedBytes]. */
    @Synchronized
    fun recordWarmCompress(rawBytes: Long, compressedBytes: Long) {
        if (rawBytes <= 0L) return
        warmRaw += rawBytes
        warmComp += compressedBytes
    }

    /** Observed compressed/raw size of this user's strokes, or null before any was compressed. */
    @Synchronized
    fun warmRatio(): Double? = if (warmRaw > 0.0) warmComp / warmRaw else null

    @Synchronized fun readNsPerByte(): Double? = read.nsPerByte()
    @Synchronized fun writeNsPerByte(): Double? = write.nsPerByte()
    @Synchronized fun decompressNsPerRawByte(): Double? = decompress.nsPerByte()
    @Synchronized fun applyNsPerRawByte(): Double? = apply.nsPerByte()

    /**
     * Is re-compressing this Warm patch at a higher level before writing it to disk worth it?
     *
     * Worth = the bytes it is expected to save, valued at what each byte costs us to write now and
     * to read later (both measured), must exceed the CPU time of the recompression (measured).
     * Small patches save a few bytes for the same fixed work, so they are skipped automatically.
     * Until both the saving ratio and the CPU cost have been observed it explores (returns true).
     */
    @Synchronized
    fun shouldRecompress(warmBytes: Long, rawLength: Int): Boolean {
        val cpuNsPerRaw = recompress.nsPerByte()
        if (cpuNsPerRaw == null || warmBytesSeen <= 0.0) return true
        val ratio = bytesSavedByRecompress / warmBytesSeen
        val expectedSaved = warmBytes * ratio
        val ioNsPerByte = (write.nsPerByte() ?: 0.0) + (read.nsPerByte() ?: 0.0)
        return expectedSaved * ioNsPerByte > rawLength * cpuNsPerRaw
    }

    /**
     * How attractive it is to recompress a Cold file now (higher = better): expected bytes saved per
     * nanosecond of work (read + recompress + write), using what was measured. Unknown parts fall back
     * to relative sizes so the ranking still prefers big files.
     */
    @Synchronized
    fun rescueScore(coldBytes: Long, rawBytes: Long): Double {
        val ratio = if (warmBytesSeen > 0.0) bytesSavedByRecompress / warmBytesSeen else 1.0
        val saved = coldBytes * ratio
        val cost = rawBytes * (recompress.nsPerByte() ?: 1.0) +
            coldBytes * ((read.nsPerByte() ?: 0.0) + (write.nsPerByte() ?: 0.0))
        return saved / (cost + 1.0)
    }

    /** Total time spent in each step so far, in milliseconds (compress, inflate, read, write, recompress, apply). */
    @Synchronized
    fun totalsMs(): DoubleArray = doubleArrayOf(
        compress.ns / 1e6, decompress.ns / 1e6, read.ns / 1e6, write.ns / 1e6, recompress.ns / 1e6, apply.ns / 1e6
    )

    @Synchronized
    fun reset() {
        compress.clear(); read.clear(); write.clear(); decompress.clear(); apply.clear(); recompress.clear()
        warmBytesSeen = 0.0
        bytesSavedByRecompress = 0.0
        warmRaw = 0.0
        warmComp = 0.0
    }
}
