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
    val prefetchMisses: Int = 0
)

interface HistoryManager {
    val mode: HistoryMode
    suspend fun commit(before: Patch)
    suspend fun undo(canvas: Bitmap): Boolean
    suspend fun redo(canvas: Bitmap): Boolean
    fun onTrimMemory(level: Int)
    fun stats(): HistoryStats
    fun clear()
}
