package com.example.canvas.history

import android.content.ComponentCallbacks2
import com.example.canvas.core.BufferPool
import com.example.canvas.core.CodecSelector
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Victim selection strategy for demoting patches under memory pressure,
 * inspired by SWAM (arXiv:2306.08345).
 */
enum class SwamEvictionStrategy(val title: String, val subtitle: String) {
    FIFO(
        "FIFO (Theo thứ tự thời gian)",
        "Hạ tầng patch cũ nhất trước (Chronological queue)"
    ),
    SWAM_COST_AWARE(
        "SWAM EOOM (Cost-Aware)",
        "Ưu tiên hạ tầng patch tốn nhiều RAM / tỷ lệ độ tuổi × kích thước lớn (arXiv:2306.08345)"
    )
}

/** What to drop when Cold is full and compressing more is not enough. Chosen by the user. */
enum class ColdFullPolicy(val title: String, val subtitle: String) {
    SMART(
        "Thông minh",
        "Bỏ nét có giá trị kỳ vọng thấp nhất trên mỗi byte (xác suất còn undo tới đó, học từ thói quen vẽ)"
    ),
    OLDEST_FIRST("Cũ nhất trước", "Bỏ nét cũ nhất (FIFO)"),
    LARGEST_FIRST("Nặng nhất trước", "Bỏ nét tốn nhiều đĩa nhất để mất ít bước undo nhất"),
    KEEP_ALL("Không xóa", "Không bao giờ xóa lịch sử: tạm giữ trong RAM (Warm) khi Cold không nhận thêm")
}

data class TierConfig(
    val hotBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val warmBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val coldMaxBytes: Long = 500L * 1024 * 1024,
    val coldMaxSteps: Int = 1000,
    val compressionLevel: Int = 1,
    val adaptiveCompEnabled: Boolean = true,
    val coldCompressionLevel: Int = 6,
    val adaptiveCompBlockSize: Int = 4,
    val infiniteUndo: Boolean = false,
    val evictionStrategy: SwamEvictionStrategy = SwamEvictionStrategy.FIFO,
    val coldFullPolicy: ColdFullPolicy = ColdFullPolicy.SMART,
    /** Derive Hot/Warm budgets from the user's own habits instead of the fixed values above. */
    val autoBudgets: Boolean = true,
    /**
     * Tier boundary guard: a patch that comes back by undo/redo keeps its compressed form instead of being
     * compressed again, so data sitting on the Warm/Cold boundary does not cost CPU every time it crosses.
     */
    val reuseEncodings: Boolean = true,
    /**
     * Moving a step down a tier (compressing it, writing it to disk) happens in the background, so that drawing
     * never waits for it. Only when Hot has run a whole budget over does the next stroke have to wait.
     */
    val asyncDemotion: Boolean = true,
    /**
     * Which step leaves Warm for Cold is chosen by what keeping it is worth per byte of RAM (see
     * [TierPolicy.pickWarmVictim]) instead of by age.
     */
    val valueAwareVictims: Boolean = false,
    /**
     * Hot -> Warm picks how each patch is stored from what is in it (single colour, runs of equal pixels, smooth
     * gradients, or plain Deflate), learned per kind of patch (see [CodecSelector]) instead of always Deflate.
     */
    val contentAwareCodec: Boolean = true,
    /**
     * Proactive high-watermark ratio (default 80% instead of 100%): Hot and Warm start pushing older/larger
     * patches down as soon as usage exceeds 80% of budget, keeping 20% headroom ready for upcoming strokes.
     */
    val demotionWatermark: Double = 0.80,
    /**
     * Oversized stroke/patch ratio: if a single stroke group or patch in Hot exceeds this share of Hot budget,
     * it begins progressively demoting to Warm even before total usage crosses the watermark.
     */
    val largeStrokeRatio: Double = 0.45,
    /**
     * Time-based thermal decay: when the user pauses drawing, patches gradually cool down (Hot -> Warm -> Cold)
     * step by step so idle sessions settle down to near-zero RAM.
     */
    val idleCoolingEnabled: Boolean = false,
    val hotCoolAfterMs: Long = 3_500L,
    val warmCoolAfterMs: Long = 8_000L,
    val coolStepIntervalMs: Long = 1_500L
)

/**
 * Manages tiered degradation policies across Hot, Warm, and Cold stores,
 * plus SWAM OOM Cleaner deduplication and EOOM Cost-Aware victim selection.
 * Guarantees zero data loss during transitions: old representations are kept until
 * new representations are completely finalized.
 */
class TierPolicy(
    var config: TierConfig,
    private val hotStore: HotStore,
    private val warmStore: WarmStore,
    private val coldStore: ColdStore,
    var onColdEvicted: ((Patch) -> Unit)? = null,
    private val costModel: TierCostModel? = null,
    /** Free bytes of the volume holding the Cold files; default asks the file system. */
    private val diskFreeBytes: (() -> Long)? = null,
    /** Bytes that must stay free for the user's own next strokes (learned from their drawing). */
    private val reserveBytes: () -> Long = { 0L },
    /** Called after old undo steps had to be dropped: number of strokes removed. */
    var onTrimmed: ((evictedStrokes: Int) -> Unit)? = null,
    /** patch id -> number of strokes above it in the undo history (how deep it is). */
    private val undoDepths: () -> Map<Long, Int> = { emptyMap() },
    /** Probability that the user still undoes down to a given depth (learned from their habits). */
    private val reachFunction: () -> ((Int) -> Double) = { { 1.0 } },
    /** Called when Cold cannot take more and the policy forbids dropping history. */
    var onColdBlocked: (() -> Unit)? = null,
    /** (hot, warm) budgets computed from the user's habits, or null while there is not enough data. */
    private val autoBudgets: () -> Pair<Long, Long>? = { null },
    /** Receives every tier transition (the tracker panel); null = nobody is watching. */
    private val events: TierEventLog? = null,
    /** Measured fixed cost of one more file (seek, open, headers) in bytes-equivalent; 0 while unknown. */
    private val patchOverheadBytes: () -> Long = { 0L },
    /** Injectable wall-clock (ms) for deterministic time-based cooling tests. */
    private val clockMs: () -> Long = System::currentTimeMillis,
    /** Learned idle pause (ms) before Hot starts cooling, derived from the user's real stroke/undo rhythm. */
    private val learnedHotCoolMs: () -> Long? = { null }
) {
    private var blockedNotified = false

    /** Serializes tier-demotion and cooling passes across worker suspension points so two coroutines never demote the same patch. */
    private val transitionMutex = Mutex()

    /** Chooses the Cold compression level patch by patch from measurements. */
    val tuner = CompressionTuner()

    /** Chooses the Warm compression level by measuring it on this device with this user's pixels. */
    val warmLevels = WarmLevelCalibrator()

    /** Chooses the storage format (solid / run-length / delta / Deflate) of each patch entering Warm. */
    val codecs = CodecSelector()

    private fun baselineOf(patch: Patch): Int = if (patch.warmLevel > 0) patch.warmLevel else config.compressionLevel

    /** Budgets actually in force: learned ones when enabled and available, else the configured ones. */
    fun hotBudget(): Long =
        (if (config.autoBudgets) autoBudgets()?.first else null) ?: config.hotBudgetBytes

    fun warmBudget(): Long =
        (if (config.autoBudgets) autoBudgets()?.second else null) ?: config.warmBudgetBytes

    /** High-watermark byte ceilings (default 80% of budget) above which patches start pushing down proactively. */
    fun hotWatermarkBytes(): Long =
        (hotBudget().toDouble() * config.demotionWatermark.coerceIn(0.10, 1.0)).toLong()

    fun warmWatermarkBytes(): Long =
        (warmBudget().toDouble() * config.demotionWatermark.coerceIn(0.10, 1.0)).toLong()

    /**
     * Bumped when the history is cleared. A move that was still in flight (its compression or file write running)
     * sees the change when it resumes and throws its result away instead of putting a stale step back.
     */
    @Volatile
    var epoch: Int = 0

    var oomCleanerReclaimedBytes: Long = 0L
        private set

    var deduplicatedPatches: Int = 0
        private set

    /** Hot -> Warm moves that needed no compression because the patch brought its encoding back with it. */
    var reusedEncodings: Int = 0
        private set
    var reusedRawBytes: Long = 0L
        private set

    /** Warm -> Cold moves that found the patch's file still on disk and wrote nothing. */
    var reusedColdFiles: Int = 0
        private set

    var adaptiveCompSavedBytes: Long = 0L
        private set

    /** Cold pressure telemetry: how often it happened, what was saved without data loss, what was dropped. */
    var coldPressureEvents: Int = 0
        private set
    var coldRescuedBytes: Long = 0L
        private set
    var evictedStrokes: Int = 0
        private set

    fun resetSwamMetrics() {
        oomCleanerReclaimedBytes = 0L
        deduplicatedPatches = 0
        reusedEncodings = 0
        reusedRawBytes = 0L
        reusedColdFiles = 0
        adaptiveCompSavedBytes = 0L
        coldPressureEvents = 0
        coldRescuedBytes = 0L
        evictedStrokes = 0
        codecs.reset()
    }

    /**
     * Selects the next victim patch in HotStore according to [config.evictionStrategy].
     * - FIFO: picks oldest patch.
     * - SWAM_COST_AWARE: estimates eviction benefit = sizeBytes * ageRank * thermalAgeBoost so large
     *   and older cooling patches are compressed first while keeping tiny recent patches hot for O(1) undo.
     */
    private fun selectHotVictim(): Patch? {
        if (config.evictionStrategy == SwamEvictionStrategy.FIFO) {
            return hotStore.peekOldest()
        }
        val allHot = hotStore.getAll()
        if (allHot.isEmpty()) return null
        val now = clockMs()
        val total = allHot.size
        var bestPatch: Patch = allHot[0]
        var bestScore = -1.0
        for (i in allHot.indices) {
            val candidate = allHot[i]
            val ageWeight = (total - i).toDouble()
            val ageSeconds = ((now - candidate.createdAtMs).coerceAtLeast(0L)) / 1000.0
            val thermalBoost = 1.0 + (ageSeconds * 0.5)
            val score = candidate.sizeBytes.coerceAtLeast(1024L).toDouble() * ageWeight * thermalBoost
            if (score > bestScore) {
                bestScore = score
                bestPatch = candidate
            }
        }
        return bestPatch
    }

    /**
     * Enforces memory budgets for Hot, Warm, and Cold stores, demoting items
     * asynchronously from Hot -> Warm and Warm -> Cold as soon as usage exceeds the
     * 80% watermark ([hotWatermarkBytes], [warmWatermarkBytes]), and progressively offloading
     * oversized strokes/patches even below 80%.
     */
    suspend fun enforceBudgets(
        effectiveHotBudget: Long = hotWatermarkBytes(),
        effectiveWarmBudget: Long = warmWatermarkBytes(),
        checkOversized: Boolean = true
    ) = transitionMutex.withLock {
        // 1a. Enforce Hot high-watermark (default 80%) -> demote excess to Warm progressively
        val maxHotAttempts = hotStore.count
        var hotAttempts = 0
        while (hotStore.totalBytes > effectiveHotBudget && hotStore.count > 0 && hotAttempts < maxHotAttempts) {
            val victimHot = selectHotVictim() ?: break
            demoteHotToWarm(victimHot)
            hotAttempts++
        }

        // 1b. Oversized stroke/patch offloading ("nếu lớn quá thì đẩy dần xuống"):
        // Even if total Hot is still under 80%, a single giant patch or heavy multi-tile stroke
        // exceeding largeStrokeRatio of Hot budget is progressively pushed down to Warm.
        val fullHotBudget = hotBudget()
        if (checkOversized && fullHotBudget > 0L && hotStore.count > 0) {
            val largeThreshold = (fullHotBudget * config.largeStrokeRatio.coerceIn(0.15, 1.0)).toLong()
            if (largeThreshold > 0L) {
                val snapshot = hotStore.getAll()
                val byStroke = HashMap<Long, Long>()
                var anon = -1L
                for (p in snapshot) {
                    val key = if (p.groupId != 0L) p.groupId else anon--
                    byStroke[key] = (byStroke[key] ?: 0L) + p.sizeBytes
                }
                for (p in snapshot) {
                    if (p.state != PatchState.HOT || !hotStore.contains(p)) continue
                    val groupBytes = if (p.groupId != 0L) (byStroke[p.groupId] ?: p.sizeBytes) else p.sizeBytes
                    if (p.sizeBytes >= largeThreshold || groupBytes >= largeThreshold) {
                        demoteHotToWarm(p, noteOverride = "nét lớn, đẩy dần")
                    }
                }
            }
        }

        // 2. Enforce Warm high-watermark (default 80%) -> demote excess to Cold progressively
        val maxWarmAttempts = warmStore.count
        var warmAttempts = 0
        while (warmStore.totalBytes > effectiveWarmBudget && warmStore.count > 0 && warmAttempts < maxWarmAttempts) {
            val victim = pickWarmVictim() ?: break
            if (!demoteWarmToCold(victim)) break // Cold cannot take more: keep it in Warm
            warmAttempts++
        }

        // 3. Enforce the user's own Cold limits (not used when Infinite Undo is on: then only the disk limits)
        if (!config.infiniteUndo) {
            var guard = coldStore.count + 1
            while ((coldStore.totalBytes > config.coldMaxBytes || coldStore.count > config.coldMaxSteps) &&
                coldStore.count > 0 && guard-- > 0
            ) {
                val bytesOver = (coldStore.totalBytes - config.coldMaxBytes).coerceAtLeast(0L)
                val before = coldStore.count
                relieveCold(needBytes = -bytesOver, extraSlots = 0)
                if (coldStore.count == before && coldStore.totalBytes > config.coldMaxBytes) break
            }
        }
    }

    /**
     * Effective cooling timings (hotDelayMs, warmDelayMs, stepIntervalMs) combining:
     * 1. User rhythm learned by [PrefetchModel.learnedHotCoolDelayMs] (inter-stroke pause & undo decision window),
     * 2. Live memory pressure acceleration: when Hot or Warm is heavily loaded (>50% of watermark) or device free
     *    RAM is tight, cooling starts up to 2.5x sooner and steps faster.
     */
    fun effectiveCoolTimings(): Triple<Long, Long, Long> {
        val baseHot = learnedHotCoolMs() ?: config.hotCoolAfterMs
        val baseWarm = maxOf(config.warmCoolAfterMs, (baseHot * 22L) / 10L)
        val baseStep = config.coolStepIntervalMs

        val hotCap = hotWatermarkBytes().coerceAtLeast(1L)
        val warmCap = warmWatermarkBytes().coerceAtLeast(1L)
        val hotLoad = (hotStore.totalBytes.toDouble() / hotCap).coerceIn(0.0, 1.0)
        val warmLoad = (warmStore.totalBytes.toDouble() / warmCap).coerceIn(0.0, 1.0)
        val load = maxOf(hotLoad, warmLoad)
        // High load accelerates cooling down to 40% of base delay; low load stays gentle (100%)
        val accel = (1.0 - 0.60 * load).coerceIn(0.40, 1.0)

        val effHot = (baseHot * accel).toLong().coerceAtLeast(600L)
        val effWarm = (baseWarm * accel).toLong().coerceAtLeast(effHot + 800L)
        val effStep = (baseStep * accel).toLong().coerceAtLeast(350L)
        return Triple(effHot, effWarm, effStep)
    }

    /**
     * Picks the coldest stroke in Hot during idle cooling using a thermal value-density score:
     *
     *     Temperature(p) = P(reach >= depth) / (sizeBytes * (1 + ageSeconds))
     *
     * The stroke with the lowest temperature (deepest in undo stack, lowest undo probability, largest RAM
     * footprint, and oldest elapsed time) cools into Warm first, keeping the newest lightweight top stroke
     * hot until the very last cooling step.
     */
    private fun selectColdestHotVictim(): Patch? {
        val allHot = hotStore.getAll()
        if (allHot.isEmpty()) return null
        if (allHot.size == 1) return allHot[0]
        val now = clockMs()
        val depths = undoDepths()
        val reach = reachFunction()
        var coldest = allHot[0]
        var lowestTemp = Double.MAX_VALUE
        for (i in allHot.indices) {
            val p = allHot[i]
            val depth = depths[p.id] ?: (allHot.size - 1 - i)
            val prob = reach(depth).coerceAtLeast(1e-4)
            val ageSec = ((now - p.createdAtMs).coerceAtLeast(0L)) / 1000.0
            val sizeKb = (p.sizeBytes.coerceAtLeast(1024L)) / 1024.0
            val temp = prob / (sizeKb * (1.0 + ageSec * 0.8))
            if (temp < lowestTemp) {
                lowestTemp = temp
                coldest = p
            }
        }
        return coldest
    }

    /**
     * Performs one adaptive time-based thermal cooling step when the user has been idle for [idleMs].
     * - Once `idleMs >= effHotDelay`, immediately cools 1 coldest stroke group per tick from Hot -> Warm
     *   (selected by [selectColdestHotVictim] using undo reach probability, byte size, and age).
     * - Once `idleMs >= effWarmDelay`, cools 1 lowest-value stroke group per tick from Warm -> Cold.
     */
    suspend fun coolStep(idleMs: Long): Boolean = transitionMutex.withLock {
        val (effHotDelay, effWarmDelay, _) = effectiveCoolTimings()
        val idleSec = (idleMs / 1000L).coerceAtLeast(1L)

        if (idleMs >= effHotDelay && hotStore.count > 0) {
            val victim = selectColdestHotVictim()
            if (victim != null) {
                val siblings = if (victim.groupId != 0L) {
                    hotStore.getAll().filter { it.groupId == victim.groupId }
                } else {
                    listOf(victim)
                }
                for (p in siblings) {
                    if (p.state == PatchState.HOT && hotStore.contains(p)) {
                        demoteHotToWarm(p, noteOverride = "nguội dần (${idleSec}s)")
                    }
                }
            }
        }

        var coldBlockedNow = false
        if (idleMs >= effWarmDelay && warmStore.count > 0) {
            val victim = pickWarmVictim()
            if (victim != null) {
                val siblings = if (victim.groupId != 0L) {
                    warmStore.getAll().filter { it.groupId == victim.groupId }
                } else {
                    listOf(victim)
                }
                for (p in siblings) {
                    if (p.state == PatchState.WARM && warmStore.contains(p)) {
                        if (!demoteWarmToCold(p, noteOverride = "nguội dần (${idleSec}s)")) {
                            coldBlockedNow = true
                            break
                        }
                    }
                }
            }
        }

        hotStore.count > 0 || (warmStore.count > 0 && !coldBlockedNow)
    }

    /**
     * Which Warm step goes down to Cold. The team's own rule, not an age rule:
     *
     * Keeping step p in Warm instead of Cold is worth `reach(d)` (the chance that undo gets down to its depth d,
     * learned from this user's habits) times the time an undo saves by not reading it from disk. A read costs a
     * fixed part plus a part per byte, so it is proportional to `S + O` (S = its size, O = the measured fixed cost
     * expressed in bytes). It uses `S` bytes of RAM. Sending it down also costs one write of the same shape.
     * What is lost per byte of RAM freed is therefore
     *
     *     (reach(d) * readRate + writeRate) * (S + O) / S
     *
     * and the step with the smallest value goes. With a flat cost per byte this is the oldest step; the fixed part
     * is what changes the order: many small steps are expensive to bring back one by one for the little RAM they
     * hold, so they stay in Warm while big, deep steps (cheap per byte, unlikely to be undone) go to disk.
     * Ties keep the older step first.
     */
    fun pickWarmVictim(): Patch? {
        if (!config.valueAwareVictims) return warmStore.peekOldest()
        val all = warmStore.getAll()
        if (all.isEmpty()) return null
        if (all.size == 1) return all[0]
        val depths = undoDepths()
        val reach = reachFunction()
        val overhead = patchOverheadBytes().coerceAtLeast(0L).toDouble()
        val readRate = costModel?.readNsPerByte()?.takeIf { it > 0.0 }
        val writeRate = costModel?.writeNsPerByte()?.takeIf { it > 0.0 }
        // until both were measured, treat a read and a write as equally expensive
        val rd = readRate ?: writeRate ?: 1.0
        val wr = writeRate ?: readRate ?: 1.0
        var best = all[0]
        var bestScore = Double.MAX_VALUE
        for (p in all) {
            val depth = depths[p.id] ?: Int.MAX_VALUE / 2     // not in the undo history: nothing will ask for it
            val size = p.sizeBytes.coerceAtLeast(1L).toDouble()
            val score = (reach(depth) * rd + wr) * (size + overhead) / size
            if (score < bestScore - 1e-12) {
                bestScore = score
                best = p
            }
        }
        return best
    }

    // ------------------------------------------------------------------------------------------
    // Cold pressure: how much room is left, and what to do when there is not enough
    // ------------------------------------------------------------------------------------------

    private fun defaultDiskFree(): Long {
        var dir: File? = coldStore.historyDir
        while (dir != null && !dir.exists()) dir = dir.parentFile
        return dir?.usableSpace ?: Long.MAX_VALUE
    }

    /** Free bytes of the volume holding the Cold files. */
    fun diskFree(): Long = diskFreeBytes?.invoke() ?: defaultDiskFree()

    /** Bytes Cold can still take: the real free disk minus the user's reserve and, if set, the configured cap. */
    fun coldFreeBytes(): Long {
        val disk = diskFree() - reserveBytes()
        val cap = if (config.infiniteUndo) Long.MAX_VALUE else config.coldMaxBytes - coldStore.totalBytes
        return minOf(disk, cap)
    }

    private fun stepsFull(extraSlots: Int): Boolean =
        !config.infiniteUndo && coldStore.count + extraSlots > config.coldMaxSteps

    /** Makes room for [needBytes] in Cold if possible (see [relieveCold]); false if it cannot be done. */
    suspend fun ensureColdSpace(needBytes: Long): Boolean {
        if (coldFreeBytes() >= needBytes && !stepsFull(1)) return true
        relieveCold(needBytes, extraSlots = 1)
        return coldFreeBytes() >= needBytes && !stepsFull(1)
    }

    /**
     * Frees Cold space in order of increasing cost, stopping as soon as there is enough:
     *  1. deduplicate identical files (no data lost),
     *  2. recompress older files at the high level, best saving per unit of work first (no data lost),
     *  3. only then drop whole strokes, oldest first - and never when even dropping all history would not help
     *     (for example another app filled the disk).
     */
    suspend fun relieveCold(needBytes: Long, extraSlots: Int) {
        fun satisfied() = coldFreeBytes() >= needBytes && !stepsFull(extraSlots)
        if (satisfied()) return
        coldPressureEvents++

        val before = coldStore.totalBytes
        runOomCleaner()

        if (!satisfied() && config.adaptiveCompEnabled) {
            for (candidate in rescueCandidates()) {
                if (satisfied()) break
                recompressColdPatch(candidate)
            }
        }
        coldRescuedBytes += (before - coldStore.totalBytes).coerceAtLeast(0L)

        var evictedNow = 0
        while (!satisfied() && coldStore.count > 0) {
            val shortfall = needBytes - coldFreeBytes()
            if (!stepsFull(extraSlots) && shortfall > coldStore.totalBytes) break // dropping history cannot fix it
            val freeBefore = coldFreeBytes()
            if (evictOldestStroke() == 0) break
            evictedNow++
            // If freeing our own file did not give the space back, the disk is being eaten by someone else:
            // stop after this one rather than throwing the whole history away for nothing.
            if (!satisfied() && !stepsFull(extraSlots) && coldFreeBytes() <= freeBefore) break
        }
        if (evictedNow > 0) onTrimmed?.invoke(evictedNow)
    }

    private fun rescueCandidates(): List<Patch> =
        coldStore.getAll()
            .filter { !it.deepCompressed && it.filePath != null && it.rawLength > 0 && !coldStore.isShared(it) }
            .sortedByDescending { costModel?.rescueScore(it.sizeBytes, it.rawLength.toLong()) ?: it.sizeBytes.toDouble() }

    private suspend fun recompressColdPatch(patch: Patch) {
        withContext(Dispatchers.IO) {
            try {
                val t0 = System.nanoTime()
                val payload = coldStore.readPayload(patch)
                if (!PatchCodec.isDeflate(payload.compressedBytes)) {
                    patch.deepCompressed = true      // nothing deeper to try; do not pick it again
                    return@withContext
                }
                val raw = PatchCodec.decompress(payload.compressedBytes, payload.rawLength, payload.crc32)
                val cls = tuner.classOf(payload.rawLength, payload.compressedBytes.size)
                val level = tuner.mostCompressingLevel(cls, baselineOf(patch), config.coldCompressionLevel)
                val out = PatchCodec.compress(raw, level)
                costModel?.recordRecompress(
                    rawBytes = payload.rawLength.toLong(),
                    warmBytes = payload.compressedBytes.size.toLong(),
                    coldBytes = out.compressedBytes.size.toLong(),
                    ns = System.nanoTime() - t0
                )
                patch.deepCompressed = true
                if (out.compressedBytes.size < payload.compressedBytes.size) {
                    coldStore.rewrite(patch, out.compressedBytes)
                    events?.emit(
                        TierEventKind.SQUEEZE, patch.strokeNo, payload.compressedBytes.size.toLong(),
                        out.compressedBytes.size.toLong(), "mức $level"
                    )
                }
            } catch (_: Throwable) {
                // keep the file as it is; try the next candidate
            }
        }
    }

    /** Which stroke to drop first according to [TierConfig.coldFullPolicy]; null = drop nothing. */
    private fun pickVictim(): Patch? {
        val cold = coldStore.getAll()
        if (cold.isEmpty()) return null
        return when (config.coldFullPolicy) {
            ColdFullPolicy.KEEP_ALL -> null
            ColdFullPolicy.OLDEST_FIRST -> cold.first()
            ColdFullPolicy.LARGEST_FIRST ->
                strokesOf(cold).maxByOrNull { (_, tiles) -> tiles.sumOf { it.sizeBytes } }?.value?.first()
            ColdFullPolicy.SMART -> {
                // keep what is most likely to be undone per byte it costs; drop the worst ratio first
                val depths = undoDepths()
                val reach = reachFunction()
                strokesOf(cold).minWithOrNull(
                    compareBy<Map.Entry<Long, List<Patch>>>(
                        { (_, tiles) ->
                            val depth = tiles.maxOf { depths[it.id] ?: 0 } + 1
                            reach(depth) / tiles.sumOf { it.sizeBytes }.coerceAtLeast(1L).toDouble()
                        }
                    ).thenByDescending { (_, tiles) -> tiles.maxOf { depths[it.id] ?: 0 } }
                )?.value?.first()
            }
        }
    }

    private fun strokesOf(cold: List<Patch>): Map<Long, List<Patch>> {
        var anon = -1L
        return cold.groupBy { if (it.groupId != 0L) it.groupId else anon-- }
    }

    /** Drops one stroke entirely (all of its tiles, wherever they are) so no half-undoable stroke remains. */
    private fun evictOldestStroke(): Int {
        val oldest = pickVictim() ?: return 0
        val victims: List<Patch> = if (oldest.groupId == 0L) {
            listOf(oldest)
        } else {
            (coldStore.getAll() + warmStore.getAll() + hotStore.getAll())
                .filter { it.groupId == oldest.groupId }
                .distinct()
        }
        val victimBytes = victims.sumOf { it.sizeBytes }
        val victimStroke = victims.firstOrNull()?.strokeNo ?: 0
        for (v in victims) {
            coldStore.remove(v)
            warmStore.remove(v)
            hotStore.remove(v)
            v.recycle()
            onColdEvicted?.invoke(v)
        }
        events?.emit(TierEventKind.EVICT, victimStroke, victimBytes, 0L, "đĩa đầy: ${config.coldFullPolicy.title}")
        evictedStrokes++
        return victims.size
    }

    /**
     * SWAM OOM Cleaner (arXiv:2306.08345 Section 3.2):
     * Reclaims duplicate/shared pages in Warm (zRAM) and Cold (Swap) stores using
     * CRC32 + rawLength content-addressable deduplication.
     *
     * @return Total bytes reclaimed in this pass.
     */
    fun runOomCleaner(): Long {
        var reclaimedThisPass = 0L

        // 1. Deduplicate Warm store compressed byte arrays
        val canonicalWarm = HashMap<String, ByteArray>()
        for (patch in warmStore.getAll()) {
            val bytes = patch.compressedBytes ?: continue
            if (patch.crc32 == 0L || patch.rawLength <= 0) continue
            val key = "${patch.crc32}_${patch.rawLength}"
            val existing = canonicalWarm[key]
            if (existing != null && existing !== bytes && existing.size == bytes.size && existing.contentEquals(bytes)) {
                patch.compressedBytes = existing
                reclaimedThisPass += bytes.size.toLong()
                deduplicatedPatches++
            } else if (existing == null) {
                canonicalWarm[key] = bytes
            }
        }
        warmStore.recalculateTotalBytes()

        // 2. Deduplicate Cold store swap files on disk
        val canonicalCold = HashMap<String, String>()
        for (patch in coldStore.getAll()) {
            val path = patch.filePath ?: continue
            if (patch.crc32 == 0L || patch.rawLength <= 0) continue
            val key = "${patch.crc32}_${patch.rawLength}"
            val existingPath = canonicalCold[key]
            if (existingPath != null && existingPath != path && File(existingPath).exists()) {
                val oldFile = File(path)
                val oldLen = if (oldFile.exists()) oldFile.length() else patch.sizeBytes
                // Point patch to canonical swap file and delete redundant file
                patch.filePath = existingPath
                try {
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                } catch (_: Throwable) {}
                reclaimedThisPass += oldLen
                deduplicatedPatches++
            } else if (existingPath == null) {
                canonicalCold[key] = path
            }
        }
        coldStore.recalculateTotalBytes()

        oomCleanerReclaimedBytes += reclaimedThisPass
        return reclaimedThisPass
    }

    /**
     * Demotes a patch from HOT to WARM:
     * - State transitions: HOT -> COMPRESSING -> WARM.
     * - Bitmap remains accessible until compression completes.
     */
    suspend fun demoteHotToWarm(patch: Patch, noteOverride: String? = null) {
        if (patch.state != PatchState.HOT) return
        val epochAtStart = epoch
        val hotBytes = patch.sizeBytes
        patch.state = PatchState.COMPRESSING

        // Remove from HotStore using the existing Hot sizeBytes
        hotStore.remove(patch)

        val enc = patch.reuse
        patch.reuse = null
        if (enc != null && config.reuseEncodings) {
            // same pixels as before the undo: keep the bytes that were already made for them
            patch.warmLevel = enc.level
            patch.compressedBytes = enc.bytes
            patch.rawLength = enc.rawLength
            patch.crc32 = enc.crc32
            patch.sizeBytes = enc.bytes.size.toLong()
            patch.deepCompressed = enc.deep
            patch.coldTrips = enc.coldTrips
            patch.keptFile = enc.filePath
            reusedEncodings++
            reusedRawBytes += enc.rawLength.toLong()
        } else {
            // Copy pixels into a pooled Java byte array while holding patch's monitor BEFORE hopping threads,
            // so native Bitmap_copyPixelsToBuffer can NEVER touch a recycled Bitmap!
            val snapshot = patch.copyPixelsToBufferPool()
            if (snapshot != null) {
                val (rawBuf, byteCount) = snapshot
                try {
                    var usedLevel = config.compressionLevel
                    val t0 = System.nanoTime()
                    val compressed = withContext(Dispatchers.Default) {
                        if (config.adaptiveCompEnabled && warmLevels.needsCalibration()) {
                            val all = PatchCodec.compressRawAtLevels(rawBuf, byteCount, warmLevels.levels)
                            for ((level, r) in all) warmLevels.record(level, r.first.rawLength.toLong(), r.first.compressedBytes.size.toLong(), r.second)
                            warmLevels.patchDone()
                            usedLevel = warmLevels.best()
                            all.getValue(usedLevel).first
                        } else {
                            usedLevel = if (config.adaptiveCompEnabled) warmLevels.best() else config.compressionLevel
                            if (config.contentAwareCodec) {
                                val io = (costModel?.writeNsPerByte() ?: 0.0) + (costModel?.readNsPerByte() ?: 0.0)
                                PatchCodec.compressRawAdaptive(rawBuf, byteCount, usedLevel, codecs, if (io > 0.0) io else null)
                            } else {
                                PatchCodec.compress(rawBuf, usedLevel, byteCount)
                            }
                        }
                    }
                    costModel?.recordCompress(compressed.rawLength.toLong(), System.nanoTime() - t0)
                    patch.warmLevel = usedLevel
                    patch.compressedBytes = compressed.compressedBytes
                    patch.rawLength = compressed.rawLength
                    patch.crc32 = compressed.crc32
                    patch.sizeBytes = compressed.compressedBytes.size.toLong()
                    costModel?.recordWarmCompress(compressed.rawLength.toLong(), compressed.compressedBytes.size.toLong())
                } finally {
                    BufferPool.release(rawBuf)
                }
            } else if (patch.compressedBytes != null) {
                patch.sizeBytes = patch.compressedBytes!!.size.toLong()
            } else {
                // Patch was concurrently undone or cleared before compression started
                patch.recycle()
                return
            }
        }

        if (epoch != epochAtStart) {            // cleared while this was being compressed
            patch.recycle()
            return
        }
        patch.state = PatchState.WARM
        warmStore.add(patch)
        val levelNote = "mức ${patch.warmLevel.takeIf { it > 0 } ?: config.compressionLevel}"
        events?.emit(
            TierEventKind.HOT_TO_WARM, patch.strokeNo, hotBytes, patch.sizeBytes,
            if (noteOverride != null) "$levelNote · $noteOverride" else levelNote
        )

        // Now safe to recycle the bitmap
        patch.recycle()
    }

    /**
     * Demotes a patch from WARM to COLD:
     * - State transitions: WARM -> WRITING -> COLD.
     * - Ariadne AdaptiveComp (HPCA 2025, arXiv:2502.12826): Warm uses fast Level-1 compression for
     *   low-latency restore in RAM, while Cold re-compresses with higher density (e.g. Level 9)
     *   when [TierConfig.adaptiveCompEnabled] is active.
     * - Compressed bytes remain in RAM until atomic file write completes.
     */
    suspend fun demoteWarmToCold(patch: Patch, noteOverride: String? = null): Boolean {
        if (patch.state != PatchState.WARM) return false
        val epochAtStart = epoch
        val warmBytes = patch.compressedBytes ?: ByteArray(0)
        patch.state = PatchState.WRITING

        // Remove from WarmStore using existing Warm sizeBytes
        warmStore.remove(patch)

        // Which level, if any, pays off for THIS patch (see CompressionTuner). When space is short, squeeze hardest.
        val tight = coldFreeBytes() < warmBytes.size.toLong()
        var deep = false
        val baseline = baselineOf(patch)
        var usedLevel = baseline
        var coldPayloadBytes = warmBytes
        val kept = patch.keptFile?.takeIf { config.reuseEncodings && java.io.File(it).exists() }
        patch.keptFile = null
        // solid / run-length / delta patches are already in the form that suits them: deeper Deflate cannot beat it
        if (kept == null && config.adaptiveCompEnabled && patch.coldTrips == 0 && warmBytes.isNotEmpty() && patch.rawLength > 0 &&
            PatchCodec.isDeflate(warmBytes)
        ) {
            val cls = tuner.classOf(patch.rawLength, warmBytes.size)
            val io = (costModel?.writeNsPerByte() ?: 0.0) + (costModel?.readNsPerByte() ?: 0.0)
            val level = tuner.choose(
                cls = cls,
                warmBytes = warmBytes.size.toLong(),
                rawBytes = patch.rawLength.toLong(),
                baseline = baseline,
                maxLevel = config.coldCompressionLevel,
                ioNsPerByte = if (io > 0.0) io else null,
                inflateNsPerRaw = costModel?.decompressNsPerRawByte(),
                squeeze = tight
            )
            if (level > baseline) {
                coldPayloadBytes = withContext(Dispatchers.Default) {
                    try {
                        val raw = PatchCodec.decompress(warmBytes, patch.rawLength, patch.crc32)
                        val t0 = System.nanoTime()
                        val recompressed = PatchCodec.compress(raw, level)
                        val ns = System.nanoTime() - t0
                        tuner.record(cls, level, patch.rawLength.toLong(), warmBytes.size.toLong(),
                            recompressed.compressedBytes.size.toLong(), ns)
                        costModel?.recordRecompress(
                            rawBytes = patch.rawLength.toLong(),
                            warmBytes = warmBytes.size.toLong(),
                            coldBytes = recompressed.compressedBytes.size.toLong(),
                            ns = ns
                        )
                        deep = level >= config.coldCompressionLevel
                        if (recompressed.compressedBytes.size < warmBytes.size) {
                            usedLevel = level
                            adaptiveCompSavedBytes += (warmBytes.size - recompressed.compressedBytes.size).toLong()
                            recompressed.compressedBytes
                        } else {
                            warmBytes
                        }
                    } catch (_: Throwable) {
                        warmBytes
                    }
                }
            }
        }

        // Make room first; if that is impossible keep the patch in Warm instead of failing the user's stroke
        if (!ensureColdSpace(coldPayloadBytes.size.toLong())) {
            restoreToWarm(patch)
            events?.emit(TierEventKind.HELD_IN_WARM, patch.strokeNo, warmBytes.size.toLong(), warmBytes.size.toLong(),
                "Cold không nhận thêm")
            if (!blockedNotified) {
                blockedNotified = true
                onColdBlocked?.invoke()
            }
            return false
        }

        val file = if (kept != null) {
            reusedColdFiles++
            java.io.File(kept)                      // same bytes are already on disk: nothing to write
        } else try {
            writeCold(patch, coldPayloadBytes)
        } catch (_: Exception) {
            // The disk refused anyway (quota, another app): free what we can once and retry once
            relieveCold(coldPayloadBytes.size.toLong(), extraSlots = 1)
            try {
                writeCold(patch, coldPayloadBytes)
            } catch (_: Exception) {
                restoreToWarm(patch)
                return false
            }
        }

        patch.filePath = file.absolutePath
        if (epoch != epochAtStart) {            // cleared while this was being written
            try { file.delete() } catch (_: Throwable) {}
            return false
        }
        patch.sizeBytes = file.length()
        patch.deepCompressed = deep
        patch.coldTrips++
        patch.state = PatchState.COLD
        blockedNotified = false

        coldStore.add(patch)
        events?.emit(
            TierEventKind.WARM_TO_COLD, patch.strokeNo, warmBytes.size.toLong(), patch.sizeBytes,
            if (noteOverride != null) "mức $usedLevel · $noteOverride" else "mức $usedLevel"
        )

        // Free compressed bytes (and any pre-decompressed bitmap) from RAM
        patch.compressedBytes = null
        patch.recycle()
        return true
    }

    private suspend fun writeCold(patch: Patch, bytes: ByteArray): File {
        val start = System.nanoTime()
        val file = withContext(Dispatchers.IO) { coldStore.writeToFile(patch, bytes) }
        costModel?.recordWrite(bytes.size.toLong(), System.nanoTime() - start)
        return file
    }

    private fun restoreToWarm(patch: Patch) {
        patch.state = PatchState.WARM
        warmStore.add(patch)
    }

    /**
     * Handles Android onTrimMemory events according to Section 5.4:
     * - Mild (UI_HIDDEN, BACKGROUND, RUNNING_MODERATE): Hot shrunk to 50%
     * - Moderate (MODERATE, RUNNING_LOW): Hot shrunk to 25%
     * - Complete (COMPLETE, RUNNING_CRITICAL): Evict all Hot to Warm, shrink Warm to 50%
     */
    @Suppress("DEPRECATION")
    suspend fun applyTrimMemory(level: Int) {
        when {
            // Level 80 (COMPLETE) or Level 15 (RUNNING_CRITICAL)
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Severe: Push all Hot down to Warm, reduce Warm to 50%, and run SWAM OOM Cleaner
                enforceBudgets(
                    effectiveHotBudget = 0L,
                    effectiveWarmBudget = (warmBudget() * 0.5).toLong(),
                    checkOversized = false
                )
                runOomCleaner()
            }
            // Level 60 (MODERATE) or Level 10 (RUNNING_LOW)
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Moderate: Reduce Hot to 25% and run SWAM OOM Cleaner
                enforceBudgets(
                    effectiveHotBudget = (hotBudget() * 0.25).toLong(),
                    effectiveWarmBudget = warmBudget(),
                    checkOversized = false
                )
                runOomCleaner()
            }
            // Level 20 (UI_HIDDEN), Level 40 (BACKGROUND), or Level 5 (RUNNING_MODERATE)
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // Mild: Reduce Hot to 50%
                enforceBudgets(
                    effectiveHotBudget = (hotBudget() * 0.5).toLong(),
                    effectiveWarmBudget = warmBudget(),
                    checkOversized = false
                )
            }
        }
    }
}
