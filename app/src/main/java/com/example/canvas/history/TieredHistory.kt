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
import com.example.canvas.core.PatchState
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
    private val maxRedoSteps: Int = 20,
    private val historyDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) : HistoryManager {

    companion object {
        fun create(
            context: Context,
            tierConfig: TierConfig = TierConfig(
                infiniteUndo = true,
                coldMaxSteps = Int.MAX_VALUE,
                coldMaxBytes = 4L * 1024 * 1024 * 1024, // 4 GiB disk ceiling for Infinite Undo
                evictionStrategy = SwamEvictionStrategy.SWAM_COST_AWARE
            )
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

    private val scope = CoroutineScope(historyDispatcher + SupervisorJob())

    val hotStore = HotStore()
    val warmStore = WarmStore()
    val coldStore = ColdStore(historyDir)
    val tierPolicy = TierPolicy(
        config = tierConfig,
        hotStore = hotStore,
        warmStore = warmStore,
        coldStore = coldStore,
        onColdEvicted = { evictedPatch ->
            undoHistory.remove(evictedPatch)
        }
    )
    val prefetcher = Prefetcher(coldStore, warmStore, k = 5)

    private val undoHistory = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()
    private var totalRawEquivalentBytes: Long = 0L

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

        // Clear redo stack on new action
        clearRedoStackLocked()

        // Asynchronously check and enforce tier budgets
        tierPolicy.enforceBudgets()
    }

    override suspend fun undo(canvas: Bitmap): Boolean = withContext(historyDispatcher) {
        if (undoHistory.isEmpty()) return@withContext false

        val patchToRestore = undoHistory.removeLast()
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
        val redoPatch = Patch(
            id = System.nanoTime(),
            rect = rect,
            state = PatchState.HOT,
            bitmap = redoBitmap,
            sizeBytes = redoBitmap.allocationByteCount.toLong(),
            rawLength = redoBitmap.byteCount
        )
        if (redoStack.size >= maxRedoSteps) {
            val oldest = redoStack.removeFirst()
            oldest.recycle()
        }
        redoStack.addLast(redoPatch)

        // 2. Resolve bitmap representation of patchToRestore
        val bitmapToApply: Bitmap = resolveBitmapForPatch(patchToRestore, patchW, patchH)

        // Remove from whichever store it belonged to
        hotStore.remove(patchToRestore)
        warmStore.remove(patchToRestore)
        coldStore.remove(patchToRestore)

        // 3. Overwrite canvas region with PorterDuff.Mode.SRC
        val nativeCanvas = Canvas(canvas)
        nativeCanvas.drawBitmap(bitmapToApply, rect.left.toFloat(), rect.top.toFloat(), srcPaint)
        bitmapToApply.recycle()

        // 4. Prefetch next K=5 older patches from Cold to Warm
        prefetcher.prefetchNextK(undoHistory.toList(), scope)

        return@withContext true
    }

    override suspend fun redo(canvas: Bitmap): Boolean = withContext(historyDispatcher) {
        if (redoStack.isEmpty()) return@withContext false

        val patchToRestore = redoStack.removeLast()
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
            rawLength = undoBitmap.byteCount
        )
        hotStore.add(undoPatch)
        undoHistory.addLast(undoPatch)
        totalRawEquivalentBytes += undoPatch.rawLength.toLong()

        // Apply redo patch
        val bmp = patchToRestore.bitmap
        if (bmp != null) {
            val nativeCanvas = Canvas(canvas)
            nativeCanvas.drawBitmap(bmp, rect.left.toFloat(), rect.top.toFloat(), srcPaint)
            patchToRestore.recycle()
        }

        tierPolicy.enforceBudgets()
        return@withContext true
    }

    private fun resolveBitmapForPatch(patch: Patch, width: Int, height: Int): Bitmap {
        // Case 1: Bitmap is already in RAM (HOT or in middle of COMPRESSING)
        patch.bitmap?.let { return it }

        // Case 2: In RAM compressed (WARM or in middle of WRITING)
        patch.compressedBytes?.let { bytes ->
            return PatchCodec.decompressToBitmap(
                compressedBytes = bytes,
                width = width,
                height = height,
                rawLength = if (patch.rawLength > 0) patch.rawLength else width * height * 4,
                expectedCrc32 = patch.crc32
            )
        }

        // Case 3: On Disk (COLD)
        val payload = coldStore.readPayload(patch)
        return PatchCodec.decompressToBitmap(
            compressedBytes = payload.compressedBytes,
            width = width,
            height = height,
            rawLength = payload.rawLength,
            expectedCrc32 = payload.crc32
        )
    }

    override fun onTrimMemory(level: Int) {
        scope.launch {
            tierPolicy.applyTrimMemory(level)
        }
    }

    suspend fun runOomCleaner(): Long = withContext(historyDispatcher) {
        tierPolicy.runOomCleaner()
    }

    fun updateTierConfig(newConfig: TierConfig) {
        tierConfig = newConfig
        tierPolicy.config = newConfig
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
            totalSteps = undoHistory.size,
            redoSteps = redoStack.size,
            prefetchHits = prefetcher.hits.get(),
            prefetchMisses = prefetcher.misses.get(),
            rawEquivalentBytes = totalRawEquivalentBytes,
            oomCleanerReclaimedBytes = tierPolicy.oomCleanerReclaimedBytes,
            deduplicatedPatches = tierPolicy.deduplicatedPatches,
            infiniteUndoEnabled = tierConfig.infiniteUndo,
            evictionStrategy = tierConfig.evictionStrategy.name
        )
    }

    override fun clear() {
        scope.launch {
            hotStore.clear()
            warmStore.clear()
            coldStore.clear()
            undoHistory.clear()
            totalRawEquivalentBytes = 0L
            tierPolicy.resetSwamMetrics()
            clearRedoStackLocked()
            prefetcher.resetStats()
        }
    }

    private fun clearRedoStackLocked() {
        for (p in redoStack) {
            p.recycle()
        }
        redoStack.clear()
    }
}
