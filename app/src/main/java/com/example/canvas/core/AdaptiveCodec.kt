package com.example.canvas.core

/**
 * How the bytes of one patch are stored. The first byte tells which: a zlib stream (every patch written before
 * this codec existed, and the [DEFLATE] choice) always starts with a byte whose low nibble is 8, so the tags 1..3
 * can never be mistaken for it and old Cold files stay readable.
 */
enum class PatchFormat {
    /** Plain zlib stream (no tag). */
    DEFLATE,

    /** Every pixel is the same: 5 bytes, nothing to compress or decompress. */
    SOLID,

    /** Runs of equal pixels: `varint length + 4 bytes of pixel`. Linear time both ways, no dictionary. */
    RLE,

    /** Each byte minus the byte one pixel to its left (the PNG "Sub" filter), then zlib. For gradients and soft edges. */
    DELTA
}

/** How a patch looks to the chooser: how many pixels, and how many runs of equal neighbouring pixels they form. */
class PixelProfile(val pixels: Int, val runs: Int, val capped: Boolean) {
    /** `floor(log2(pixels per run))`: 0 for noisy patches, large for flat ones. */
    val runClass: Int
        get() = if (capped || runs <= 0) 0 else (31 - Integer.numberOfLeadingZeros((pixels / runs).coerceAtLeast(1))).coerceAtMost(15)
}

/** The encoders and decoders of the formats above. Stateless; the choice between them is [CodecSelector]'s job. */
object AdaptiveCodec {

    const val TAG_SOLID: Byte = 1
    const val TAG_RLE: Byte = 2
    const val TAG_DELTA: Byte = 3

    fun formatOf(bytes: ByteArray): PatchFormat = when {
        bytes.isEmpty() -> PatchFormat.DEFLATE
        bytes[0] == TAG_SOLID -> PatchFormat.SOLID
        bytes[0] == TAG_RLE -> PatchFormat.RLE
        bytes[0] == TAG_DELTA -> PatchFormat.DELTA
        else -> PatchFormat.DEFLATE
    }

    private fun pixelAt(a: ByteArray, i: Int): Int =
        (a[i].toInt() and 0xFF) or ((a[i + 1].toInt() and 0xFF) shl 8) or
            ((a[i + 2].toInt() and 0xFF) shl 16) or ((a[i + 3].toInt() and 0xFF) shl 24)

    /**
     * One pass over the pixels counting runs of equal neighbours. Stops as soon as there are more than
     * [maxRuns]: such a patch is not worth run-length coding and the rest of the scan would be wasted.
     */
    fun profile(raw: ByteArray, length: Int, maxRuns: Int): PixelProfile {
        val pixels = length / 4
        if (pixels == 0) return PixelProfile(0, 0, false)
        var runs = 1
        var prev = pixelAt(raw, 0)
        var i = 4
        val end = pixels * 4
        while (i < end) {
            val p = pixelAt(raw, i)
            if (p != prev) {
                if (++runs > maxRuns) return PixelProfile(pixels, runs, true)
                prev = p
            }
            i += 4
        }
        return PixelProfile(pixels, runs, false)
    }

    // ---------------------------------------------------------------- SOLID / RLE

    fun encodeSolid(raw: ByteArray): ByteArray = byteArrayOf(TAG_SOLID, raw[0], raw[1], raw[2], raw[3])

    fun encodeRle(raw: ByteArray, length: Int, runs: Int): ByteArray {
        val out = ByteArray(1 + runs * 9)        // worst case: 5-byte varint + 4 pixel bytes per run
        out[0] = TAG_RLE
        var o = 1
        val end = length / 4 * 4
        var i = 0
        while (i < end) {
            val p = pixelAt(raw, i)
            var j = i + 4
            while (j < end && pixelAt(raw, j) == p) j += 4
            var n = (j - i) / 4
            while (n >= 0x80) {
                out[o++] = ((n and 0x7F) or 0x80).toByte()
                n = n ushr 7
            }
            out[o++] = n.toByte()
            out[o++] = raw[i]; out[o++] = raw[i + 1]; out[o++] = raw[i + 2]; out[o++] = raw[i + 3]
            i = j
        }
        return out.copyOf(o)
    }

    /** Fills [dest] from a SOLID or RLE payload; throws if the stream does not describe exactly [rawLength] bytes. */
    fun decodeRunsInto(bytes: ByteArray, dest: ByteArray, rawLength: Int) {
        if (bytes[0] == TAG_SOLID) {
            if (bytes.size != 5) throw IllegalStateException("Corrupt solid patch")
            var i = 0
            while (i + 4 <= rawLength) {
                dest[i] = bytes[1]; dest[i + 1] = bytes[2]; dest[i + 2] = bytes[3]; dest[i + 3] = bytes[4]
                i += 4
            }
            return
        }
        var o = 1
        var d = 0
        while (o < bytes.size) {
            var n = 0
            var shift = 0
            while (true) {
                if (o >= bytes.size || shift > 28) throw IllegalStateException("Corrupt run-length patch")
                val b = bytes[o++].toInt()
                n = n or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            if (o + 4 > bytes.size || d + n.toLong() * 4 > rawLength) throw IllegalStateException("Corrupt run-length patch")
            val b0 = bytes[o]; val b1 = bytes[o + 1]; val b2 = bytes[o + 2]; val b3 = bytes[o + 3]
            o += 4
            repeat(n) {
                dest[d] = b0; dest[d + 1] = b1; dest[d + 2] = b2; dest[d + 3] = b3
                d += 4
            }
        }
        if (d != rawLength) throw IllegalStateException("Run-length size mismatch: expected $rawLength bytes but got $d")
    }

    // ---------------------------------------------------------------- DELTA

    /** Sub filter over the whole buffer (rows are not special: a pixel's left neighbour is simply the 4 bytes before). */
    fun deltaFilter(raw: ByteArray, length: Int, out: ByteArray) {
        val head = minOf(4, length)
        for (i in 0 until head) out[i] = raw[i]
        for (i in 4 until length) out[i] = (raw[i] - raw[i - 4]).toByte()
    }

    fun deltaUnfilterInPlace(buf: ByteArray, length: Int) {
        for (i in 4 until length) buf[i] = (buf[i] + buf[i - 4]).toByte()
    }
}

/** A finished encoding and which format it ended up in. */
class EncodedPatch(val patch: CompressedPatch, val format: PatchFormat)

/**
 * Chooses the format of each patch from what was measured on this device with this user's pixels.
 *
 * The idea: the right way to store a patch depends on what is *in* it, and a one-pass scan tells that much more
 * cheaply than compressing does. So patches are grouped by `runClass` (how long the runs of equal pixels are) and,
 * per group, the selector keeps the average size and CPU cost (encode and decode, per raw byte) of every format
 * that is possible for that group. A format is picked when it has the lowest
 *
 *     bytes * price + encode time + decode time
 *
 * where `price` is what one stored byte costs this device (disk write + read, measured; [FALLBACK_NS_PER_BYTE]
 * until it is). A group is tried on every format its first [TRIALS] patches and again every [RETRY_EVERY]th, so
 * the tables follow the user when they change brush. Patches with a single colour skip all this: 5 bytes, no CPU.
 */
class CodecSelector {

    /**
     * What one format costs for one group of patches. Times keep the last [WINDOW] samples and report their
     * minimum: a measurement can only be disturbed upwards (cold code, GC, another thread), never downwards.
     */
    private class Stat {
        var n = 0
        var bytesPerRaw = 0.0
        val enc = DoubleArray(WINDOW)
        var nEnc = 0
        val dec = DoubleArray(WINDOW)
        var nDec = 0
        val encNsPerRaw: Double get() = (0 until minOf(nEnc, WINDOW)).minOfOrNull { enc[it] } ?: 0.0
        val decNsPerRaw: Double get() = (0 until minOf(nDec, WINDOW)).minOfOrNull { dec[it] } ?: 0.0
    }

    private val stats = HashMap<Int, Stat>()
    private val seen = IntArray(16)

    /** How many patches were stored in each format (index = [PatchFormat.ordinal]). */
    private val chosen = IntArray(PatchFormat.values().size)

    @Volatile
    var trialNs: Long = 0L
        private set

    /** Bytes the chosen format saved against plain Deflate on patches where both were measured. */
    @Volatile
    var savedVsDeflateBytes: Long = 0L
        private set

    @Synchronized
    fun count(f: PatchFormat): Int = chosen[f.ordinal]

    @Synchronized
    fun reset() {
        stats.clear()
        seen.fill(0)
        chosen.fill(0)
        trialNs = 0L
        savedVsDeflateBytes = 0L
    }

    private fun key(cls: Int, f: PatchFormat) = cls * 8 + f.ordinal

    private fun update(cls: Int, f: PatchFormat, rawLen: Int, outBytes: Int, encNs: Long, decNs: Long?) {
        val s = synchronized(this) { stats.getOrPut(key(cls, f)) { Stat() } }
        synchronized(s) {
            val a = 1.0 / minOf(s.n + 1, 16)               // running mean that slowly forgets
            s.bytesPerRaw += (outBytes.toDouble() / rawLen - s.bytesPerRaw) * a
            s.n++
            s.enc[s.nEnc++ % WINDOW] = encNs.toDouble() / rawLen
            if (decNs != null) s.dec[s.nDec++ % WINDOW] = decNs.toDouble() / rawLen
        }
    }

    private fun expectedCost(cls: Int, f: PatchFormat, rawLen: Int, price: Double): Double? {
        val s = synchronized(this) { stats[key(cls, f)] } ?: return null
        synchronized(s) {
            if (s.n == 0 || s.nDec == 0) return null
            return rawLen * (s.bytesPerRaw * price + s.encNsPerRaw + s.decNsPerRaw)
        }
    }

    private fun trialsDone(cls: Int, f: PatchFormat): Boolean {
        val s = synchronized(this) { stats[key(cls, f)] } ?: return false
        return synchronized(s) { s.nDec >= TRIALS }
    }

    private fun encodeAs(f: PatchFormat, raw: ByteArray, length: Int, level: Int, prof: PixelProfile): ByteArray = when (f) {
        PatchFormat.RLE -> AdaptiveCodec.encodeRle(raw, length, prof.runs)
        PatchFormat.DELTA -> {
            val tmp = BufferPool.acquire(length)
            try {
                AdaptiveCodec.deltaFilter(raw, length, tmp)
                val z = PatchCodec.compress(tmp, level, length).compressedBytes
                val out = ByteArray(z.size + 1)
                out[0] = AdaptiveCodec.TAG_DELTA
                System.arraycopy(z, 0, out, 1, z.size)
                out
            } finally {
                BufferPool.release(tmp)
            }
        }
        else -> PatchCodec.compress(raw, level, length).compressedBytes
    }

    private fun timeDecode(bytes: ByteArray, length: Int): Long {
        val scratch = BufferPool.acquire(length)
        try {
            val t = System.nanoTime()
            PatchCodec.decompressInto(bytes, scratch, length, 0L)
            return System.nanoTime() - t
        } finally {
            BufferPool.release(scratch)
        }
    }

    /**
     * Encodes [length] bytes of [raw] in the format this patch is expected to be cheapest in.
     * @param level zlib level used by the Deflate-based formats
     * @param priceNsPerByte what keeping one more byte costs (disk write + read, ns); null = not measured yet
     */
    fun encode(raw: ByteArray, length: Int, level: Int, priceNsPerByte: Double?): EncodedPatch {
        val crc = PatchCodec.computeCrc32(raw, length)
        fun wrap(bytes: ByteArray, f: PatchFormat): EncodedPatch {
            synchronized(this) { chosen[f.ordinal]++ }
            return EncodedPatch(CompressedPatch(bytes, length, crc), f)
        }
        if (length < MIN_BYTES || length % 4 != 0) return wrap(PatchCodec.compress(raw, level, length).compressedBytes, PatchFormat.DEFLATE)

        val prof = AdaptiveCodec.profile(raw, length, maxRuns = length / 4 / 3)
        if (!prof.capped && prof.runs == 1) return wrap(AdaptiveCodec.encodeSolid(raw), PatchFormat.SOLID)

        val cls = prof.runClass
        val price = priceNsPerByte?.takeIf { it > 0.0 } ?: FALLBACK_NS_PER_BYTE
        val candidates = buildList {
            add(PatchFormat.DEFLATE)
            // the filter only pays on smooth data; on flat patches it is a wasted trial
            if (prof.capped || cls <= 2) add(PatchFormat.DELTA)
            if (!prof.capped) add(PatchFormat.RLE)
        }

        val round = synchronized(this) { ++seen[cls] }
        // a retrial is a short burst so that every format gets fresh samples and the old ones leave the window
        val explore = candidates.any { !trialsDone(cls, it) } || round % RETRY_EVERY < RETRY_BURST

        if (!explore) {
            // never a format that stores more than Deflate does for this kind of patch: RAM must not get worse
            val deflateBytes = synchronized(this) { stats[key(cls, PatchFormat.DEFLATE)] }?.let { synchronized(it) { it.bytesPerRaw } }
            val best = candidates.mapNotNull { f ->
                val s = synchronized(this) { stats[key(cls, f)] }
                val ok = f == PatchFormat.DEFLATE || deflateBytes == null || (s != null && synchronized(s) { s.bytesPerRaw } <= deflateBytes)
                if (!ok) null else expectedCost(cls, f, length, price)?.let { f to it }
            }.minByOrNull { it.second }?.first ?: PatchFormat.DEFLATE
            val t = System.nanoTime()
            val bytes = encodeAs(best, raw, length, level, prof)
            update(cls, best, length, bytes.size, System.nanoTime() - t, null)
            return wrap(bytes, best)
        }

        // trial: every possible format on this very patch, measured; keep the cheapest
        val t0 = System.nanoTime()
        var bestF = PatchFormat.DEFLATE
        var bestBytes: ByteArray? = null
        var bestCost = Double.MAX_VALUE
        var deflateSize = 0
        for (f in candidates) {
            val t = System.nanoTime()
            val bytes = encodeAs(f, raw, length, level, prof)
            val enc = System.nanoTime() - t
            val dec = timeDecode(bytes, length)
            update(cls, f, length, bytes.size, enc, dec)
            if (f == PatchFormat.DEFLATE) deflateSize = bytes.size
            val cost = bytes.size * price + enc + dec
            val notBigger = f == PatchFormat.DEFLATE || bytes.size <= deflateSize
            if (notBigger && cost < bestCost) {
                bestCost = cost; bestF = f; bestBytes = bytes
            }
        }
        val spent = System.nanoTime() - t0
        synchronized(this) {
            trialNs += spent
            if (bestF != PatchFormat.DEFLATE) savedVsDeflateBytes += deflateSize - bestBytes!!.size
        }
        return wrap(bestBytes!!, bestF)
    }

    companion object {
        const val TRIALS = 2
        const val WINDOW = 3
        const val RETRY_EVERY = 64
        const val RETRY_BURST = 2
        const val MIN_BYTES = 256

        /** About 250 MB/s of flash each way: used until the real disk speed has been measured. */
        const val FALLBACK_NS_PER_BYTE = 8.0
    }
}
