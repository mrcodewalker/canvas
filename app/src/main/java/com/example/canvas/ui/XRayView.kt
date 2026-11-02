package com.example.canvas.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.canvas.history.HistoryMode
import kotlin.math.max

/**
 * Visual HUD overlay displaying real-time memory telemetry:
 * - 3 horizontal tier bars for Hot (Red), Warm (Amber), Cold (Cyan)
 * - 60-second scrolling RAM graph (Java Heap + Native Heap)
 * - Counters for Undo/Redo, GC runs, and Prefetch hit-rate
 * - Minimized / Expanded toggle
 */
class XRayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private fun dp(v: Float): Float = v * density
    private fun sp(v: Float): Float = v * density

    // Display State
    var isMinimized: Boolean = false
        private set

    var currentMode: HistoryMode = HistoryMode.TIERED

    private var currentTelemetry: XRayCurrentTelemetry = XRayCurrentTelemetry()
    private var telemetryPoints: List<XRayPoint> = emptyList()

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6101622") // Dark navy translucent
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334E68")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F3A52")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64B5F6")
        textSize = sp(13f)
        isFakeBoldText = true
    }

    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        isFakeBoldText = true
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        textSize = sp(10.5f)
    }

    private val subtleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78909C")
        textSize = sp(9.5f)
    }

    private val hotBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252") // Coral Red
        style = Paint.Style.FILL
    }

    private val warmBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300") // Warm Amber
        style = Paint.Style.FILL
    }

    private val coldBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Cyan
        style = Paint.Style.FILL
    }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.FILL
    }

    private val javaLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Neon Green
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val nativeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C4DFF") // Deep Purple / Violet
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val totalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2200E5FF") // Very soft cyan fill
        style = Paint.Style.FILL
    }

    // Cached geometries
    private val cardRect = RectF()
    private val barRect = RectF()
    private val chartRect = RectF()
    private val pathTotal = Path()
    private val pathJava = Path()
    private val pathNative = Path()

    fun updateTelemetry(tracker: XRayTelemetryTracker, mode: HistoryMode) {
        this.currentTelemetry = tracker.getCurrentTelemetry()
        this.telemetryPoints = tracker.getPoints()
        this.currentMode = mode
        postInvalidate()
    }

    fun toggleMinimize() {
        isMinimized = !isMinimized
        requestLayout()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // If tapped near header or anywhere when minimized, toggle minimize state
            val y = event.y
            if (isMinimized || y <= dp(36f)) {
                toggleMinimize()
                return true
            }
        }
        return true // Consume touch so it doesn't accidentally draw through HUD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = if (isMinimized) {
            dp(38f).toInt()
        } else {
            dp(275f).toInt()
        }
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = dp(8f)
        val radius = dp(10f)

        cardRect.set(padding, padding, w - padding, h - padding)

        // Draw card background
        canvas.drawRoundRect(cardRect, radius, radius, bgPaint)
        canvas.drawRoundRect(cardRect, radius, radius, borderPaint)

        // Header
        val headerY = cardRect.top + dp(18f)
        canvas.drawText("⚡ MEMORY X-RAY", cardRect.left + dp(10f), headerY, titlePaint)

        // Mode badge
        val modeColor = when (currentMode) {
            HistoryMode.NAIVE -> Color.parseColor("#FF5252")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#FFB300")
            HistoryMode.TIERED -> Color.parseColor("#00E676")
        }
        modePaint.color = modeColor
        val modeText = "[${currentMode.name}]"
        canvas.drawText(modeText, cardRect.left + dp(135f), headerY, modePaint)

        // Minimize / Expand indicator
        val toggleText = if (isMinimized) "▼ Expand" else "▲ Minimize"
        canvas.drawText(toggleText, cardRect.right - dp(65f), headerY, subtleTextPaint)

        if (isMinimized) {
            drawMinimizedContent(canvas, cardRect)
        } else {
            drawExpandedContent(canvas, cardRect)
        }
    }

    private fun drawMinimizedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        val totalMb = tel.currentJavaHeapMb + tel.currentNativeHeapMb
        val text = "Total: %.1f MB (H:%.1f | W:%.1f | C:%.1f) | Steps: %d".format(
            totalMb, tel.hotMb, tel.warmMb, tel.coldMb, tel.totalSteps
        )
        canvas.drawText(text, bounds.left + dp(10f), bounds.top + dp(30f), textPaint)
    }

    private fun drawExpandedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        var currentY = bounds.top + dp(34f)

        // 1. Tier Bars (Hot, Warm, Cold)
        val barTotalCapacityMb = max(1f, max(tel.hotMb + tel.warmMb + tel.coldMb, tel.peakHeapMb))
        val barWidthAvailable = bounds.width() - dp(20f)
        val barLeft = bounds.left + dp(10f)
        val barHeight = dp(14f)

        // Hot Bar
        canvas.drawText(
            "HOT: %.1f MiB (%d steps)".format(tel.hotMb, tel.hotSteps),
            barLeft, currentY + dp(10f), textPaint
        )
        currentY += dp(14f)
        barRect.set(barLeft, currentY, barLeft + barWidthAvailable, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4f), dp(4f), barBgPaint)
        val hotFraction = (tel.hotMb / barTotalCapacityMb).coerceIn(0f, 1f)
        if (hotFraction > 0f) {
            barRect.set(barLeft, currentY, barLeft + (barWidthAvailable * hotFraction), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(4f), dp(4f), hotBarPaint)
        }
        currentY += barHeight + dp(6f)

        // Warm Bar
        canvas.drawText(
            "WARM: %.1f MiB (%d steps)".format(tel.warmMb, tel.warmSteps),
            barLeft, currentY + dp(10f), textPaint
        )
        currentY += dp(14f)
        barRect.set(barLeft, currentY, barLeft + barWidthAvailable, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4f), dp(4f), barBgPaint)
        val warmFraction = (tel.warmMb / barTotalCapacityMb).coerceIn(0f, 1f)
        if (warmFraction > 0f) {
            barRect.set(barLeft, currentY, barLeft + (barWidthAvailable * warmFraction), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(4f), dp(4f), warmBarPaint)
        }
        currentY += barHeight + dp(6f)

        // Cold Bar
        canvas.drawText(
            "COLD: %.1f MiB (%d steps)".format(tel.coldMb, tel.coldSteps),
            barLeft, currentY + dp(10f), textPaint
        )
        currentY += dp(14f)
        barRect.set(barLeft, currentY, barLeft + barWidthAvailable, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4f), dp(4f), barBgPaint)
        val coldFraction = (tel.coldMb / barTotalCapacityMb).coerceIn(0f, 1f)
        if (coldFraction > 0f) {
            barRect.set(barLeft, currentY, barLeft + (barWidthAvailable * coldFraction), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(4f), dp(4f), coldBarPaint)
        }
        currentY += barHeight + dp(10f)

        // 2. 60-Second Memory Graph
        val chartLeft = barLeft
        val chartRight = bounds.right - dp(10f)
        val chartTop = currentY
        val chartBottom = chartTop + dp(70f)
        chartRect.set(chartLeft, chartTop, chartRight, chartBottom)

        // Chart background grid
        canvas.drawRect(chartRect, barBgPaint)
        canvas.drawRect(chartRect, gridPaint)
        val midY = (chartTop + chartBottom) / 2f
        canvas.drawLine(chartLeft, midY, chartRight, midY, gridPaint)

        // Draw rolling line graphs
        drawChartLines(canvas, chartRect)

        // Chart labels
        val peakLabel = "Peak: %.1f MB".format(tel.peakHeapMb)
        canvas.drawText(peakLabel, chartLeft + dp(4f), chartTop + dp(10f), subtleTextPaint)
        canvas.drawText("-60s", chartLeft + dp(2f), chartBottom - dp(2f), subtleTextPaint)
        canvas.drawText("Now", chartRight - dp(22f), chartBottom - dp(2f), subtleTextPaint)

        currentY = chartBottom + dp(14f)

        // 3. Stats & Telemetry Counters Row
        val gcText = if (tel.gcCount >= 0) "GC: %d (+%d)".format(tel.gcCount, tel.gcDelta) else "GC: N/A"
        val prefetchText = "Prefetch: %d/%d (%.0f%%)".format(
            tel.prefetchHits,
            tel.prefetchHits + tel.prefetchMisses,
            tel.prefetchHitRate
        )
        val stepsText = "Steps: %d (Redo: %d)".format(tel.totalSteps, tel.redoSteps)

        canvas.drawText(stepsText, barLeft, currentY, textPaint)
        canvas.drawText(gcText, barLeft + dp(115f), currentY, textPaint)
        canvas.drawText(prefetchText, barLeft + dp(185f), currentY, textPaint)
    }

    private fun drawChartLines(canvas: Canvas, rect: RectF) {
        val points = telemetryPoints
        if (points.isEmpty()) return

        val now = points.last().timestampMs
        val windowDuration = 60_000f // 60 seconds
        val windowStart = now - windowDuration

        val maxMb = max(10f, currentTelemetry.peakHeapMb * 1.15f)
        val chartWidth = rect.width()
        val chartHeight = rect.height()

        pathTotal.reset()
        pathJava.reset()
        pathNative.reset()

        var firstPoint = true
        var startX = rect.left

        for (pt in points) {
            val tFrac = ((pt.timestampMs - windowStart) / windowDuration).coerceIn(0f, 1f)
            val px = rect.left + (tFrac * chartWidth)

            val javaFrac = (pt.javaHeapMb / maxMb).coerceIn(0f, 1f)
            val pyJava = rect.bottom - (javaFrac * chartHeight)

            val nativeFrac = (pt.nativeHeapMb / maxMb).coerceIn(0f, 1f)
            val pyNative = rect.bottom - (nativeFrac * chartHeight)

            val totalFrac = (pt.totalHeapMb / maxMb).coerceIn(0f, 1f)
            val pyTotal = rect.bottom - (totalFrac * chartHeight)

            if (firstPoint) {
                startX = px
                pathTotal.moveTo(px, pyTotal)
                pathJava.moveTo(px, pyJava)
                pathNative.moveTo(px, pyNative)
                firstPoint = false
            } else {
                pathTotal.lineTo(px, pyTotal)
                pathJava.lineTo(px, pyJava)
                pathNative.lineTo(px, pyNative)
            }
        }

        // Fill total under-curve
        if (!firstPoint) {
            val lastX = rect.left + (((points.last().timestampMs - windowStart) / windowDuration).coerceIn(0f, 1f) * chartWidth)
            val fillPath = Path(pathTotal).apply {
                lineTo(lastX, rect.bottom)
                lineTo(startX, rect.bottom)
                close()
            }
            canvas.drawPath(fillPath, totalFillPaint)
        }

        // Draw curves
        canvas.drawPath(pathJava, javaLinePaint)
        canvas.drawPath(pathNative, nativeLinePaint)
    }
}
