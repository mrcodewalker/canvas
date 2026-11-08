package com.example.canvas.history

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.max
import kotlin.math.min

/**
 * Discrete-event simulation of the undo experience under three prefetch policies:
 *  - NONE   : every Cold patch is read and decoded on demand.
 *  - FIXED5 : the old policy - after each undo read the next 5 Cold patches into Warm (no decoding ahead).
 *  - BRAIN  : [PrefetchModel] (behaviour prediction + stage-aware knapsack + drift-tracking costs).
 *
 * Time is virtual (milliseconds). The IO queue and the CPU queue are sequential; queued (not yet started)
 * work is replaced at every undo, work already running finishes - exactly like [Prefetcher].
 * The test prints a comparison table (useful for the report) and asserts the BRAIN is not worse.
 */
class PrefetchPolicySimulationTest {

    private enum class Policy { NONE, FIXED5, BRAIN }

    private class Hardware(
        val readFixedMs: Double = 0.5,
        val readMsPerKb: Double = 0.02,
        val decodeMsPerKb: Double = 0.03,
        val applyMsPerKb: Double = 0.01,
        val readSlowdown: (Double) -> Double = { 1.0 }
    ) {
        fun read(coldBytes: Long, t: Double) = (readFixedMs + coldBytes / 1024.0 * readMsPerKb) * readSlowdown(t)
        fun decode(raw: Long) = raw / 1024.0 * decodeMsPerKb
        fun apply(raw: Long) = raw / 1024.0 * applyMsPerKb
    }

    private class Group(val raw: Long, val coldBytes: Long) {
        var nativeHot = false
        var warmAt: Double? = null
        var bitmapAt: Double? = null
        var warmReported = false
        var bitmapReported = false
    }

    private class Job(val group: Group, val start: Double, val end: Double, val io: Boolean)

    class Result(val name: String, val totalLatencyMs: Double, val p95Ms: Double, val avgRamKb: Double, val ioBusyMs: Double)

    private class Workload(
        val initialColdGroups: Int,
        val sessions: Int,
        val gapMs: Double,
        val hw: Hardware,
        val ramBudget: Long = 7_000_000,
        val undoWhatYouDrew: Boolean = true,
        val seed: Long = 7
    )

    private fun simulate(policy: Policy, w: Workload): Result {
        val rnd = Random(w.seed)
        fun newGroup(): Group {
            val raw = (64_000 + rnd.nextInt(900_000)).toLong()
            return Group(raw, raw / 5)
        }

        var t = 0.0
        val clock: () -> Long = { (t * 1_000_000).toLong() }
        val model = PrefetchModel(clock)
        val stack = ArrayList<Group>()
        repeat(w.initialColdGroups) { stack.add(newGroup()) }

        val jobs = ArrayList<Job>()
        val latencies = ArrayList<Double>()
        var ramSum = 0.0
        var ramSamples = 0
        var ioBusy = 0.0

        fun held(): Pair<Long, Long> {
            var warm = 0L
            var hot = 0L
            for (g in stack) {
                if (g.nativeHot) continue
                if (g.warmAt != null) warm += g.coldBytes
                if (g.bitmapAt != null) hot += g.raw
            }
            return warm to hot
        }

        fun isWarm(g: Group, now: Double) = g.nativeHot || (g.warmAt != null && g.warmAt!! <= now)
        fun isBitmap(g: Group, now: Double) = g.nativeHot || (g.bitmapAt != null && g.bitmapAt!! <= now)

        // feed the model what finished by now (like Prefetcher.recordFetch / recordDecode)
        fun reportFinished(now: Double) {
            for (j in jobs) {
                if (j.end > now) continue
                if (j.io && !j.group.warmReported) {
                    j.group.warmReported = true
                    model.recordFetch(j.group.coldBytes, ((j.end - j.start) * 1_000_000).toLong())
                } else if (!j.io && !j.group.bitmapReported) {
                    j.group.bitmapReported = true
                    model.recordDecode(j.group.raw, ((j.end - j.start) * 1_000_000).toLong())
                }
            }
        }

        fun schedule(targets: List<Stage>, upcoming: List<Group>, now: Double) {
            // drop work that has not started; keep what is running
            val it = jobs.iterator()
            while (it.hasNext()) {
                val j = it.next()
                if (j.start > now) {
                    if (j.io) j.group.warmAt = null else j.group.bitmapAt = null
                    it.remove()
                }
            }
            var ioFree = max(now, jobs.filter { it.io }.maxOfOrNull { it.end } ?: now)
            var cpuFree = max(now, jobs.filter { !it.io }.maxOfOrNull { it.end } ?: now)
            for ((i, target) in targets.withIndex()) {
                val g = upcoming[i]
                if (g.nativeHot) continue
                if (target.ordinal >= Stage.WARM.ordinal && g.warmAt == null) {
                    val d = w.hw.read(g.coldBytes, ioFree)
                    jobs.add(Job(g, ioFree, ioFree + d, io = true))
                    g.warmAt = ioFree + d
                    g.warmReported = false
                    ioBusy += d
                    ioFree += d
                }
                if (target == Stage.BITMAP && g.bitmapAt == null) {
                    val start = max(cpuFree, g.warmAt ?: now)
                    val d = w.hw.decode(g.raw)
                    jobs.add(Job(g, start, start + d, io = false))
                    g.bitmapAt = start + d
                    g.bitmapReported = false
                    cpuFree = start + d
                }
            }
        }

        fun describe(g: Group, now: Double): GroupInput = when {
            g.nativeHot || isBitmap(g, now) -> GroupInput(Stage.BITMAP, 0, 0)
            isWarm(g, now) -> GroupInput(Stage.WARM, 0, g.raw)
            else -> GroupInput(Stage.COLD, g.coldBytes, g.raw)
        }

        fun doUndo() {
            val g = stack.last()
            val arrival = t
            reportFinished(arrival)

            val (warm0, hot0) = held()
            ramSum += (warm0 + hot0) / 1024.0
            ramSamples++

            model.recordUndo()
            // wait for / perform whatever is not ready
            var wait = 0.0
            if (!isBitmap(g, arrival)) {
                val readRemaining = when {
                    isWarm(g, arrival) -> 0.0
                    g.warmAt != null -> min(g.warmAt!! - arrival, w.hw.read(g.coldBytes, arrival))
                    else -> {
                        val d = w.hw.read(g.coldBytes, arrival)
                        model.recordFetch(g.coldBytes, (d * 1_000_000).toLong())
                        ioBusy += d
                        d
                    }
                }
                val decodeRemaining = if (g.bitmapAt != null) {
                    min(max(0.0, g.bitmapAt!! - arrival), readRemaining + w.hw.decode(g.raw))
                } else {
                    val d = w.hw.decode(g.raw)
                    model.recordDecode(g.raw, (d * 1_000_000).toLong())
                    readRemaining + d
                }
                wait = decodeRemaining
            }
            val applyMs = w.hw.apply(g.raw)
            model.recordApply(g.raw, (applyMs * 1_000_000).toLong())
            val latency = wait + applyMs
            latencies.add(latency)
            t = arrival + latency

            stack.removeAt(stack.size - 1)
            // forget bookkeeping of the consumed group
            jobs.removeAll { it.group === g }

            // plan the next steps
            val upcoming = stack.asReversed().take(60)
            when (policy) {
                Policy.NONE -> {}
                Policy.FIXED5 -> {
                    var n = 0
                    val targets = ArrayList<Stage>()
                    for (u in upcoming) {
                        if (!u.nativeHot && !isWarm(u, t) && u.warmAt == null && n < 5) { targets.add(Stage.WARM); n++ }
                        else targets.add(Stage.COLD)
                    }
                    schedule(targets, upcoming, t)
                }
                Policy.BRAIN -> {
                    val (warm, hot) = held()
                    val seq = upcoming.asSequence().map { describe(it, t) }
                    val targets = model.plan(seq, max(0L, w.ramBudget - warm - hot))
                    schedule(targets, upcoming, t)
                }
            }
        }

        fun draw() {
            val g = newGroup()
            g.nativeHot = true
            stack.add(g)
            // groups older than the 4 most recent fall to Cold
            val idx = stack.size - 5
            if (idx >= 0) stack[idx].nativeHot = false
            model.recordCommit(true, g.raw)
        }

        repeat(w.sessions) {
            val strokes = 1 + rnd.nextInt(7)
            repeat(strokes) { t += 350.0; draw() }
            t += 500.0
            val undos = if (w.undoWhatYouDrew) strokes else 1 + rnd.nextInt(7)
            repeat(undos) {
                if (stack.isNotEmpty()) {
                    doUndo()
                    t += w.gapMs
                }
            }
            model.recordRunBreak()
        }
        // finally a long dive into the old (Cold) history
        repeat(min(40, stack.size)) {
            doUndo()
            t += w.gapMs
        }

        latencies.sort()
        val p95 = latencies[((latencies.size - 1) * 0.95).toInt()]
        return Result(policy.name, latencies.sum(), p95, ramSum / max(1, ramSamples), ioBusy)
    }

    private fun compare(title: String, w: Workload): Map<Policy, Result> {
        val r = Policy.values().associateWith { simulate(it, w) }
        println("=== $title")
        println(String.format("%-8s %12s %10s %12s %10s", "policy", "total ms", "p95 ms", "avg RAM KiB", "IO ms"))
        for (p in Policy.values()) {
            val x = r[p]!!
            println(String.format("%-8s %12.0f %10.1f %12.0f %10.0f", x.name, x.totalLatencyMs, x.p95Ms, x.avgRamKb, x.ioBusyMs))
        }
        return r
    }

    @Test
    fun slowUser_brainHidesDecodeAndUsesLessRamThanFixed5() {
        val r = compare("slow user (300 ms between undos)", Workload(150, 12, gapMs = 300.0, hw = Hardware()))
        val none = r[Policy.NONE]!!; val k5 = r[Policy.FIXED5]!!; val brain = r[Policy.BRAIN]!!
        assertTrue("brain ${brain.totalLatencyMs} vs none ${none.totalLatencyMs}", brain.totalLatencyMs < none.totalLatencyMs * 0.6)
        assertTrue("brain ${brain.totalLatencyMs} vs k5 ${k5.totalLatencyMs}", brain.totalLatencyMs <= k5.totalLatencyMs)
        assertTrue("brain ram ${brain.avgRamKb} vs k5 ${k5.avgRamKb}", brain.avgRamKb <= k5.avgRamKb)
    }

    @Test
    fun fastUser_brainIsNotWorseThanFixed5() {
        val r = compare("fast user (30 ms between undos)", Workload(150, 12, gapMs = 30.0, hw = Hardware()))
        val none = r[Policy.NONE]!!; val k5 = r[Policy.FIXED5]!!; val brain = r[Policy.BRAIN]!!
        assertTrue("brain ${brain.totalLatencyMs} vs none ${none.totalLatencyMs}", brain.totalLatencyMs < none.totalLatencyMs)
        assertTrue("brain ${brain.totalLatencyMs} vs k5 ${k5.totalLatencyMs}", brain.totalLatencyMs <= k5.totalLatencyMs * 1.05)
    }

    @Test
    fun diskSuddenlySlower_brainAdapts() {
        val hw = Hardware(readSlowdown = { t -> if (t > 12_000.0) 8.0 else 1.0 })
        val r = compare("disk 8x slower after 12 s", Workload(150, 12, gapMs = 120.0, hw = hw))
        val none = r[Policy.NONE]!!; val k5 = r[Policy.FIXED5]!!; val brain = r[Policy.BRAIN]!!
        assertTrue("brain ${brain.totalLatencyMs} vs none ${none.totalLatencyMs}", brain.totalLatencyMs < none.totalLatencyMs)
        assertTrue("brain ${brain.totalLatencyMs} vs k5 ${k5.totalLatencyMs}", brain.totalLatencyMs <= k5.totalLatencyMs * 1.05)
    }

    @Test
    fun tightRam_brainSpendsItWhereItPays() {
        val r = compare(
            "tight RAM, users undo what they drew",
            Workload(150, 14, gapMs = 100.0, hw = Hardware(), ramBudget = 1_000_000)
        )
        val k5 = r[Policy.FIXED5]!!; val brain = r[Policy.BRAIN]!!
        assertTrue("brain ram ${brain.avgRamKb} vs k5 ${k5.avgRamKb}", brain.avgRamKb <= k5.avgRamKb)
        assertTrue("brain ${brain.totalLatencyMs} vs k5 ${k5.totalLatencyMs}", brain.totalLatencyMs <= k5.totalLatencyMs * 1.1)
    }
}
