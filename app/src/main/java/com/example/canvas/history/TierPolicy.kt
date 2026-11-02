package com.example.canvas.history

import android.content.ComponentCallbacks2
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.Dispatchers
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

data class TierConfig(
    val hotBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val warmBudgetBytes: Long = (Runtime.getRuntime().maxMemory() * 0.15).toLong(),
    val coldMaxBytes: Long = 500L * 1024 * 1024,
    val coldMaxSteps: Int = 1000,
    val compressionLevel: Int = 1,
    val infiniteUndo: Boolean = false,
    val evictionStrategy: SwamEvictionStrategy = SwamEvictionStrategy.FIFO
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
    var onColdEvicted: ((Patch) -> Unit)? = null
) {
    var oomCleanerReclaimedBytes: Long = 0L
        private set

    var deduplicatedPatches: Int = 0
        private set

    fun resetSwamMetrics() {
        oomCleanerReclaimedBytes = 0L
        deduplicatedPatches = 0
    }

    /**
     * Selects the next victim patch in HotStore according to [config.evictionStrategy].
     * - FIFO: picks oldest patch.
     * - SWAM_COST_AWARE: estimates eviction benefit = sizeBytes * ageRank so large older
     *   patches are compressed first while keeping tiny recent patches hot for O(1) undo.
     */
    private fun selectHotVictim(): Patch? {
        if (config.evictionStrategy == SwamEvictionStrategy.FIFO) {
            return hotStore.peekOldest()
        }
        val allHot = hotStore.getAll()
        if (allHot.isEmpty()) return null
        val total = allHot.size
        var bestPatch: Patch = allHot[0]
        var bestScore = -1.0
        for (i in allHot.indices) {
            val candidate = allHot[i]
            // Older patches have higher ageWeight (fromtotal down to 1)
            val ageWeight = (total - i).toDouble()
            // Eviction score favors freeing larger RAM footprints weighted by age
            val score = candidate.sizeBytes.coerceAtLeast(1024L).toDouble() * ageWeight
            if (score > bestScore) {
                bestScore = score
                bestPatch = candidate
            }
        }
        return bestPatch
    }

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
            val victimHot = selectHotVictim() ?: break
            demoteHotToWarm(victimHot)
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

        // 3. Enforce Cold limits (Skipped when Infinite Undo is enabled!)
        if (!config.infiniteUndo) {
            while ((coldStore.totalBytes > config.coldMaxBytes || coldStore.count > config.coldMaxSteps) && coldStore.count > 0) {
                val evicted = coldStore.removeOldest()
                if (evicted != null) {
                    onColdEvicted?.invoke(evicted)
                }
            }
        }
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
                // Severe: Push all Hot down to Warm, reduce Warm to 50%, and run SWAM OOM Cleaner
                enforceBudgets(
                    effectiveHotBudget = 0L,
                    effectiveWarmBudget = (config.warmBudgetBytes * 0.5).toLong()
                )
                runOomCleaner()
            }
            // Level 60 (MODERATE) or Level 10 (RUNNING_LOW)
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Moderate: Reduce Hot to 25% and run SWAM OOM Cleaner
                enforceBudgets(
                    effectiveHotBudget = (config.hotBudgetBytes * 0.25).toLong(),
                    effectiveWarmBudget = config.warmBudgetBytes
                )
                runOomCleaner()
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
