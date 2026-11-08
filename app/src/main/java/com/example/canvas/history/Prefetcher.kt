package com.example.canvas.history

import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adaptive prefetcher. On every undo it asks [PrefetchModel] how far each upcoming stroke group should be
 * prepared and executes that plan: read from disk into Warm RAM, and (when Hot RAM allows) decompress into
 * a bitmap so the undo itself only has to draw. The depth is never a fixed number; see [PrefetchModel].
 *
 * How much it may load is not a count: it is whatever fits in the RAM that is really free
 * ([freeMemoryBytes], by default the live JVM heap) minus the reserve that the user's own strokes need
 * ([PrefetchModel.memoryReserveBytes]), spent on the groups with the best expected saving per byte. Heavy
 * strokes therefore take more of the budget and fewer fit; light strokes are cheap and many fit.
 *
 * @param predecodeEnabled allow decompressing groups into bitmaps ahead of time.
 */
class Prefetcher(
    private val coldStore: ColdStore,
    private val warmStore: WarmStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val freeMemoryBytes: () -> Long = { MemorySignal.free() },
    private val clockNs: () -> Long = System::nanoTime,
    private val costModel: TierCostModel? = null,
    private val predecodeEnabled: Boolean = false,
    private val events: TierEventLog? = null,
    /** Extra ceiling on what read-ahead and pre-decoded bitmaps may occupy (the tiers' own free budget). */
    private val ramCapBytes: () -> Long = { Long.MAX_VALUE }
) {
    // Reads of the *next* step started as soon as an undo begins, so the disk works while this step is being
    // decompressed and drawn. They only read (no store is touched from the IO thread); the result is collected
    // by whoever needs it, on the history thread.
    private val early = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<ColdFilePayload>>()
    private val ioScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + ioDispatcher)

    val hits = AtomicInteger(0)
    val misses = AtomicInteger(0)

    val model = PrefetchModel(clockNs)

    var isEnabled: Boolean = true

    // Work queues. At every undo the *queued* work is replaced by the new plan; a read or decode that has
    // already started is allowed to finish (cancelling it would throw its work away).
    private val lock = Any()
    private val ioQueue = ArrayDeque<Pair<List<Patch>, Stage>>()
    private val decodeQueue = ArrayDeque<List<Patch>>()
    private var ioRunning = false
    private var decodeRunning = false
    private var ioWorker: Job? = null
    private var decodeWorker: Job? = null

    /** Number of stroke groups the last plan decided to prepare further (telemetry). */
    @Volatile
    var lastLookaheadGroups: Int = 0
        private set

    private val predecoded = ArrayList<Patch>()

    val predecodedBytes: Long
        get() = synchronized(predecoded) {
            predecoded.sumOf { p -> p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L }
        }

    @Volatile
    var predecodeCount: Int = 0
        private set

    fun resetStats() {
        synchronized(lock) {
            ioQueue.clear()
            decodeQueue.clear()
        }
        ioWorker?.cancel()
        decodeWorker?.cancel()
        cancelEarlyReads()
        releasePredecoded()
        hits.set(0)
        misses.set(0)
        lastLookaheadGroups = 0
        predecodeCount = 0
        model.reset()
    }

    val hitRate: Double
        get() {
            val h = hits.get()
            val m = misses.get()
            val total = h + m
            return if (total > 0) h.toDouble() / total.toDouble() else 0.0
        }

    fun recordHit() {
        hits.incrementAndGet()
    }

    fun recordMiss() {
        misses.incrementAndGet()
    }

    /** Call once per undo step performed by the user. */
    fun onUndo() = model.recordUndo()

    /** Call for every committed tile; [newStroke] is true for the first tile of each stroke. */
    fun onCommit(newStroke: Boolean, bytes: Long) {
        model.recordCommit(newStroke, bytes)
        releasePredecoded()
        cancelEarlyReads()
    }

    /** Call when a redo/clear ends the current run of consecutive undos. */
    fun onRunBreak() {
        model.recordRunBreak()
        releasePredecoded()
        cancelEarlyReads()
    }

    /** Starts reading a Cold patch from disk right now (no-op if it is not Cold or a read is already running). */
    fun startEarlyRead(patch: Patch) {
        if (!isEnabled || patch.state != PatchState.COLD || patch.filePath == null) return
        val bytesOnDisk = patch.sizeBytes
        early.computeIfAbsent(patch.uid) {
            ioScope.async {
                val started = clockNs()
                val payload = coldStore.readPayload(patch)
                recordFetch(bytesOnDisk, clockNs() - started)
                payload
            }
        }
    }

    /** The payload of an early read of [patch], waiting for it if still running; null if none was started or it failed. */
    suspend fun takeEarlyRead(patch: Patch): ColdFilePayload? {
        val d = early.remove(patch.uid) ?: return null
        return try {
            d.await()
        } catch (_: Throwable) {
            null
        }
    }

    private fun cancelEarlyReads() {
        for (d in early.values) d.cancel()
        early.clear()
    }

    /** Drops pre-decompressed bitmaps that were not consumed (their compressed form is still kept). */
    fun releasePredecoded() {
        val list = synchronized(predecoded) {
            val copy = ArrayList(predecoded)
            predecoded.clear()
            copy
        }
        for (p in list) {
            if (p.state != PatchState.HOT) {
                val bmp = p.bitmap
                if (bmp != null && !bmp.isRecycled) bmp.recycle()
                p.bitmap = null
            }
        }
    }

    /** Feed a measured Cold read (from prefetch or from a demand miss) into the cost models. */
    fun recordFetch(bytes: Long, durationNs: Long) {
        model.recordFetch(bytes, durationNs)
        costModel?.recordRead(bytes, durationNs)
    }

    /** Feed a measured decompression into the cost models. */
    fun recordDecode(rawBytes: Long, durationNs: Long) {
        model.recordDecode(rawBytes, durationNs)
        costModel?.recordDecompress(rawBytes, durationNs)
    }

    /** Feed a measured "draw the patch onto the canvas" into the cost models. */
    fun recordApply(rawBytes: Long, durationNs: Long) {
        model.recordApply(rawBytes, durationNs)
        costModel?.recordApply(rawBytes, durationNs)
    }

    private fun rawBytesOf(p: Patch): Long =
        p.rect.width().coerceAtLeast(1).toLong() * p.rect.height().coerceAtLeast(1) * 4L

    private fun hasBitmap(p: Patch): Boolean = p.bitmap?.isRecycled == false

    private fun describe(members: List<Patch>): GroupInput {
        var cold = 0L
        var decode = 0L
        var anyCold = false
        for (p in members) {
            if (p.state == PatchState.COLD && p.filePath != null) {
                anyCold = true
                cold += p.sizeBytes
            }
            if (!hasBitmap(p)) decode += rawBytesOf(p)
        }
        val stage = when {
            anyCold -> Stage.COLD
            decode > 0 -> Stage.WARM
            else -> Stage.BITMAP
        }
        return GroupInput(stage, cold, decode)
    }

    /**
     * Plans and launches preparation for the patches that will be undone next.
     * [undoHistory] is ordered oldest -> newest; the newest is undone first.
     */
    fun prefetchAhead(
        undoHistory: List<Patch>,
        scope: CoroutineScope
    ) {
        if (!isEnabled || undoHistory.isEmpty()) return

        val members = ArrayList<List<Patch>>()
        val groups = sequence {
            var idx = undoHistory.size - 1
            while (idx >= 0) {
                val first = undoHistory[idx--]
                val tiles = arrayListOf(first)
                if (first.groupId != 0L) {
                    while (idx >= 0 && undoHistory[idx].groupId == first.groupId) tiles.add(undoHistory[idx--])
                }
                members.add(tiles)
                yield(describe(tiles))
            }
        }

        val ramBudget = minOf(
            freeMemoryBytes() - model.memoryReserveBytes() - predecodedBytes,
            ramCapBytes() - predecodedBytes
        ).coerceAtLeast(0L)
        val targets = model.plan(groups, ramBudget, allowBitmap = predecodeEnabled)

        var upgraded = 0
        val work = ArrayList<Pair<List<Patch>, Stage>>()
        for ((i, target) in targets.withIndex()) {
            val tiles = members[i]
            val before = describe(tiles).stage
            if (target.ordinal > before.ordinal) {
                upgraded++
                work.add(tiles to target)
            }
        }
        lastLookaheadGroups = upgraded

        synchronized(lock) {
            ioQueue.clear()
            decodeQueue.clear()
            for ((tiles, target) in work) {
                val needsRead = tiles.any { it.state == PatchState.COLD && it.filePath != null }
                if (needsRead) ioQueue.add(tiles to target)
                else if (target == Stage.BITMAP) decodeQueue.add(tiles)
            }
        }
        ensureWorkers(scope)
    }

    private fun ensureWorkers(scope: CoroutineScope) {
        synchronized(lock) {
            if (!ioRunning && ioQueue.isNotEmpty()) {
                ioRunning = true
                ioWorker = scope.launch {
                    try {
                        while (true) {
                            val item = synchronized(lock) {
                                ioQueue.removeFirstOrNull().also { if (it == null) ioRunning = false }
                            } ?: break
                            val (tiles, target) = item
                            for (patch in tiles) prefetchPatchToWarm(patch)   // sequential disk reads
                            if (target == Stage.BITMAP) {
                                synchronized(lock) { decodeQueue.add(tiles) }
                                ensureWorkers(scope)
                            }
                        }
                    } finally {
                        synchronized(lock) { ioRunning = false }
                    }
                }
            }
            if (!decodeRunning && decodeQueue.isNotEmpty()) {
                decodeRunning = true
                decodeWorker = scope.launch {
                    try {
                        while (true) {
                            val tiles = synchronized(lock) {
                                decodeQueue.removeFirstOrNull().also { if (it == null) decodeRunning = false }
                            } ?: break
                            predecodeGroup(tiles)   // runs in parallel with the next disk read
                        }
                    } finally {
                        synchronized(lock) { decodeRunning = false }
                    }
                }
            }
        }
    }

    /**
     * Decompresses a stroke group into bitmaps ahead of time, if it still fits in free RAM and the
     * patch is still in Warm.
     */
    private suspend fun predecodeGroup(group: List<Patch>) {
        val todo = group.filter { it.state == PatchState.WARM && it.compressedBytes != null && !hasBitmap(it) }
        if (todo.isEmpty()) return
        val rawTotal = todo.sumOf { rawBytesOf(it) }
        // peak while decoding = the bitmap plus the same again for the temporary inflated buffer; the tiers'
        // budget decides (letting one group exceed it was measured and did not shorten undo)
        if (!predecodeEnabled ||
            freeMemoryBytes() - model.memoryReserveBytes() - predecodedBytes < 2 * rawTotal ||
            ramCapBytes() - predecodedBytes < rawTotal
        ) return

        for (patch in todo) {
            val bytes = patch.compressedBytes ?: continue
            val w = patch.rect.width().coerceAtLeast(1)
            val h = patch.rect.height().coerceAtLeast(1)
            try {
                val t0 = clockNs()
                val bmp = withContext(Dispatchers.Default) {
                    PatchCodec.decompressToBitmap(
                        compressedBytes = bytes,
                        width = w,
                        height = h,
                        rawLength = if (patch.rawLength > 0) patch.rawLength else w * h * 4,
                        expectedCrc32 = 0L
                    )
                }
                recordDecode(w.toLong() * h * 4L, clockNs() - t0)
                // The patch may have been undone/demoted while decoding: attach only if still in Warm
                if (patch.state == PatchState.WARM && warmStore.contains(patch) && !hasBitmap(patch)) {
                    patch.bitmap = bmp
                    synchronized(predecoded) { predecoded.add(patch) }
                    predecodeCount++
                    events?.emit(TierEventKind.PREDECODE, patch.strokeNo, bytes.size.toLong(), w.toLong() * h * 4L, "giải nén sẵn")
                } else {
                    bmp.recycle()
                }
            } catch (_: Throwable) {
                // leave the patch compressed; undo will decode it on demand
            }
        }
    }

    /**
     * Reads a patch from disk into RAM compressed bytes, promoting it to Warm.
     */
    suspend fun prefetchPatchToWarm(patch: Patch) {
        if (patch.state != PatchState.COLD || patch.filePath == null) return

        try {
            val bytesOnDisk = patch.sizeBytes
            val earlyPayload = takeEarlyRead(patch)
            val payload = if (earlyPayload != null) {
                earlyPayload                       // already read and measured by the early read
            } else {
                val started = clockNs()
                val read = withContext(ioDispatcher) { coldStore.readPayload(patch) }
                recordFetch(bytesOnDisk, clockNs() - started)
                read
            }

            // Leave Cold first (uses the Cold size for accounting). If the patch was undone while we
            // were reading, it is no longer in Cold and must not be resurrected into Warm.
            if (!coldStore.remove(patch)) return

            // Successfully read into RAM -> promote to WARM
            patch.compressedBytes = payload.compressedBytes
            patch.rawLength = payload.rawLength
            patch.crc32 = payload.crc32
            patch.sizeBytes = payload.compressedBytes.size.toLong()
            patch.state = PatchState.WARM
            warmStore.add(patch)
            events?.emit(TierEventKind.COLD_TO_WARM, patch.strokeNo, bytesOnDisk, patch.sizeBytes, "nạp trước")
        } catch (_: Throwable) {
            // If prefetch fails, patch remains accessible on disk
        }
    }
}
