package com.example.canvas.history

import android.graphics.Bitmap
import com.example.canvas.core.Patch

enum class HistoryMode {
    NAIVE,
    DIRTY_RECT,
    TIERED
}

data class HistoryStats(
    val hotBytes: Long = 0L,
    val warmBytes: Long = 0L,
    val coldBytes: Long = 0L,
    val hotSteps: Int = 0,
    val warmSteps: Int = 0,
    val coldSteps: Int = 0,
    val totalSteps: Int = 0,
    val redoSteps: Int = 0,
    val gcCount: Long = 0L,
    val prefetchHits: Int = 0,
    val prefetchMisses: Int = 0,
    val prefetchLookahead: Int = 0,
    val predecodedPatches: Int = 0,
    /** RAM held by bitmaps decompressed ahead of time for the next undos (Hot form of Warm steps). */
    val predecodedBytes: Long = 0L,
    /** Budgets in force for Hot and Warm (Tiered only), so a display can show how full each tier is. */
    val hotBudgetBytes: Long = 0L,
    val warmBudgetBytes: Long = 0L,
    /** Breakdown of the last undo: disk read, decompression, and drawing the patch (milliseconds). */
    val lastUndoReadMs: Double = 0.0,
    val lastUndoDecompressMs: Double = 0.0,
    val lastUndoApplyMs: Double = 0.0,
    val rawEquivalentBytes: Long = 0L,
    /** Total CPU/IO time spent so far in each step of the pipeline (milliseconds). */
    val costCompressMs: Double = 0.0,
    val costInflateMs: Double = 0.0,
    val costReadMs: Double = 0.0,
    val costWriteMs: Double = 0.0,
    val costRecompressMs: Double = 0.0,
    val costApplyMs: Double = 0.0,
    val oomCleanerReclaimedBytes: Long = 0L,
    val deduplicatedPatches: Int = 0,
    /** Tier moves that reused an existing compressed form instead of compressing again. */
    val reusedEncodings: Int = 0,
    /** Tier moves to Cold that reused a file already on disk instead of writing a new one. */
    val reusedColdFiles: Int = 0,
    /** How many patches entered Warm in each storage format (see CodecSelector), and what choosing cost/saved. */
    val codecSolid: Int = 0,
    val codecRle: Int = 0,
    val codecDelta: Int = 0,
    val codecDeflate: Int = 0,
    val codecSavedBytes: Long = 0L,
    val codecTrialMs: Double = 0.0,
    /** Cold under pressure: events, bytes saved without losing data, strokes that had to be dropped. */
    val coldPressureEvents: Int = 0,
    val coldRescuedBytes: Long = 0L,
    val coldEvictedSteps: Int = 0,
    val diskFreeBytes: Long = 0L,
    val infiniteUndoEnabled: Boolean = true,
    val evictionStrategy: String = "FIFO"
)

interface HistoryManager {
    val mode: HistoryMode

    /** Draws a recorded step onto a canvas; set by the view so redo can replay (merge) steps. */
    var opRenderer: ((android.graphics.Canvas, com.example.canvas.core.StrokeOp) -> Unit)?

    /** Waits until steps that were still moving down a tier in the background have arrived; no-op where there is none. */
    suspend fun settle() {}

    suspend fun commit(before: Patch)
    suspend fun undo(canvas: Bitmap): Boolean
    suspend fun redo(canvas: Bitmap): Boolean
    /** Measured fixed cost of one more patch, in bytes-equivalent; null until measured. */
    fun patchOverheadBytes(): Long? = null

    fun onTrimMemory(level: Int)
    fun stats(): HistoryStats
    fun clear()
}
