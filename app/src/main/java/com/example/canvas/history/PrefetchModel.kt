package com.example.canvas.history

import kotlin.math.max
import kotlin.math.min

/** How far a stroke group has been prepared for undo. */
enum class Stage { COLD, WARM, BITMAP }

/**
 * One stroke group (all dirty tiles of one stroke) as the planner sees it.
 *
 * @param stage lowest preparation level among its tiles.
 * @param coldBytes bytes that still have to be read from disk (0 unless [stage] is COLD).
 * @param decodeBytes raw pixel bytes that still have to be decompressed into a bitmap.
 */
data class GroupInput(
    val stage: Stage,
    val coldBytes: Long,
    val decodeBytes: Long
)

/**
 * The brain of the prefetcher. It decides, for the stroke groups that will be undone next, how far each
 * should be prepared (leave on disk / read into RAM / decompress into a bitmap) so that the time the user
 * waits for undo is minimal for the RAM it uses. Nothing in it is a tuned constant; it learns from:
 *
 *  1. **Behaviour** - [BehaviorPredictor] estimates, from the strokes the user just drew, how likely each
 *     further undo is.
 *  2. **Costs** - separate [AdaptiveLinearModel]s for disk read, decompression and drawing the patch. They
 *     follow speed changes of the device and also give a pessimistic (tail) estimate for deadlines.
 *  3. **Cadence** - median and fast-quartile of the time between undos (P² estimators).
 *  4. **Load** - fraction of time the disk / CPU were busy with our own work (price of a wasted read).
 *
 * Planning is a value-density knapsack: each upgrade (disk->RAM, RAM->bitmap) is worth
 * `P(reach) * time_saved - load * (1 - P) * work`, costs the extra RAM it occupies, and upgrades are taken
 * best value-per-byte first within the free RAM budget (live free heap minus the reserve). Because undo is replanned on every step,
 * only groups whose preparation must *start now* to meet their deadline are considered
 * (deadline = depth * fast cadence, measured against pessimistic costs on a simulated IO queue and CPU queue).
 */
class PrefetchModel(private val clockNs: () -> Long = System::nanoTime) {

    private val createdNs = clockNs()

    private val gapMedian = P2Quantile(0.5)
    private val gapFast = P2Quantile(0.25)
    private var lastUndoNs = -1L

    private val readModel = AdaptiveLinearModel()
    private val decodeModel = AdaptiveLinearModel()
    private val applyModel = AdaptiveLinearModel()
    private val behavior = BehaviorPredictor(clockNs)

    private var ioBusyNs = 0L
    private var cpuBusyNs = 0L

    // How heavy the user's strokes really are (raw bytes per committed stroke), tail = 90th percentile
    private val strokeBytes90 = P2Quantile(0.9)
    private val strokeBytesMedian = P2Quantile(0.5)
    private var pendingStrokeBytes = 0L   // tiles of the stroke being committed

    // ---------------------------------------------------------------- observations

    private fun flushStroke() {
        if (pendingStrokeBytes > 0L) {
            strokeBytes90.add(pendingStrokeBytes.toDouble())
            strokeBytesMedian.add(pendingStrokeBytes.toDouble())
        }
        pendingStrokeBytes = 0L
    }

    @Synchronized
    fun recordUndo() {
        flushStroke()
        val now = clockNs()
        if (lastUndoNs >= 0) {
            val gap = (now - lastUndoNs).toDouble()
            gapMedian.add(gap)
            gapFast.add(gap)
        }
        lastUndoNs = now
        behavior.recordUndo()
    }

    /** A commit of a stroke tile; [newStroke] is true for the first tile of each stroke. */
    @Synchronized
    fun recordCommit(newStroke: Boolean, bytes: Long) {
        if (newStroke) flushStroke()
        pendingStrokeBytes += bytes
        behavior.recordCommit(newStroke, bytes)
        lastUndoNs = -1L
    }

    /** A redo/clear: ends the current undo run. */
    @Synchronized
    fun recordRunBreak() {
        behavior.recordBreak()
        lastUndoNs = -1L
    }

    @Synchronized
    fun recordFetch(bytes: Long, durationNs: Long) {
        if (bytes <= 0L) return
        readModel.update(bytes, durationNs)
        ioBusyNs += durationNs.coerceAtLeast(0L)
    }

    @Synchronized
    fun recordDecode(rawBytes: Long, durationNs: Long) {
        if (rawBytes <= 0L) return
        decodeModel.update(rawBytes, durationNs)
        cpuBusyNs += durationNs.coerceAtLeast(0L)
    }

    @Synchronized
    fun recordApply(rawBytes: Long, durationNs: Long) {
        if (rawBytes <= 0L) return
        applyModel.update(rawBytes, durationNs)
    }

    @Synchronized
    fun reset() {
        gapMedian.reset()
        gapFast.reset()
        lastUndoNs = -1L
        readModel.reset()
        decodeModel.reset()
        applyModel.reset()
        behavior.reset()
        strokeBytes90.reset()
        strokeBytesMedian.reset()
        pendingStrokeBytes = 0L
        ioBusyNs = 0L
        cpuBusyNs = 0L
    }

    // ---------------------------------------------------------------- estimates

    /**
     * Memory that must stay free for the user's own next actions: capturing the "before" pixels of one
     * more stroke plus decoding one back (twice the heavy-stroke size the user actually draws).
     * Prefetching may only use what is left of the free heap after this.
     */
    @Synchronized
    fun medianStrokeBytes(): Long? = strokeBytesMedian.value()?.toLong()

    @Synchronized
    fun heavyStrokeBytes(): Long? = strokeBytes90.value()?.toLong()

    /** Mean length of the user's undo runs, or null until one finished. */
    @Synchronized fun expectedRun(): Double? = behavior.meanRunLength()

    /** Length that 90% of the user's undo runs did not exceed. */
    @Synchronized fun deepRun(): Int? = behavior.runQuantile(0.9)

    @Synchronized
    fun memoryReserveBytes(): Long = ((strokeBytes90.value() ?: 0.0) * 2).toLong()

    /** `depth -> probability the user still undoes down to that many strokes from the top`. */
    @Synchronized
    fun reachFunction(): (Int) -> Double = behavior.reachFunction()

    /** Fixed cost of one extra patch read, in bytes-equivalent (null until measured). */
    @Synchronized
    fun patchOverheadBytes(): Long? = readModel.fixedCostBytes()

    @Synchronized fun cadenceNs(): Double? = gapMedian.value()
    @Synchronized fun predictFetchNs(bytes: Long): Double? = readModel.predict(bytes)
    @Synchronized fun predictDecodeNs(rawBytes: Long): Double? = decodeModel.predict(rawBytes)
    @Synchronized fun predictApplyNs(rawBytes: Long): Double? = applyModel.predict(rawBytes)

    @Synchronized
    fun ioUtilization(): Double = min(1.0, ioBusyNs.toDouble() / (clockNs() - createdNs).coerceAtLeast(1L))

    @Synchronized
    fun cpuUtilization(): Double = min(1.0, cpuBusyNs.toDouble() / (clockNs() - createdNs).coerceAtLeast(1L))

    // ---------------------------------------------------------------- planning

    private class Cand(
        val depth: Int,
        val group: GroupInput,
        val readMean: Double?,
        val readTail: Double?,
        val decMean: Double?,
        val decTail: Double?,
        val p: Double
    )

    private class Item(val cand: Int, val bitmap: Boolean, val value: Double, val ram: Long) {
        val density: Double get() = value / max(ram, 1L).toDouble()
    }

    /**
     * @param groups upcoming undo steps, nearest first (consumed lazily).
     * @return the stage each consumed group should be brought to, in the same order (shorter than the
     *   number of groups pulled from [groups] when scanning stopped).
     */
    @Synchronized
    fun plan(groups: Sequence<GroupInput>, ramBudget: Long, allowBitmap: Boolean = true): List<Stage> {
        val cadMed = gapMedian.value()
        val cadFast = gapFast.value()
        val reach = behavior.reachFunction()
        val ioU = ioUtilization()
        val cpuU = cpuUtilization()

        // ---- scan: keep only groups whose preparation has to start before the next replan
        val cands = ArrayList<Cand>()
        var depth = 0
        var backlog = 0.0
        var maxWork = (readModel.predictTail(readModel.meanBytes()) ?: 0.0) +
            (decodeModel.predictTail(decodeModel.meanBytes()) ?: 0.0)
        for (g in groups) {
            depth++
            if (depth > 1 && (cadFast == null || cadMed == null)) break
            val rm = if (g.coldBytes > 0) readModel.predict(g.coldBytes) else 0.0
            val rt = if (g.coldBytes > 0) readModel.predictTail(g.coldBytes) else 0.0
            val dm = if (g.decodeBytes > 0) decodeModel.predict(g.decodeBytes) else 0.0
            val dt = if (g.decodeBytes > 0) decodeModel.predictTail(g.decodeBytes) else 0.0
            val work = (rt ?: 0.0) + (dt ?: 0.0)
            maxWork = max(maxWork, work)
            if (depth > 1 && depth * cadFast!! - maxWork > cadMed!! + backlog) break // can wait for next replan
            if (depth > 1 && backlog > depth * cadFast!!) break // resources already saturated: deeper is futile
            cands.add(Cand(depth, g, rm, rt, dm, dt, reach(depth)))
            backlog += work
        }
        if (cands.isEmpty()) return emptyList()

        // ---- items: every possible upgrade with its expected value and RAM cost
        val items = ArrayList<Item>()
        val chosenW = BooleanArray(cands.size)
        val chosenB = BooleanArray(cands.size)
        var ramLeft = ramBudget

        for ((i, c) in cands.withIndex()) {
            val g = c.group
            if (g.stage == Stage.COLD && g.coldBytes > 0) {
                if (c.depth == 1) {
                    chosenW[i] = true            // the next undo needs these bytes anyway
                    ramLeft -= g.coldBytes
                } else if (c.readMean != null) {
                    val price = max(ioU, g.coldBytes.toDouble() / max(ramBudget, 1L))
                    val v = c.p * c.readMean - price * (1.0 - c.p) * c.readMean
                    items.add(Item(i, false, v, g.coldBytes))
                }
            }
            if (allowBitmap && g.stage != Stage.BITMAP && g.decodeBytes > 0) {
                if (c.decMean != null) {
                    val price = max(cpuU, g.decodeBytes.toDouble() / max(ramBudget, 1L))
                    val v = c.p * c.decMean - price * (1.0 - c.p) * c.decMean
                    items.add(Item(i, true, v, g.decodeBytes))
                } else if (c.depth == 1) {
                    items.add(Item(i, true, 1.0, g.decodeBytes)) // explore: take the first decode sample
                }
            }
        }

        items.sortByDescending { it.density }
        var changed = true
        val taken = HashSet<Item>()
        while (changed) {
            changed = false
            for (it in items) {
                if (it in taken || it.value <= 0.0) continue
                val c = cands[it.cand]
                if (!it.bitmap) {
                    if (it.ram <= ramLeft) {
                        chosenW[it.cand] = true; ramLeft -= it.ram; taken.add(it); changed = true
                    }
                } else {
                    val ready = c.group.stage != Stage.COLD || chosenW[it.cand]
                    if (ready && it.ram <= ramLeft) {
                        chosenB[it.cand] = true; ramLeft -= it.ram; taken.add(it); changed = true
                    }
                }
            }
        }

        // ---- feasibility: simulate one IO queue and one CPU queue. A job helps if it can *start* before
        // the user arrives (even a partly done read shortens the wait); otherwise it is pointless.
        var ioFree = 0.0
        var cpuFree = 0.0
        for ((i, c) in cands.withIndex()) {
            if (!chosenW[i] && !chosenB[i]) continue
            val deadline = if (cadFast != null) c.depth * cadFast else Double.MAX_VALUE
            val reads = chosenW[i] && c.group.stage == Stage.COLD
            if (c.depth > 1 && reads && ioFree >= deadline) {
                // everything deeper queues behind this read: stop here
                for (j in i until cands.size) { chosenW[j] = false; chosenB[j] = false }
                break
            }
            val ioDone = if (reads) ioFree + (c.readTail ?: 0.0) else ioFree
            if (chosenB[i]) {
                val cpuStart = max(cpuFree, ioDone)
                if (c.depth > 1 && cpuStart >= deadline) {
                    chosenB[i] = false
                } else {
                    cpuFree = cpuStart + (c.decTail ?: 0.0)
                }
            }
            ioFree = ioDone
        }

        return cands.mapIndexed { i, c ->
            when {
                chosenB[i] -> Stage.BITMAP
                chosenW[i] && c.group.stage == Stage.COLD -> Stage.WARM
                else -> c.group.stage
            }
        }
    }
}

/** P² streaming quantile estimator (Jain & Chlamtac): O(1) memory, no window size to tune. */
class P2Quantile(private val p: Double = 0.5) {
    private val q = DoubleArray(5)
    private val pos = IntArray(5)
    private val desired = DoubleArray(5)
    private val step = doubleArrayOf(0.0, p / 2, p, (1 + p) / 2, 1.0)
    var count = 0
        private set

    fun reset() {
        count = 0
    }

    fun add(x: Double) {
        if (count < 5) {
            q[count++] = x
            if (count == 5) {
                q.sort()
                for (i in 0..4) pos[i] = i + 1
                desired[0] = 1.0
                desired[1] = 1 + 2 * p
                desired[2] = 1 + 4 * p
                desired[3] = 3 + 2 * p
                desired[4] = 5.0
            }
            return
        }
        count++
        val k: Int = when {
            x < q[0] -> { q[0] = x; 0 }
            x < q[1] -> 0
            x < q[2] -> 1
            x < q[3] -> 2
            x <= q[4] -> 3
            else -> { q[4] = x; 3 }
        }
        for (i in k + 1..4) pos[i]++
        for (i in 0..4) desired[i] += step[i]
        for (i in 1..3) {
            val d = desired[i] - pos[i]
            if ((d >= 1 && pos[i + 1] - pos[i] > 1) || (d <= -1 && pos[i - 1] - pos[i] < -1)) {
                val s = if (d >= 0) 1 else -1
                var qn = parabolic(i, s)
                if (qn <= q[i - 1] || qn >= q[i + 1]) qn = linear(i, s)
                q[i] = qn
                pos[i] += s
            }
        }
    }

    fun value(): Double? {
        if (count == 0) return null
        if (count < 5) {
            val copy = q.copyOf(count)
            copy.sort()
            return copy[((count - 1) * p).toInt().coerceIn(0, count - 1)]
        }
        return q[2]
    }

    private fun parabolic(i: Int, s: Int): Double {
        val a = s.toDouble() / (pos[i + 1] - pos[i - 1])
        val b1 = (pos[i] - pos[i - 1] + s) * (q[i + 1] - q[i]) / (pos[i + 1] - pos[i])
        val b2 = (pos[i + 1] - pos[i] - s) * (q[i] - q[i - 1]) / (pos[i] - pos[i - 1])
        return q[i] + a * (b1 + b2)
    }

    private fun linear(i: Int, s: Int): Double =
        q[i] + s * (q[i + s] - q[i]) / (pos[i + s] - pos[i])
}
