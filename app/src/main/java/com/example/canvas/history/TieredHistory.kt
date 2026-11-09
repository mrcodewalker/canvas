package com.example.canvas.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchFormat
import com.example.canvas.core.PatchState
import com.example.canvas.core.StrokeOp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-tiered history manager implementing SWAM's progressive down-tiering philosophy:
 * Hot (RAM Bitmap) -> Warm (RAM Deflated Bytes) -> Cold (Disk Swapped File).
 * All state mutations are serialized through a single-threaded dispatcher.
 */
class TieredHistory(
    val historyDir: File,
    var tierConfig: TierConfig = TierConfig(),
    /** Optional hard cap on redo steps; null (default) means memory alone decides. */
    private val maxRedoSteps: Int? = null,
    private val historyDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) : HistoryManager {

    companion object {
        /** The configuration the app starts with; experiments derive their variants from it with `copy(...)`. */
        fun defaultConfig() = TierConfig(
            hotBudgetBytes = 4L * 1024 * 1024, // 4 MiB Hot RAM (~4-8 recent patches)
            warmBudgetBytes = 256L * 1024,     // 256 KiB Warm compressed RAM (~8-15 compressed patches)
            infiniteUndo = true,
            coldMaxSteps = Int.MAX_VALUE,
            coldMaxBytes = 4L * 1024 * 1024 * 1024, // 4 GiB disk ceiling for Infinite Undo
            evictionStrategy = SwamEvictionStrategy.SWAM_COST_AWARE
        )

        fun create(
            context: Context,
            tierConfig: TierConfig = defaultConfig()
        ): TieredHistory {
            val dir = File(context.filesDir, "history")
            return TieredHistory(historyDir = dir, tierConfig = tierConfig)
        }

        private val srcPaint by lazy {
            Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }
        }
    }

    override val mode: HistoryMode = HistoryMode.TIERED
    override var opRenderer: ((Canvas, StrokeOp) -> Unit)? = null

    private val scope = CoroutineScope(historyDispatcher + SupervisorJob())

    val hotStore = HotStore()
    val warmStore = WarmStore()
    val coldStore = ColdStore(historyDir)
    val costs = TierCostModel()

    /** Tier transitions for the tracker panel; costs nothing while nobody listens. */
    val events = TierEventLog()
    private var strokeCounter = 0

    val tierPolicy: TierPolicy = TierPolicy(
        config = tierConfig,
        hotStore = hotStore,
        warmStore = warmStore,
        coldStore = coldStore,
        onColdEvicted = { evictedPatch ->
            undoHistory.remove(evictedPatch)
        },
        costModel = costs,
        reserveBytes = { prefetcher.model.memoryReserveBytes() },
        onTrimmed = { strokes -> onHistoryTrimmed?.invoke(strokes, distinctGroups(undoHistory)) },
        undoDepths = { depthMap() },
        reachFunction = { prefetcher.model.reachFunction() },
        onColdBlocked = { onColdBlocked?.invoke() },
        autoBudgets = { computeAutoBudgets() },
        events = events,
        patchOverheadBytes = { prefetcher.model.patchOverheadBytes() ?: 0L }
    )

    /**
     * Hot/Warm budgets from this user's strokes instead of fixed numbers. The RAM the user allows for history
     * (Hot + Warm as configured) is split by [TierBudgetPlanner]: Hot keeps only what is about to be undone,
     * Warm, which stores the same steps at a few percent of the size, gets the rest. Available as soon as the
     * first strokes were drawn (it does not wait for undo habits), limited by the memory that is really free.
     */
    private fun computeAutoBudgets(): Pair<Long, Long>? {
        val m = prefetcher.model
        val typical = m.medianStrokeBytes() ?: return null
        val heavy = m.heavyStrokeBytes() ?: return null
        val total = tierPolicy.config.hotBudgetBytes + tierPolicy.config.warmBudgetBytes
        val room = (MemorySignal.free() - m.memoryReserveBytes()).coerceAtLeast(0L)
        return TierBudgetPlanner.split(total, typical, heavy, m.expectedRun(), room)
    }

    override fun patchOverheadBytes(): Long? = prefetcher.model.patchOverheadBytes()

    /** Called when Cold is full and the chosen policy forbids dropping history (it stays in RAM instead). */
    var onColdBlocked: (() -> Unit)? = null

    private fun depthMap(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        var depth = 0
        var prevGroup = Long.MIN_VALUE
        for (i in undoHistory.indices.reversed()) {
            val p = undoHistory[i]
            if (p.groupId == 0L || p.groupId != prevGroup) depth++
            prevGroup = p.groupId
            out[p.id] = depth - 1
        }
        return out
    }

    /** Called when low disk space forced the oldest strokes out of the undo history: (strokes dropped, steps left). */
    var onHistoryTrimmed: ((evictedStrokes: Int, remainingSteps: Int) -> Unit)? = null
    val prefetcher: Prefetcher = Prefetcher(
        coldStore = coldStore,
        warmStore = warmStore,
        costModel = costs,
        predecodeEnabled = true,
        events = events,
        ramCapBytes = {
            // read-ahead and decoded bitmaps live in Warm/Hot: never more than those tiers' free budget
            (tierPolicy.hotBudget() - hotStore.totalBytes).coerceAtLeast(0L) +
                (tierPolicy.warmBudget() - warmStore.totalBytes).coerceAtLeast(0L)
        }
    )

    /** Receives the tier of every step whenever something changes (set by the tracker panel). */
    @Volatile
    var tierMapListener: ((List<TierCell>) -> Unit)? = null

    private val mapPending = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        events.afterEmit = { requestTierMap() }
        com.example.canvas.core.BufferPool.limit = { tierPolicy.hotBudget() / 2 }
        prefetcher.keepColdFiles = tierConfig.reuseEncodings
    }

    /** Asks for a fresh tier map; computed on the history thread, at most one pending at a time. */
    fun requestTierMap() {
        if (tierMapListener == null) return
        if (mapPending.compareAndSet(false, true)) {
            scope.launch {
                mapPending.set(false)
                publishTierMap()
            }
        }
    }

    private fun publishTierMap() {
        val listener = tierMapListener ?: return
        val cells = ArrayList<TierCell>()
        var stroke = -1
        var tier = 0
        var bytes = 0L
        var allReady = true
        fun flush() {
            if (stroke >= 0) cells.add(TierCell(stroke, tier, bytes, tier == 1 && allReady))
        }
        for (p in undoHistory) {
            if (p.strokeNo != stroke) {
                flush()
                stroke = p.strokeNo; tier = 0; bytes = 0L; allReady = true
            }
            val t = when (p.state) {
                PatchState.COLD, PatchState.WRITING -> 2
                PatchState.WARM, PatchState.COMPRESSING -> 1
                else -> 0
            }
            if (t > tier) tier = t
            bytes += p.sizeBytes
            if (p.bitmap?.isRecycled != false) allReady = false
        }
        flush()
        listener(cells)
    }

    // Per-step timing of the most recent undo (read / decompress / apply), in nanoseconds
    @Volatile private var lastReadNs = 0L
    @Volatile private var lastDecompressNs = 0L
    @Volatile private var lastApplyNs = 0L
    private var stepReadNs = 0L
    private var stepDecompressNs = 0L

    /** Learns how deep this user redoes; sizes the redo stack instead of a fixed count. */
    val redoBehavior = RedoBehavior()

    private val undoHistory = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()
    private var totalRawEquivalentBytes: Long = 0L
    private var lastCommittedGroupId: Long = -1L

    override suspend fun commit(before: Patch): Unit = withContext(historyDispatcher) {
        before.state = PatchState.HOT
        if (before.bitmap != null) {
            before.sizeBytes = before.bitmap!!.allocationByteCount.toLong()
            before.rawLength = before.bitmap!!.byteCount
        }
        val rawLen = if (before.rawLength > 0) before.rawLength.toLong() else before.sizeBytes
        totalRawEquivalentBytes += rawLen

        hotStore.add(before)
        undoHistory.addLast(before)

        // New drawing no longer wipes the redo stack: overlapping steps lose their stale pixels and will be
        // replayed (merged) on redo; the others stay exactly restorable
        redoBehavior.onOther()
        RedoMerge.invalidate(redoStack, before.rect)
        val newStroke = before.groupId == 0L || before.groupId != lastCommittedGroupId
        lastCommittedGroupId = before.groupId
        if (newStroke) strokeCounter++
        before.strokeNo = strokeCounter
        events.emit(TierEventKind.NEW, before.strokeNo, 0L, before.sizeBytes, "vào Hot")
        prefetcher.onCommit(newStroke, before.sizeBytes)

        settleTiers()
    }

    // ---- moving steps down the tiers without making the user wait ---------------------------------------------------
    // Only touched on the history thread (every coroutine of [scope] runs there), so plain fields are enough.
    private var settling = false
    private var settleAgain = false

    override suspend fun settle() {
        withContext(historyDispatcher) {
            while (settling) kotlinx.coroutines.delay(2L)
        }
    }

    /**
     * Moves what no longer fits down a tier. Normally this runs in the background: a stroke is committed as soon as
     * it is in Hot, and the compression or disk write that makes room happens next to the drawing, not inside it.
     * It waits only when Hot is already a whole budget over, which keeps RAM bounded when strokes arrive faster
     * than they can be compressed.
     */
    private suspend fun settleTiers() {
        if (!tierPolicy.config.asyncDemotion || hotStore.totalBytes > 2 * tierPolicy.hotBudget()) {
            tierPolicy.enforceBudgets()
            return
        }
        if (settling) {
            settleAgain = true
            return
        }
        settling = true
        scope.launch {
            try {
                do {
                    settleAgain = false
                    tierPolicy.enforceBudgets()
                } while (settleAgain)
            } finally {
                settling = false
            }
        }
    }

    /** Pops the top patch plus every other patch of the same stroke group (dirty tiles). */
    private fun popGroup(stack: ArrayDeque<Patch>): List<Patch> {
        val first = stack.removeLast()
        if (first.groupId == 0L) return listOf(first)
        val group = arrayListOf(first)
        while (stack.isNotEmpty() && stack.last().groupId == first.groupId) {
            group.add(stack.removeLast())
        }
        return group
    }

    private fun distinctGroups(stack: ArrayDeque<Patch>): Int {
        // stats() may race with a mutation on another thread: fall back to the raw size then.
        return try {
            var count = 0
            var prev = Long.MIN_VALUE
            for (p in stack) {
                if (p.groupId == 0L || p.groupId != prev) count++
                prev = p.groupId
            }
            count
        } catch (_: RuntimeException) {
            stack.size
        }
    }

    override suspend fun undo(canvas: Bitmap): Boolean = withContext(historyDispatcher) {
        if (undoHistory.isEmpty()) return@withContext false

        val group = popGroup(undoHistory)
        redoBehavior.onOther()
        prefetcher.onUndo()
        // The next step to be undone: start reading it from disk now, so the read overlaps with decoding and
        // drawing the step in hand. Only the wait that is left is counted as a stall.
        run {
            val top = undoHistory.lastOrNull() ?: return@run
            var i = undoHistory.size - 1
            while (i >= 0 && (undoHistory[i].groupId == top.groupId || undoHistory[i] === top)) {
                prefetcher.startEarlyRead(undoHistory[i])
                if (top.groupId == 0L) break
                i--
            }
        }
        val sourceTier = when {
            group.any { it.state == PatchState.COLD } -> "Cold"
            group.any { it.state == PatchState.WARM } && !group.all { it.bitmap?.isRecycled == false } -> "Warm"
            group.any { it.state == PatchState.WARM } -> "Warm, đã giải nén sẵn"
            else -> "Hot"
        }
        stepReadNs = 0L
        stepDecompressNs = 0L
        var stepApplyNs = 0L

        for (patchToRestore in group) {
            val rawLen = if (patchToRestore.rawLength > 0) patchToRestore.rawLength.toLong() else patchToRestore.sizeBytes
            totalRawEquivalentBytes = (totalRawEquivalentBytes - rawLen).coerceAtLeast(0L)
            val rect = patchToRestore.rect
            val patchW = (rect.right - rect.left).coerceAtLeast(1)
            val patchH = (rect.bottom - rect.top).coerceAtLeast(1)

            // Track prefetch hit/miss for telemetry
            if (patchToRestore.state == PatchState.COLD) {
                prefetcher.recordMiss()
            } else if (patchToRestore.state == PatchState.WARM) {
                prefetcher.recordHit()
            }

            // 1. Capture current canvas region for redo (retained in Hot)
            val redoBitmap = Bitmap.createBitmap(canvas, rect.left, rect.top, patchW, patchH)
            redoStack.addLast(
                Patch(
                    id = System.nanoTime(),
                    rect = rect,
                    state = PatchState.HOT,
                    bitmap = redoBitmap,
                    sizeBytes = redoBitmap.allocationByteCount.toLong(),
                    rawLength = redoBitmap.byteCount,
                    groupId = patchToRestore.groupId,
                    op = patchToRestore.op,
                    strokeNo = patchToRestore.strokeNo
                )
            )

            // 2. Resolve bitmap representation of patchToRestore
            lastPayload = null
            val bitmapToApply: Bitmap = resolveBitmapForPatch(patchToRestore, patchW, patchH)

            // Redo will capture these very pixels again: remember how they were compressed so that it need not
            // compress them (and the tier moves after it) a second time
            var enc = encodingOf(patchToRestore, patchW, patchH)

            // Remove from whichever store it belonged to. A Cold file is left on disk when its bytes are kept for redo.
            hotStore.remove(patchToRestore)
            warmStore.remove(patchToRestore)
            if (enc != null && patchToRestore.state == PatchState.COLD) {
                val kept = coldStore.detach(patchToRestore).keptFile
                if (kept != null) enc = Patch.Encoding(enc.bytes, enc.rawLength, enc.crc32, enc.level, enc.deep, enc.coldTrips, kept)
            }
            coldStore.remove(patchToRestore)
            patchToRestore.keptFile?.let { f ->
                try { java.io.File(f).delete() } catch (_: Throwable) {}   // nothing will reuse it
                patchToRestore.keptFile = null
            }
            redoStack.lastOrNull()?.reuse = enc

            // 3. Overwrite canvas region with PorterDuff.Mode.SRC
            val nativeCanvas = Canvas(canvas)
            val applyStart = System.nanoTime()
            nativeCanvas.drawBitmap(bitmapToApply, rect.left.toFloat(), rect.top.toFloat(), srcPaint)
            val applyNs = System.nanoTime() - applyStart
            stepApplyNs += applyNs
            prefetcher.recordApply(patchW.toLong() * patchH * 4L, applyNs)
            bitmapToApply.recycle()
        }
        lastReadNs = stepReadNs
        lastDecompressNs = stepDecompressNs
        lastApplyNs = stepApplyNs
        enforceRedoMemory()
        events.emit(
            TierEventKind.UNDO, group.first().strokeNo, 0L, 0L,
            buildString {
                // only the steps that really cost something are listed
                append("từ ").append(sourceTier).append(" · ")
                if (stepReadNs > 0) append(String.format(java.util.Locale.US, "đọc %.1f · ", stepReadNs / 1e6))
                if (stepDecompressNs > 0) append(String.format(java.util.Locale.US, "giải nén %.1f · ", stepDecompressNs / 1e6))
                append(String.format(java.util.Locale.US, "vẽ %.1f ms", stepApplyNs / 1e6))
            }
        )

        // 4. Adaptive prefetch: the model decides how many upcoming stroke groups to read Cold -> Warm
        prefetcher.prefetchAhead(undoHistory, scope)

        return@withContext true
    }

    private fun enforceRedoMemory() {
        // redo pixels count against the same budget as Hot RAM and follow how deep this user redoes
        RedoMerge.enforce(
            redoStack,
            keepDepth = redoBehavior.typicalDepth() ?: 1,
            pixelBudget = tierPolicy.hotBudget(),
            reach = redoBehavior::reach
        )
        RedoMerge.dropLeastValuable(redoStack, RedoMerge.excess(redoStack), redoBehavior::reach)
        val cap = maxRedoSteps ?: return
        while (redoStack.isNotEmpty() && distinctGroups(redoStack) > cap) {
            val oldest = redoStack.removeFirst()
            oldest.recycle()
            while (oldest.groupId != 0L && redoStack.isNotEmpty() && redoStack.first().groupId == oldest.groupId) {
                redoStack.removeFirst().recycle()
            }
        }
    }

    override suspend fun redo(canvas: Bitmap): Boolean = withContext(historyDispatcher) {
        if (redoStack.isEmpty()) return@withContext false

        val group = popGroup(redoStack)
        redoBehavior.onRedo()
        prefetcher.onRunBreak()

        val replay = RedoMerge.needsReplay(group)
        val op = group.first().op
        val render = opRenderer
        if (replay && (op == null || render == null)) {
            group.forEach { it.recycle() }       // nothing left to redo this step from
            return@withContext false
        }

        for (patchToRestore in group) {
            val rect = patchToRestore.rect
            val patchW = (rect.right - rect.left).coerceAtLeast(1)
            val patchH = (rect.bottom - rect.top).coerceAtLeast(1)

            // Capture current canvas region for undo
            val undoBitmap = Bitmap.createBitmap(canvas, rect.left, rect.top, patchW, patchH)
            val undoPatch = Patch(
                id = System.nanoTime(),
                rect = rect,
                state = PatchState.HOT,
                bitmap = undoBitmap,
                sizeBytes = undoBitmap.allocationByteCount.toLong(),
                rawLength = undoBitmap.byteCount,
                groupId = patchToRestore.groupId,
                op = patchToRestore.op,
                strokeNo = patchToRestore.strokeNo
            )
            // The encoding describes the pixels this undo patch captures now. It is still there only if nothing
            // drawn since the undo touched this area (that would have cleared it), so the pixels are the same
            // ones whether the step is restored from its saved pixels or replayed from its stroke.
            undoPatch.reuse = patchToRestore.reuse
            patchToRestore.reuse = null
            hotStore.add(undoPatch)
            undoHistory.addLast(undoPatch)
            totalRawEquivalentBytes += undoPatch.rawLength.toLong()

            if (!replay) {
                // Canvas under this step is unchanged since the undo: exact pixel restore
                val bmp = patchToRestore.bitmap
                if (bmp != null) {
                    Canvas(canvas).drawBitmap(bmp, rect.left.toFloat(), rect.top.toFloat(), srcPaint)
                }
            }
        }
        if (replay) {
            // Canvas changed under this step: draw the stroke again on top of whatever is there now (merge)
            render!!(Canvas(canvas), op!!)
        }
        group.forEach { it.recycle() }
        events.emit(
            TierEventKind.REDO, group.first().strokeNo, 0L, 0L,
            if (replay) "vẽ lại trên bản vẽ hiện tại" else "khôi phục pixel"
        )

        settleTiers()
        return@withContext true
    }

    /** The Cold file read by the latest [resolveBitmapForPatch], if it had to read one. */
    private var lastPayload: ColdFilePayload? = null

    private fun encodingOf(p: Patch, w: Int, h: Int): Patch.Encoding? {
        if (!tierPolicy.config.reuseEncodings) return null
        p.reuse?.let { return it }
        val level = if (p.warmLevel > 0) p.warmLevel else tierPolicy.config.compressionLevel
        val warm = p.compressedBytes
        if (warm != null && p.rawLength > 0) {
            val file = p.keptFile
            p.keptFile = null                       // the encoding owns the file from now on
            return Patch.Encoding(warm, p.rawLength, p.crc32, level, p.deepCompressed, p.coldTrips, file)
        }
        val read = lastPayload ?: return null
        val cold = if (p.deepCompressed) tierPolicy.config.coldCompressionLevel else level
        return Patch.Encoding(read.compressedBytes, read.rawLength, read.crc32, cold, p.deepCompressed, p.coldTrips)
    }

    private suspend fun resolveBitmapForPatch(patch: Patch, width: Int, height: Int): Bitmap {
        // Case 1: Bitmap is already in RAM (HOT, in middle of COMPRESSING, or pre-decompressed)
        patch.bitmap?.takeIf { !it.isRecycled }?.let { return it }

        val rawBytes = width.toLong() * height * 4L

        // Case 2: In RAM compressed (WARM or in middle of WRITING)
        patch.compressedBytes?.let { bytes ->
            val t0 = System.nanoTime()
            val bmp = PatchCodec.decompressToBitmap(
                compressedBytes = bytes,
                width = width,
                height = height,
                rawLength = if (patch.rawLength > 0) patch.rawLength else width * height * 4,
                expectedCrc32 = 0L   // bytes in RAM: no checksum pass on the undo path (disk data is verified)
            )
            val dt = System.nanoTime() - t0
            stepDecompressNs += dt
            prefetcher.recordDecode(rawBytes, dt)
            return bmp
        }

        // Case 3: On Disk (COLD)
        val readBytes = patch.sizeBytes
        val readStart = System.nanoTime()
        val early = prefetcher.takeEarlyRead(patch)          // usually already read while the previous step ran
        val payload = early ?: coldStore.readPayload(patch)
        lastPayload = payload
        val readNs = System.nanoTime() - readStart           // what is left to wait is the real stall
        stepReadNs += readNs
        if (early == null) prefetcher.recordFetch(readBytes, readNs)

        val t1 = System.nanoTime()
        val bmp = PatchCodec.decompressToBitmap(
            compressedBytes = payload.compressedBytes,
            width = width,
            height = height,
            rawLength = payload.rawLength,
            expectedCrc32 = payload.crc32
        )
        val dt = System.nanoTime() - t1
        stepDecompressNs += dt
        prefetcher.recordDecode(rawBytes, dt)
        return bmp
    }

    override fun onTrimMemory(level: Int) {
        scope.launch {
            // redo steps keep their stroke but give back their pixels
            RedoMerge.dropOldestPixels(redoStack, Long.MAX_VALUE)
            tierPolicy.applyTrimMemory(level)
        }
    }

    suspend fun trimMemorySuspend(level: Int) = withContext(historyDispatcher) {
        tierPolicy.applyTrimMemory(level)
    }

    suspend fun runOomCleaner(): Long = withContext(historyDispatcher) {
        tierPolicy.runOomCleaner()
    }

    fun updateTierConfig(newConfig: TierConfig) {
        tierConfig = newConfig
        tierPolicy.config = newConfig
        prefetcher.keepColdFiles = newConfig.reuseEncodings
        scope.launch {
            tierPolicy.enforceBudgets()
        }
    }

    override fun stats(): HistoryStats {
        return HistoryStats(
            hotBytes = hotStore.totalBytes,
            warmBytes = warmStore.totalBytes,
            coldBytes = coldStore.totalBytes,
            hotSteps = hotStore.count,
            warmSteps = warmStore.count,
            coldSteps = coldStore.count,
            totalSteps = distinctGroups(undoHistory),
            redoSteps = distinctGroups(redoStack),
            prefetchHits = prefetcher.hits.get(),
            prefetchMisses = prefetcher.misses.get(),
            prefetchLookahead = prefetcher.lastLookaheadGroups,
            predecodedPatches = prefetcher.predecodeCount,
            predecodedBytes = prefetcher.predecodedBytes,
            hotBudgetBytes = tierPolicy.hotBudget(),
            warmBudgetBytes = tierPolicy.warmBudget(),
            lastUndoReadMs = lastReadNs / 1_000_000.0,
            lastUndoDecompressMs = lastDecompressNs / 1_000_000.0,
            lastUndoApplyMs = lastApplyNs / 1_000_000.0,
            rawEquivalentBytes = totalRawEquivalentBytes,
            costCompressMs = costs.totalsMs()[0],
            costInflateMs = costs.totalsMs()[1],
            costReadMs = costs.totalsMs()[2],
            costWriteMs = costs.totalsMs()[3],
            costRecompressMs = costs.totalsMs()[4],
            costApplyMs = costs.totalsMs()[5],
            oomCleanerReclaimedBytes = tierPolicy.oomCleanerReclaimedBytes,
            deduplicatedPatches = tierPolicy.deduplicatedPatches,
            reusedEncodings = tierPolicy.reusedEncodings,
            reusedColdFiles = tierPolicy.reusedColdFiles,
            codecSolid = tierPolicy.codecs.count(PatchFormat.SOLID),
            codecRle = tierPolicy.codecs.count(PatchFormat.RLE),
            codecDelta = tierPolicy.codecs.count(PatchFormat.DELTA),
            codecDeflate = tierPolicy.codecs.count(PatchFormat.DEFLATE),
            codecSavedBytes = tierPolicy.codecs.savedVsDeflateBytes,
            codecTrialMs = tierPolicy.codecs.trialNs / 1_000_000.0,
            coldPressureEvents = tierPolicy.coldPressureEvents,
            coldRescuedBytes = tierPolicy.coldRescuedBytes,
            coldEvictedSteps = tierPolicy.evictedStrokes,
            diskFreeBytes = tierPolicy.diskFree(),
            infiniteUndoEnabled = tierConfig.infiniteUndo,
            evictionStrategy = tierConfig.evictionStrategy.name
        )
    }

    override fun clear() {
        scope.launch {
            tierPolicy.epoch++           // moves still in flight must not put their step back
            hotStore.clear()
            warmStore.clear()
            coldStore.clear()
            undoHistory.clear()
            totalRawEquivalentBytes = 0L
            tierPolicy.resetSwamMetrics()
            strokeCounter = 0
            clearRedoStackLocked()
            prefetcher.resetStats()
            redoBehavior.reset()
        }
    }

    private fun clearRedoStackLocked() {
        for (p in redoStack) {
            p.recycle()
        }
        redoStack.clear()
    }
}
