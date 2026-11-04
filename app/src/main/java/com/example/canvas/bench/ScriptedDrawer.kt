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
}
