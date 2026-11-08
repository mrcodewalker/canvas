package com.example.canvas.history

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Predicts how many undos in a row the user will do, from *what they just drew*.
 *
 * When an undo run starts it records a feature vector describing the drawing activity that led to it:
 *  - how many strokes were drawn since the previous undo run (people tend to undo what they just drew),
 *  - how large the last stroke was (a full-canvas fill is undone very differently from a dot),
 *  - how long ago the last stroke was committed.
 * When the run ends the observed length is stored with those features. To predict the current run, the
 * k most similar past situations vote (k = ceil(sqrt(n)); distances are standardised by the observed
 * spread of each feature), so every number comes from the user's own data.
 */
class BehaviorPredictor(private val clockNs: () -> Long = System::nanoTime) {

    private class Sample(val features: DoubleArray, val length: Int)

    private val samples = ArrayList<Sample>()

    // Welford statistics of each feature across stored samples
    private val mean = DoubleArray(FEATURES)
    private val m2 = DoubleArray(FEATURES)

    private var commitsSinceUndo = 0
    private var lastCommitNs = -1L
    private var lastStrokeBytes = 0L

    private var runLength = 0
    private var runFeatures: DoubleArray? = null

    val currentRunLength: Int get() = runLength

    @Synchronized
    fun recordCommit(newStroke: Boolean, bytes: Long) {
        if (newStroke) {
            commitsSinceUndo++
            lastStrokeBytes = bytes
        } else {
            lastStrokeBytes += bytes
        }
        lastCommitNs = clockNs()
        endRun()
    }

    @Synchronized
    fun recordUndo() {
        if (runLength == 0) {
            runFeatures = currentFeatures()
            commitsSinceUndo = 0
        }
        runLength++
    }

    /** A redo (or any other action that is not an undo) ends the run. */
    @Synchronized
    fun recordBreak() = endRun()

    @Synchronized
    fun reset() {
        samples.clear()
        mean.fill(0.0)
        m2.fill(0.0)
        commitsSinceUndo = 0
        lastCommitNs = -1L
        lastStrokeBytes = 0L
        runLength = 0
        runFeatures = null
    }

    /**
     * Returns `P(reach)(d)`: probability that the user, already [currentRunLength] undos into the run,
     * performs at least d more. Add-one smoothing; 1.0 while there is no history.
     */
    @Synchronized
    fun reachFunction(): (Int) -> Double {
        val r = max(runLength, 1)
        val lens = neighbourLengths()
        if (lens.isEmpty()) return { 1.0 }
        lens.sort()
        val atLeastNow = lens.size - lowerBound(lens, r)
        return { d ->
            val atLeastTarget = lens.size - lowerBound(lens, r + d)
            (atLeastTarget + 1.0) / (atLeastNow + 1.0)
        }
    }

    /** Mean number of undos in a run so far, or null with no history. */
    @Synchronized
    fun meanRunLength(): Double? =
        if (samples.isEmpty()) null else samples.sumOf { it.length }.toDouble() / samples.size

    /** Run length that [q] of the past runs did not exceed, or null with no history. */
    @Synchronized
    fun runQuantile(q: Double): Int? {
        if (samples.isEmpty()) return null
        val lens = samples.map { it.length }.sorted()
        return lens[((lens.size - 1) * q).toInt().coerceIn(0, lens.size - 1)]
    }

    private fun currentFeatures(): DoubleArray {
        val sinceMs = if (lastCommitNs >= 0) (clockNs() - lastCommitNs) / 1_000_000.0 else 0.0
        return doubleArrayOf(
            ln(1.0 + commitsSinceUndo),
            ln(1.0 + lastStrokeBytes),
            ln(1.0 + max(0.0, sinceMs))
        )
    }

    private fun neighbourLengths(): IntArray {
        val n = samples.size
        if (n == 0) return IntArray(0)
        if (n < 2) return IntArray(n) { samples[it].length }

        val f = runFeatures ?: currentFeatures()
        val std = DoubleArray(FEATURES) { j ->
            val s = sqrt(m2[j] / (n - 1))
            if (s > 1e-12) s else 1.0
        }
        val dist = DoubleArray(n)
        for (i in 0 until n) {
            var d = 0.0
            for (j in 0 until FEATURES) {
                val z = (f[j] - samples[i].features[j]) / std[j]
                d += z * z
            }
            dist[i] = d
        }
        val k = ceil(sqrt(n.toDouble())).toInt().coerceIn(1, n)
        val order = (0 until n).sortedBy { dist[it] }
        return IntArray(k) { samples[order[it]].length }
    }

    private fun endRun() {
        val f = runFeatures
        if (runLength > 0 && f != null) {
            samples.add(Sample(f, runLength))
            val n = samples.size
            for (j in 0 until FEATURES) {
                val delta = f[j] - mean[j]
                mean[j] += delta / n
                m2[j] += delta * (f[j] - mean[j])
            }
        }
        runLength = 0
        runFeatures = null
    }

    private fun lowerBound(a: IntArray, value: Int): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private companion object {
        const val FEATURES = 3
    }
}
