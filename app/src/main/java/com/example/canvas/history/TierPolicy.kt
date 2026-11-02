package com.example.canvas.history

import android.content.ComponentCallbacks2
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TierConfig(
    val hotBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val warmBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val coldMaxBytes: Long = 500L * 1024 * 1024,
    val coldMaxSteps: Int = 1000,
    val compressionLevel: Int = 1
)

/**
 * Manages tiered degradation policies across Hot, Warm, and Cold stores.
 * Guarantees zero data loss during transitions: old representations are kept until
 * new representations are completely finalized.
 */
class TierPolicy(
    var config: TierConfig,
    private val hotStore: HotStore,
    private val warmStore: WarmStore,
    private val coldStore: ColdStore
) {
    /**
     * Enforces memory budgets for Hot, Warm, and Cold stores, demoting items
     * asynchronously from Hot -> Warm and Warm -> Cold as needed.
     */
    suspend fun enforceBudgets(
        effectiveHotBudget: Long = config.hotBudgetBytes,
        effectiveWarmBudget: Long = config.warmBudgetBytes
    ) {
        // 1. Enforce Hot budget -> demote excess to Warm
        val maxHotAttempts = hotStore.count
        var hotAttempts = 0
        while (hotStore.totalBytes > effectiveHotBudget && hotStore.count > 0 && hotAttempts < maxHotAttempts) {
            val oldestHot = hotStore.peekOldest() ?: break
            demoteHotToWarm(oldestHot)
            hotAttempts++
        }

        // 2. Enforce Warm budget -> demote excess to Cold
        val maxWarmAttempts = warmStore.count
        var warmAttempts = 0
        while (warmStore.totalBytes > effectiveWarmBudget && warmStore.count > 0 && warmAttempts < maxWarmAttempts) {
            val oldestWarm = warmStore.peekOldest() ?: break
            demoteWarmToCold(oldestWarm)
            warmAttempts++
        }

        // 3. Enforce Cold limits
        while ((coldStore.totalBytes > config.coldMaxBytes || coldStore.count > config.coldMaxSteps) && coldStore.count > 0) {
            coldStore.removeOldest()
        }
    }

    /**
     * Demotes a patch from HOT to WARM:
     * - State transitions: HOT -> COMPRESSING -> WARM.
     * - Bitmap remains accessible until compression completes.
     */
    suspend fun demoteHotToWarm(patch: Patch) {
        val bmp = patch.bitmap
        patch.state = PatchState.COMPRESSING

        // Remove from HotStore using the existing Hot sizeBytes
        hotStore.remove(patch)

        if (bmp != null) {
            val compressed = withContext(Dispatchers.Default) {
                PatchCodec.compressBitmap(bmp, config.compressionLevel)
            }
            patch.compressedBytes = compressed.compressedBytes
            patch.rawLength = compressed.rawLength
            patch.crc32 = compressed.crc32
            patch.sizeBytes = compressed.compressedBytes.size.toLong()
        } else if (patch.compressedBytes != null) {
            patch.sizeBytes = patch.compressedBytes!!.size.toLong()
        }

        patch.state = PatchState.WARM
        warmStore.add(patch)

        // Now safe to recycle the bitmap
        patch.recycle()
    }

    /**
     * Demotes a patch from WARM to COLD:
     * - State transitions: WARM -> WRITING -> COLD.
     * - Compressed bytes remain in RAM until atomic file write completes.
     */
    suspend fun demoteWarmToCold(patch: Patch) {
        val bytes = patch.compressedBytes ?: ByteArray(0)
        patch.state = PatchState.WRITING

        // Remove from WarmStore using existing Warm sizeBytes
        warmStore.remove(patch)

        val file = withContext(Dispatchers.IO) {
            coldStore.writeToFile(patch, bytes)
        }

        patch.filePath = file.absolutePath
        patch.sizeBytes = file.length()
        patch.state = PatchState.COLD

        coldStore.add(patch)

        // Free compressed bytes from RAM
        patch.compressedBytes = null
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
                // Severe: Push all Hot down to Warm, reduce Warm to 50%
                enforceBudgets(
                    effectiveHotBudget = 0L,
                    effectiveWarmBudget = (config.warmBudgetBytes * 0.5).toLong()
                )
            }
            // Level 60 (MODERATE) or Level 10 (RUNNING_LOW)
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Moderate: Reduce Hot to 25%
                enforceBudgets(
                    effectiveHotBudget = (config.hotBudgetBytes * 0.25).toLong(),
                    effectiveWarmBudget = config.warmBudgetBytes
                )
            }
            // Level 20 (UI_HIDDEN), Level 40 (BACKGROUND), or Level 5 (RUNNING_MODERATE)
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // Mild: Reduce Hot to 50%
                enforceBudgets(
                    effectiveHotBudget = (config.hotBudgetBytes * 0.5).toLong(),
                    effectiveWarmBudget = config.warmBudgetBytes
                )
            }
        }
    }
}
