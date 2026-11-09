package com.example.canvas.bench

import android.graphics.Color
import com.example.canvas.core.Stroke
import java.util.Random
import kotlin.math.sqrt

/**
 * Deterministic scripted drawer that generates reproducible strokes for benchmarking.
 * Uses a fixed seed Random instance so repeated runs with identical parameters produce identical strokes.
 */
class ScriptedDrawer(
    val seed: Long = 42L,
    val canvasWidth: Int = 2048,
    val canvasHeight: Int = 2048
) {
    private val random = Random(seed)

    private val colorPalette = intArrayOf(
        Color.rgb(15, 23, 42),    // Deep Slate Ink
        Color.rgb(0, 184, 212),   // Cyber Cyan
        Color.rgb(255, 64, 129),  // Vibrant Coral Rose
        Color.rgb(124, 77, 255),  // Electric Indigo
        Color.rgb(0, 200, 83),    // Radiant Emerald
        Color.rgb(255, 145, 0),   // Warm Amber Gold
        Color.rgb(41, 98, 255)    // Royal Cobalt Blue
    )

    /**
     * Resets the pseudo-random generator back to the initial seed.
     */
    fun reset() {
        random.setSeed(seed)
    }

    /**
     * Generates a sequence of [count] strokes of the given [type].
     */
    fun generateStrokes(count: Int, type: StrokeType): List<Stroke> {
        val strokes = ArrayList<Stroke>(count)
        for (i in 0 until count) {
            strokes.add(nextStroke(type))
        }
        return strokes
    }

    /**
     * Generates the next stroke according to [type].
     */
    fun nextStroke(type: StrokeType): Stroke {
        val color = colorPalette[random.nextInt(colorPalette.size)]

        return when (type) {
            StrokeType.SMALL -> generateSmallStroke(color)
            StrokeType.LARGE -> generateLargeStroke(color)
            StrokeType.HOLLOW -> generateHollowFrame(color)
            StrokeType.MIXED -> if (random.nextInt(3) == 0) generateLargeStroke(color) else generateSmallStroke(color)
            StrokeType.DIVERSE -> generateDiverse(color)
        }
    }

    private fun generateSmallStroke(color: Int): Stroke {
        // Small: changed area spans ~2% to 10% of canvas
        val strokeWidth = 14f + random.nextFloat() * 10f // 14 to 24px

        val boxWidth = 300f + random.nextFloat() * 320f  // 300 to 620px
        val boxHeight = 300f + random.nextFloat() * 320f // 300 to 620px

        val maxOriginX = (canvasWidth - boxWidth).coerceAtLeast(0f)
        val maxOriginY = (canvasHeight - boxHeight).coerceAtLeast(0f)

        val originX = random.nextFloat() * maxOriginX
        val originY = random.nextFloat() * maxOriginY

        val stroke = Stroke(color = color, width = strokeWidth)
        populateSmoothCalligraphicCurve(
            stroke = stroke,
            originX = originX,
            originY = originY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            steps = 24
        )
        return stroke
    }

    private fun generateHollowFrame(color: Int): Stroke {
        val strokeWidth = 24f + random.nextFloat() * 16f
        val boxWidth = 1350f + random.nextFloat() * (canvasWidth - 1350f)
        val boxHeight = 1350f + random.nextFloat() * (canvasHeight - 1350f)
        val left = random.nextFloat() * (canvasWidth - boxWidth).coerceAtLeast(0f)
        val top = random.nextFloat() * (canvasHeight - boxHeight).coerceAtLeast(0f)
        val right = left + boxWidth
        val bottom = top + boxHeight
        val stroke = Stroke(color = color, width = strokeWidth)
        // walk the four sides, a point every ~60px so the renderer draws straight edges
        fun side(x0: Float, y0: Float, x1: Float, y1: Float) {
            val n = (kotlin.math.max(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)) / 60f).toInt().coerceAtLeast(1)
            for (i in 0..n) {
                val t = i.toFloat() / n
                stroke.addPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            }
        }
        side(left, top, right, top)
        side(right, top, right, bottom)
        side(right, bottom, left, bottom)
        side(left, bottom, left, top)
        return stroke
    }

    private fun generateLargeStroke(color: Int): Stroke {
        // Large: changed area spans ~40% to 100% of canvas
        val strokeWidth = 42f + random.nextFloat() * 44f // Smooth ribbon brush 42 to 86px

        val boxWidth = 1350f + random.nextFloat() * (canvasWidth - 1350f)
        val boxHeight = 1350f + random.nextFloat() * (canvasHeight - 1350f)

        val maxOriginX = (canvasWidth - boxWidth).coerceAtLeast(0f)
        val maxOriginY = (canvasHeight - boxHeight).coerceAtLeast(0f)

        val originX = random.nextFloat() * maxOriginX
        val originY = random.nextFloat() * maxOriginY

        val stroke = Stroke(color = color, width = strokeWidth)
        populateSmoothCalligraphicCurve(
            stroke = stroke,
            originX = originX,
            originY = originY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            steps = 36
        )
        return stroke
    }

    /**
     * Generates a silky-smooth calligraphic S-curve / Lissajous wave inside [originX..originX+boxWidth]
     * and [originY..originY+boxHeight], anchored at the box corners so the exact bounding-box area
     * ratio is preserved while looking like real digital artwork.
     */
    private fun populateSmoothCalligraphicCurve(
        stroke: Stroke,
        originX: Float,
        originY: Float,
        boxWidth: Float,
        boxHeight: Float,
        steps: Int
    ) {
        val flipDiagonal = random.nextBoolean()
        val waves = 1.5 + random.nextDouble() * 1.5
        val ampX = boxWidth * (0.22f + random.nextFloat() * 0.20f)
        val ampY = boxHeight * (0.22f + random.nextFloat() * 0.20f)

        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val envelope = kotlin.math.sin(t * Math.PI).toFloat()
            val osc = kotlin.math.sin(t * Math.PI * waves).toFloat()
            val offset = envelope * osc

            val baseX = originX + boxWidth * t
            val baseY = if (flipDiagonal) {
                originY + boxHeight * (1f - t)
            } else {
                originY + boxHeight * t
            }

            val px = (baseX + offset * ampX).coerceIn(originX, originX + boxWidth)
            val py = (baseY - offset * ampY).coerceIn(originY, originY + boxHeight)
            stroke.addPoint(px, py)
        }
    }

    // ------------------------------------------------------------------------------------------ DIVERSE

    /** Kinds of strokes a person makes; the weights give a realistic mix (many lines and sketches, fewer huge bands). */
    private enum class Kind(val weight: Int) {
        AXIS_LINE(14), ANGLE_LINE(12), HATCHING(11), CIRCLE(10), SPIRAL(5), WALK(14), BAND(9), RIBBON(10), SKETCH(10), ERASE(5)
    }

    private fun pickKind(): Kind {
        var r = random.nextInt(Kind.values().sumOf { it.weight })
        for (k in Kind.values()) {
            if (r < k.weight) return k
            r -= k.weight
        }
        return Kind.SKETCH
    }

    private fun anyColor(base: Int): Int =
        if (random.nextInt(4) == 0) Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.35f + random.nextFloat() * 0.6f, 0.45f + random.nextFloat() * 0.55f)) else base

    private fun cx(v: Float) = v.coerceIn(0f, (canvasWidth - 1).toFloat())
    private fun cy(v: Float) = v.coerceIn(0f, (canvasHeight - 1).toFloat())

    /** Points every ~[step] px along a straight segment, so the renderer draws it as a straight edge. */
    private fun segment(s: Stroke, x0: Float, y0: Float, x1: Float, y1: Float, step: Float = 40f) {
        val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        val n = (len / step).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val t = i.toFloat() / n
            s.addPoint(cx(x0 + (x1 - x0) * t), cy(y0 + (y1 - y0) * t))
        }
    }

    private fun generateDiverse(baseColor: Int): Stroke {
        val kind = pickKind()
        val color = if (kind == Kind.ERASE) Color.WHITE else anyColor(baseColor)
        val w = canvasWidth.toFloat()
        val h = canvasHeight.toFloat()
        return when (kind) {
            Kind.AXIS_LINE -> {
                // a ruler-straight horizontal or vertical line, short to full length
                val s = Stroke(color = color, width = 8f + random.nextFloat() * 52f)
                if (random.nextBoolean()) {
                    val len = 250f + random.nextFloat() * (w - 250f)
                    val x0 = random.nextFloat() * (w - len)
                    val y = random.nextFloat() * h
                    segment(s, x0, y, x0 + len, y + (random.nextFloat() - 0.5f) * 12f)
                } else {
                    val len = 250f + random.nextFloat() * (h - 250f)
                    val y0 = random.nextFloat() * (h - len)
                    val x = random.nextFloat() * w
                    segment(s, x, y0, x + (random.nextFloat() - 0.5f) * 12f, y0 + len)
                }
                s
            }
            Kind.ANGLE_LINE -> {
                val s = Stroke(color = color, width = 6f + random.nextFloat() * 40f)
                val ang = random.nextDouble() * Math.PI
                val len = 200f + random.nextFloat() * 1400f
                val x0 = random.nextFloat() * w
                val y0 = random.nextFloat() * h
                segment(s, x0, y0, x0 + (kotlin.math.cos(ang) * len).toFloat(), y0 + (kotlin.math.sin(ang) * len).toFloat())
                s
            }
            Kind.HATCHING -> {
                // back and forth strokes close together: dense, many colour changes per pixel row
                val s = Stroke(color = color, width = 6f + random.nextFloat() * 10f)
                val bw = 220f + random.nextFloat() * 700f
                val bh = 220f + random.nextFloat() * 700f
                val x0 = random.nextFloat() * (w - bw).coerceAtLeast(1f)
                val y0 = random.nextFloat() * (h - bh).coerceAtLeast(1f)
                val gap = 16f + random.nextFloat() * 28f
                val diagonal = random.nextBoolean()
                var y = y0
                var left = true
                while (y < y0 + bh) {
                    val xa = if (left) x0 else x0 + bw
                    val xb = if (left) x0 + bw else x0
                    segment(s, xa, y, xb, if (diagonal) y + gap else y, step = 50f)
                    y += gap
                    left = !left
                }
                s
            }
            Kind.CIRCLE -> {
                val s = Stroke(color = color, width = 6f + random.nextFloat() * 36f)
                val r = 60f + random.nextFloat() * 640f
                val x = r + random.nextFloat() * (w - 2 * r).coerceAtLeast(1f)
                val y = r + random.nextFloat() * (h - 2 * r).coerceAtLeast(1f)
                val n = (r / 8f).toInt().coerceIn(24, 120)
                for (i in 0..n) {
                    val a = 2.0 * Math.PI * i / n
                    s.addPoint(cx(x + (kotlin.math.cos(a) * r).toFloat()), cy(y + (kotlin.math.sin(a) * r).toFloat()))
                }
                s
            }
            Kind.SPIRAL -> {
                val s = Stroke(color = color, width = 5f + random.nextFloat() * 18f)
                val r = 120f + random.nextFloat() * 520f
                val x = r + random.nextFloat() * (w - 2 * r).coerceAtLeast(1f)
                val y = r + random.nextFloat() * (h - 2 * r).coerceAtLeast(1f)
                val turns = 2 + random.nextInt(3)
                val n = turns * 48
                for (i in 0..n) {
                    val t = i.toFloat() / n
                    val a = 2.0 * Math.PI * turns * t
                    s.addPoint(cx(x + (kotlin.math.cos(a) * r * t).toFloat()), cy(y + (kotlin.math.sin(a) * r * t).toFloat()))
                }
                s
            }
            Kind.WALK -> {
                // hand-written wandering line: the heading turns a little at every step
                val s = Stroke(color = color, width = 5f + random.nextFloat() * 30f)
                var x = random.nextFloat() * w
                var y = random.nextFloat() * h
                var heading = random.nextDouble() * 2 * Math.PI
                val steps = 30 + random.nextInt(70)
                val stride = 14f + random.nextFloat() * 30f
                s.addPoint(cx(x), cy(y))
                repeat(steps) {
                    heading += (random.nextDouble() - 0.5) * 1.1
                    x += (kotlin.math.cos(heading) * stride).toFloat()
                    y += (kotlin.math.sin(heading) * stride).toFloat()
                    if (x < 0f || x > w) { heading = Math.PI - heading; x = cx(x) }
                    if (y < 0f || y > h) { heading = -heading; y = cy(y) }
                    s.addPoint(x, y)
                }
                s
            }
            Kind.BAND -> {
                // a wide flat brush dragged across: a big area of one colour
                val s = Stroke(color = color, width = 110f + random.nextFloat() * 130f)
                if (random.nextBoolean()) {
                    val y = random.nextFloat() * h
                    segment(s, random.nextFloat() * w * 0.3f, y, w * (0.6f + random.nextFloat() * 0.4f), y + (random.nextFloat() - 0.5f) * 200f, step = 80f)
                } else {
                    val x = random.nextFloat() * w
                    segment(s, x, random.nextFloat() * h * 0.3f, x + (random.nextFloat() - 0.5f) * 200f, h * (0.6f + random.nextFloat() * 0.4f), step = 80f)
                }
                s
            }
            Kind.RIBBON -> generateLargeStroke(color)
            Kind.SKETCH -> generateSmallStroke(color)
            Kind.ERASE -> {
                val s = Stroke(color = color, width = 50f + random.nextFloat() * 90f)
                val x = random.nextFloat() * w
                val y = random.nextFloat() * h
                segment(s, x, y, x + (random.nextFloat() - 0.5f) * 700f, y + (random.nextFloat() - 0.5f) * 700f, step = 50f)
                s
            }
        }
    }
}
