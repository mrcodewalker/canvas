package com.example.canvas.history

/**
 * Chooses the compression level for Warm by measuring it on this device with this user's pixels.
 *
 * For the first few patches the same pixels are compressed at every candidate level, which is the only fair
 * comparison (different patches compress differently). Levels are then walked upwards and a higher level is
 * accepted only while it improves the ratio at least as much, in relative terms, as it slows compression down:
 * `ratio gain >= time increase`. On stroke-like pixels this accepts the cheap levels that are practically free
 * and stops where zlib switches to its slower lazy matching, where each extra percent costs several percent of CPU.
 */
class WarmLevelCalibrator(private val candidates: List<Int> = listOf(1, 3)) {

    private class Stat {
        var raw = 0.0
        var out = 0.0
        var ns = 0.0
    }

    private val stats = HashMap<Int, Stat>()

    @Volatile
    var calibratedPatches = 0
        private set

    /** Levels that are compared on the same pixels. */
    val levels: List<Int> get() = candidates

    fun needsCalibration(): Boolean = calibratedPatches < CALIBRATION_PATCHES

    @Synchronized
    fun record(level: Int, rawBytes: Long, outBytes: Long, ns: Long) {
        val s = stats.getOrPut(level) { Stat() }
        s.raw += rawBytes
        s.out += outBytes
        s.ns += ns.coerceAtLeast(1L)
    }

    @Synchronized
    fun patchDone() {
        calibratedPatches++
    }

    /** The level to use now; the cheapest candidate until every candidate has been measured. */
    @Synchronized
    fun best(): Int {
        var current = candidates.first()
        val cs = stats[current] ?: return current
        var ratio = cs.raw / cs.out.coerceAtLeast(1.0)
        var time = cs.ns / cs.raw.coerceAtLeast(1.0)
        for (next in candidates.drop(1)) {
            val s = stats[next] ?: break
            val r = s.raw / s.out.coerceAtLeast(1.0)
            val t = s.ns / s.raw.coerceAtLeast(1.0)
            val gain = r / ratio - 1.0
            val slowdown = t / time - 1.0
            if (gain >= slowdown) {
                current = next
                ratio = r
                time = t
            } else {
                break
            }
        }
        return current
    }

    @Synchronized
    fun reset() {
        stats.clear()
        calibratedPatches = 0
    }

    private companion object {
        /** Patches compared at every level before the choice is locked in (the exploration budget). */
        const val CALIBRATION_PATCHES = 3
    }
}
