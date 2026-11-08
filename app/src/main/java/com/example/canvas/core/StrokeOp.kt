package com.example.canvas.core

/**
 * What a history step drew, kept next to its "before" pixels so the step can be *replayed* on top of a
 * canvas that has changed since (redo with merge). Either a stroke or a full-canvas fill.
 *
 * Every step of an unlimited history keeps one of these, so it is stored compactly: the points live in one
 * `FloatArray` (8 bytes per point) instead of a list of objects (about 32 bytes per point), and the
 * [Stroke] is rebuilt only when a replay actually happens. All tiles of one stroke share one instance.
 */
class StrokeOp private constructor(
    private val color: Int,
    private val width: Float,
    private val xy: FloatArray?,
    val fillColor: Int?
) {
    constructor(stroke: Stroke? = null, fillColor: Int? = null) : this(
        stroke?.color ?: 0,
        stroke?.width ?: 0f,
        stroke?.let { s -> FloatArray(s.points.size * 2).also { a ->
            s.points.forEachIndexed { i, p -> a[2 * i] = p.x; a[2 * i + 1] = p.y }
        } },
        fillColor
    )

    /** The stroke to replay, rebuilt from the compact form; null for a fill. */
    val stroke: Stroke?
        get() {
            val a = xy ?: return null
            val pts = ArrayList<StrokePoint>(a.size / 2)
            for (i in 0 until a.size / 2) pts.add(StrokePoint(a[2 * i], a[2 * i + 1]))
            return Stroke(color, width, pts)
        }

    /** RAM taken by the recorded points. */
    val approxBytes: Long get() = (xy?.size ?: 0) * 4L + 24L
}
