package com.example.canvas.history

import kotlin.math.ceil

/**
 * Learns how deep this user redoes, so the redo stack is sized by habit instead of a fixed count.
 *
 * A redo run is a series of redos with nothing else in between (an undo or a new stroke ends it). The lengths
 * of the finished runs give the probability that a redo reaches depth d: `P(d) = (runs >= d + 1) / (n + 1)`
 * (add-one smoothing, 1.0 while nothing was observed). Steps deeper than the 90th percentile of the observed
 * runs are kept only as the stroke itself (tiny, replayable) and lose their pixels first when memory is short.
 */
class RedoBehavior {
    private val finished = ArrayList<Int>()   // sorted
    private var current = 0

    @Synchronized
    fun onRedo() {
        current++
    }

    /** An undo or a new stroke: the redo run, if any, is over. */
    @Synchronized
    fun onOther() {
        if (current > 0) {
            val idx = lowerBound(current)
            finished.add(idx, current)
        }
        current = 0
    }

    /** Probability that a redo run reaches at least [depth] steps. */
    @Synchronized
    fun reach(depth: Int): Double {
        if (finished.isEmpty()) return 1.0
        val atLeast = finished.size - lowerBound(depth)
        return (atLeast + 1.0) / (finished.size + 1.0)
    }

    /** Depth that 90% of the observed redo runs did not exceed; null before any run finished. */
    @Synchronized
    fun typicalDepth(): Int? {
        if (finished.isEmpty()) return null
        return finished[ceil((finished.size - 1) * 0.9).toInt().coerceIn(0, finished.size - 1)]
    }

    @Synchronized
    fun reset() {
        finished.clear()
        current = 0
    }

    private fun lowerBound(value: Int): Int {
        var lo = 0
        var hi = finished.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (finished[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
