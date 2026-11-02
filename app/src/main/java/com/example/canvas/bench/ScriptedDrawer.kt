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
        Color.BLACK,
        Color.rgb(229, 57, 53),   // Red
        Color.rgb(30, 136, 229),  // Blue
        Color.rgb(67, 160, 71),   // Green
        Color.rgb(255, 179, 0),   // Amber
        Color.rgb(142, 36, 170)   // Purple
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
        // Canvas area = 2048 x 2048 = 4,194,304 px^2
        // Target area: ~84,000 to ~419,000 px^2 -> width/height roughly 290 to 640
        val strokeWidth = 14f + random.nextFloat() * 10f // 14 to 24px

        val boxWidth = 300f + random.nextFloat() * 320f  // 300 to 620px
        val boxHeight = 300f + random.nextFloat() * 320f // 300 to 620px

        val maxOriginX = (canvasWidth - boxWidth).coerceAtLeast(0f)
        val maxOriginY = (canvasHeight - boxHeight).coerceAtLeast(0f)

        val originX = random.nextFloat() * maxOriginX
        val originY = random.nextFloat() * maxOriginY

        val stroke = Stroke(color = color, width = strokeWidth)

        // Anchor start and end to corners of the box to guarantee bounded span
        stroke.addPoint(originX, originY)

        val intermediatePoints = 3 + random.nextInt(4)
        for (i in 1..intermediatePoints) {
            val px = originX + random.nextFloat() * boxWidth
            val py = originY + random.nextFloat() * boxHeight
            stroke.addPoint(px, py)
        }

        stroke.addPoint(originX + boxWidth, originY + boxHeight)
        return stroke
    }

    private fun generateLargeStroke(color: Int): Stroke {
        // Large: changed area spans ~40% to 100% of canvas
        // Target area: ~1,680,000 to ~4,194,000 px^2 -> width/height roughly 1300 to 2048
        val strokeWidth = 80f + random.nextFloat() * 80f // 80 to 160px

        val boxWidth = 1350f + random.nextFloat() * (canvasWidth - 1350f)
        val boxHeight = 1350f + random.nextFloat() * (canvasHeight - 1350f)

        val maxOriginX = (canvasWidth - boxWidth).coerceAtLeast(0f)
        val maxOriginY = (canvasHeight - boxHeight).coerceAtLeast(0f)

        val originX = random.nextFloat() * maxOriginX
        val originY = random.nextFloat() * maxOriginY

        val stroke = Stroke(color = color, width = strokeWidth)

        // Traverses across corners of the bounding area
        stroke.addPoint(originX, originY)

        val intermediatePoints = 8 + random.nextInt(8)
        for (i in 1..intermediatePoints) {
            val px = originX + random.nextFloat() * boxWidth
            val py = originY + random.nextFloat() * boxHeight
            stroke.addPoint(px, py)
        }

        stroke.addPoint(originX + boxWidth, originY + boxHeight)
        return stroke
    }
}
