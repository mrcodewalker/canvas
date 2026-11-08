package com.example.canvas.history

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Splits the RAM allowed for history between Hot and Warm by what each byte of RAM buys.
 *
 * A Hot step costs its raw size. The same step in Warm costs a few percent of that (strokes on a blank canvas
 * compress 30x to 170x), and with read-ahead and decoding-ahead a Warm step is almost as quick to undo as a Hot
 * one. So Hot only needs to hold what is about to be undone, and everything else of the budget belongs to Warm:
 * the denser tier. Giving Hot most of the RAM (as fixed budgets did) keeps ~10 steps in RAM where the same RAM
 * could keep hundreds.
 */
object TierBudgetPlanner {

    /**
     * @param totalBytes RAM the user allows for Hot + Warm together
     * @param typicalStroke median raw size of one of this user's strokes
     * @param heavyStroke raw size of a heavy stroke (90th percentile): the newest one must stay Hot
     * @param expectedRun mean number of undos in a row, or null while nothing was observed
     * @param freeRoom memory that is really free for history (after the reserve for the user's next strokes)
     * @return (hot, warm) budgets in bytes
     */
    fun split(totalBytes: Long, typicalStroke: Long, heavyStroke: Long, expectedRun: Double?, freeRoom: Long): Pair<Long, Long> {
        val total = min(totalBytes, freeRoom).coerceAtLeast(0L)
        val wanted = if (expectedRun != null) max(ceil(expectedRun).toLong() * typicalStroke, heavyStroke) else heavyStroke
        // never more than half: the denser tier gets at least the other half
        val hot = min(wanted, total / 2)
        return hot to (total - hot)
    }
}
