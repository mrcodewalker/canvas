package com.example.canvas.history

/** Everything that can happen to a step of the history, as the tracker panel shows it. */
enum class TierEventKind {
    /** A new stroke entered Hot. */
    NEW,
    /** Hot -> Warm: the bitmap was compressed (fast level) into RAM. */
    HOT_TO_WARM,
    /** Warm -> Cold: written to disk, possibly recompressed harder. */
    WARM_TO_COLD,
    /** Cold -> Warm: read back ahead of time by the prefetcher. */
    COLD_TO_WARM,
    /** Warm -> bitmap: decompressed ahead of time so undo only has to draw. */
    PREDECODE,
    /** Cold file recompressed harder to free disk. */
    SQUEEZE,
    /** The stroke was dropped because the disk was full. */
    EVICT,
    /** Cold could not take it, so it stays in Warm. */
    HELD_IN_WARM,
    UNDO,
    REDO
}

/**
 * One transition. [stroke] is the number of the stroke (1 = first stroke drawn), [bytesBefore] and
 * [bytesAfter] are the size of the step before and after the transition (0 when not applicable).
 */
data class TierEvent(
    val timeMs: Long,
    val kind: TierEventKind,
    val stroke: Int,
    val bytesBefore: Long = 0L,
    val bytesAfter: Long = 0L,
    val note: String = "",
    /** How many tiles (patches) of the stroke this line stands for. */
    val tiles: Int = 1
)

/** Where a step lives right now: 0 = Hot, 1 = Warm, 2 = Cold. */
data class TierCell(
    val stroke: Int,
    val tier: Int,
    val bytes: Long,
    /** Warm but already decompressed ahead of time. */
    val ready: Boolean
)

/**
 * Hands transitions to whoever is watching (the tracker panel). Nothing is stored here and nothing is
 * computed while there is no listener, so the tracker costs nothing when it is hidden.
 */
class TierEventLog {
    @Volatile
    var listener: ((TierEvent) -> Unit)? = null

    /** Called after every delivered event (the history uses it to refresh the tier map). */
    @Volatile
    var afterEmit: (() -> Unit)? = null

    fun emit(
        kind: TierEventKind,
        stroke: Int,
        bytesBefore: Long = 0L,
        bytesAfter: Long = 0L,
        note: String = ""
    ) {
        val l = listener ?: return
        l(TierEvent(System.currentTimeMillis(), kind, stroke, bytesBefore, bytesAfter, note))
        afterEmit?.invoke()
    }
}
