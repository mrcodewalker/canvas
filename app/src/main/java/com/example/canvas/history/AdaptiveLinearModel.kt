package com.example.canvas.history

import kotlin.math.max

/**
 * Online model `time = a + b * KiB` that keeps tracking when the hardware changes speed.
 *
 * It is recursive least squares whose forgetting factor is *derived from the data* at every sample:
 * the squared prediction error is compared with the error level seen so far (`z = e² / σ²`).
 * While behaviour is stable `z ≈ 1` and λ = 1 (plain least squares, the statistically optimal choice);
 * when the device suddenly gets slower or faster `z` jumps and λ = n / (n + z - 1) drops, so old samples
 * are forgotten exactly as fast as the evidence demands. Nothing to tune.
 *
 * It also learns the tail of the error (90th percentile of observed / predicted) so deadlines can be
 * planned against a pessimistic estimate instead of the mean.
 */
class AdaptiveLinearModel {
    var count = 0
        private set

    private var a = 0.0
    private var b = 0.0
    // Internally ms and KiB so the covariance stays well conditioned
    private var p00 = P0_A
    private var p01 = 0.0
    private var p11 = P0_B

    private var sumE2 = 0.0
    private var nE = 0.0
    private val tail = P2Quantile(0.9)

    private var sumBytes = 0.0

    @Synchronized
    fun update(bytes: Long, ns: Long) {
        if (bytes <= 0L) return
        val x = bytes / 1024.0
        val y = ns.coerceAtLeast(0L) / 1_000_000.0
        sumBytes += bytes

        val predicted = a + b * x
        val e = y - predicted
        if (count >= 1 && predicted > 0.0) tail.add(y / predicted)

        var lambda = 1.0
        if (nE >= 2) {
            val sigma2 = sumE2 / nE
            if (sigma2 > 0.0) {
                val z = e * e / sigma2
                lambda = nE / (nE + max(0.0, z - 1.0))
            }
        }
        if (count >= 1) {           // the very first error only reflects the empty prior
            // the noise level is forgotten at the same rate, so after a shift it re-learns the new level
            sumE2 = lambda * sumE2 + e * e
            nE = lambda * nE + 1.0
        }

        val pf0 = p00 + p01 * x
        val pf1 = p01 + p11 * x
        val denom = lambda + pf0 + pf1 * x
        val k0 = pf0 / denom
        val k1 = pf1 / denom
        a += k0 * e
        b += k1 * e
        p00 = ((p00 - k0 * pf0) / lambda).coerceIn(0.0, P_MAX)
        p01 = ((p01 - k0 * pf1) / lambda).coerceIn(-P_MAX, P_MAX)
        p11 = ((p11 - k1 * pf1) / lambda).coerceIn(0.0, P_MAX)
        count++
    }

    /** Expected duration in ns, or null before the first sample. */
    @Synchronized
    fun predict(bytes: Long): Double? {
        if (count == 0 || bytes <= 0L) return null
        return max(0.0, a + b * (bytes / 1024.0)) * 1_000_000.0
    }

    /** Pessimistic duration (mean scaled by the observed 90th-percentile error), or null if unknown. */
    @Synchronized
    fun predictTail(bytes: Long): Double? {
        val mean = predict(bytes) ?: return null
        val q = tail.value() ?: 1.0
        return mean * max(1.0, q)
    }

    /** Fixed cost of one operation expressed in bytes (a / b), or null while not measurable. */
    @Synchronized
    fun fixedCostBytes(): Long? {
        if (count < 3 || b <= 1e-9 || a <= 0.0) return null
        return (a / b * 1024.0).toLong()
    }

    /** Average size of the samples seen so far, in bytes (0 if none). */
    @Synchronized
    fun meanBytes(): Long = if (count == 0) 0L else (sumBytes / count).toLong()

    @Synchronized
    fun reset() {
        count = 0
        a = 0.0; b = 0.0
        p00 = P0_A; p01 = 0.0; p11 = P0_B
        sumE2 = 0.0; nE = 0.0
        tail.reset()
        sumBytes = 0.0
    }

    private companion object {
        const val P0_A = 1e6   // prior variance of the fixed cost (ms^2)
        const val P0_B = 1e4   // prior variance of the per-KiB cost ((ms/KiB)^2)
        const val P_MAX = 1e8
    }
}
