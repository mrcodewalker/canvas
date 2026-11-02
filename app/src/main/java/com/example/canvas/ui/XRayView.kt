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
 * Premium Telemetry HUD overlay displaying real-time memory dynamics:
 * - 3 glowing horizontal bars for Hot (Coral Red), Warm (Amber Gold), Cold (Cyan Blue)
 * - 60-second scrolling oscilloscope RAM graph (Java Heap + Native Heap)
 * - Live counters: Steps (Undo/Redo), ART GC runs (+ delta), and Prefetch hit-rate
 * - Smooth Collapse / Expand toggle and Close [X] action
 */
class XRayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private fun dp(v: Float): Float = v * density
    private fun sp(v: Float): Float = v * density

    // State
    var isMinimized: Boolean = false
        private set

    var currentMode: HistoryMode = HistoryMode.TIERED

    var onCloseClicked: (() -> Unit)? = null

    private var currentTelemetry: XRayCurrentTelemetry = XRayCurrentTelemetry()
    private var telemetryPoints: List<XRayPoint> = emptyList()

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE0B0F19") // Deep slate semi-translucent glass
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A374A")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val pulseDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val modeBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }

    private val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10.5f)
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9")
        textSize = sp(10.5f)
        isFakeBoldText = true
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = sp(9.5f)
    }

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }

    private val hotBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F43F5E") // Vibrant Coral Red
        style = Paint.Style.FILL
    }

    private val warmBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Rich Amber Gold
        style = Paint.Style.FILL
    }

    private val coldBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06B6D4") // Cyan Neon
        style = Paint.Style.FILL
    }

    private val chartBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#070A10")
        style = Paint.Style.FILL
    }

    private val chartGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val javaLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Emerald Green
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val nativeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#818CF8") // Indigo Violet
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val totalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A06B6D4")
        style = Paint.Style.FILL
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#161E2E")
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A374A")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val closeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F87171")
        textSize = sp(12f)
        isFakeBoldText = true
    }

    // Geometries
    private val cardRect = RectF()
    private val barRect = RectF()
    private val chartRect = RectF()
    private val badgeRect = RectF()
    private val toggleBtnRect = RectF()
    private val closeBtnRect = RectF()
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
        // If touch is outside the card bounds, pass it through to the canvas underneath!
        if (!cardRect.contains(event.x, event.y)) {
            return false
        }
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            // 1. Check Close Button tap
            if (closeBtnRect.contains(x, y)) {
                onCloseClicked?.invoke()
                return true
            }

            // 2. Check Toggle Minimize tap or Header tap
            if (toggleBtnRect.contains(x, y) || isMinimized || y <= cardRect.top + dp(38f)) {
                toggleMinimize()
                return true
            }
        }
        return true // Touch is inside the HUD card, consume it
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = if (isMinimized) {
            dp(42f).toInt()
        } else {
            dp(245f).toInt()
        }
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = dp(4f)
        val radius = dp(14f)

        cardRect.set(padding, padding, w - padding, h - padding)

        // Draw card background & subtle glow border
        canvas.drawRoundRect(cardRect, radius, radius, bgPaint)
        canvas.drawRoundRect(cardRect, radius, radius, borderPaint)

        // Header elements
        val headerY = cardRect.top + dp(22f)
        val startX = cardRect.left + dp(12f)

        // Pulsing live indicator dot
        canvas.drawCircle(startX + dp(4f), headerY - dp(4f), dp(3.5f), pulseDotPaint)

        // Title
        canvas.drawText("CHRONOS X-RAY", startX + dp(14f), headerY, titlePaint)

        // Mode badge pill
        val modeColor = when (currentMode) {
            HistoryMode.NAIVE -> Color.parseColor("#F43F5E")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#F59E0B")
            HistoryMode.TIERED -> Color.parseColor("#10B981")
        }
        modePaint.color = modeColor
        val modeText = "[${currentMode.name}]"
        val titleWidth = titlePaint.measureText("CHRONOS X-RAY")
        val badgeLeft = startX + dp(20f) + titleWidth
        val badgeW = modePaint.measureText(modeText) + dp(12f)
        badgeRect.set(badgeLeft, headerY - dp(14f), badgeLeft + badgeW, headerY + dp(4f))
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), modeBadgeBgPaint)
        canvas.drawText(modeText, badgeLeft + dp(6f), headerY - dp(1f), modePaint)

        // Close Button [X] at top-right
        val closeW = dp(24f)
        val closeH = dp(24f)
        val closeRight = cardRect.right - dp(8f)
        val closeLeft = closeRight - closeW
        val closeTop = headerY - dp(14f)
        val closeBottom = closeTop + closeH
        closeBtnRect.set(closeLeft, closeTop, closeRight, closeBottom)
        canvas.drawRoundRect(closeBtnRect, dp(6f), dp(6f), modeBadgeBgPaint)
        val xTextW = closeTextPaint.measureText("✕")
        canvas.drawText("✕", closeLeft + (closeW - xTextW) / 2f, headerY - dp(1f), closeTextPaint)

        // Toggle Minimize / Expand Button
        val toggleText = if (isMinimized) "▼ Mở rộng" else "▲ Thu gọn"
        val toggleW = btnTextPaint.measureText(toggleText) + dp(14f)
        val toggleH = dp(24f)
        val toggleRight = closeLeft - dp(6f)
        val toggleLeft = toggleRight - toggleW
        toggleBtnRect.set(toggleLeft, closeTop, toggleRight, closeBottom)
        canvas.drawRoundRect(toggleBtnRect, dp(6f), dp(6f), modeBadgeBgPaint)
        canvas.drawText(toggleText, toggleLeft + dp(7f), headerY - dp(1f), btnTextPaint)

        if (isMinimized) {
            drawMinimizedContent(canvas, cardRect)
        } else {
            drawExpandedContent(canvas, cardRect)
        }
    }

    private fun drawMinimizedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        val totalMb = tel.currentJavaHeapMb + tel.currentNativeHeapMb
        val text = "RAM: %.1f MB (H:%.1f | W:%.1f | C:%.1f MB) • %d steps".format(
            totalMb, tel.hotMb, tel.warmMb, tel.coldMb, tel.totalSteps
        )
        canvas.drawText(text, bounds.left + dp(12f), bounds.top + dp(32f), labelPaint)
    }

    private fun drawExpandedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        var currentY = bounds.top + dp(38f)

        val availableWidth = bounds.width() - dp(24f)
        val left = bounds.left + dp(12f)
        val barHeight = dp(10f)
        val barCapacityMb = max(1f, max(tel.hotMb + tel.warmMb + tel.coldMb, tel.peakHeapMb))

        // 1. Three Tier Bars (Hot, Warm, Cold)
        // Hot Bar
        canvas.drawText("🔥 HOT (Bitmap • O(1))", left, currentY + dp(8f), labelPaint)
        val hotValText = "%.2f MiB (%d steps)".format(tel.hotMb, tel.hotSteps)
        val hotValWidth = subLabelPaint.measureText(hotValText)
        canvas.drawText(hotValText, bounds.right - dp(12f) - hotValWidth, currentY + dp(8f), subLabelPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(5f), dp(5f), trackBgPaint)
        val hotFrac = (tel.hotMb / barCapacityMb).coerceIn(0f, 1f)
        if (hotFrac > 0f) {
            barRect.set(left, currentY, left + (availableWidth * hotFrac), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(5f), dp(5f), hotBarPaint)
        }
        currentY += barHeight + dp(5f)

        // Warm Bar
        canvas.drawText("⚡ WARM (Deflate • O(logN))", left, currentY + dp(8f), labelPaint)
        val warmValText = "%.2f MiB (%d steps)".format(tel.warmMb, tel.warmSteps)
        val warmValWidth = subLabelPaint.measureText(warmValText)
        canvas.drawText(warmValText, bounds.right - dp(12f) - warmValWidth, currentY + dp(8f), subLabelPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(5f), dp(5f), trackBgPaint)
        val warmFrac = (tel.warmMb / barCapacityMb).coerceIn(0f, 1f)
        if (warmFrac > 0f) {
            barRect.set(left, currentY, left + (availableWidth * warmFrac), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(5f), dp(5f), warmBarPaint)
        }
        currentY += barHeight + dp(5f)

        // Cold Bar
        canvas.drawText("❄️ COLD (Disk • O(Disk))", left, currentY + dp(8f), labelPaint)
        val coldValText = "%.2f MiB (%d steps)".format(tel.coldMb, tel.coldSteps)
        val coldValWidth = subLabelPaint.measureText(coldValText)
        canvas.drawText(coldValText, bounds.right - dp(12f) - coldValWidth, currentY + dp(8f), subLabelPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(5f), dp(5f), trackBgPaint)
        val coldFrac = (tel.coldMb / barCapacityMb).coerceIn(0f, 1f)
        if (coldFrac > 0f) {
            barRect.set(left, currentY, left + (availableWidth * coldFrac), currentY + barHeight)
            canvas.drawRoundRect(barRect, dp(5f), dp(5f), coldBarPaint)
        }
        currentY += barHeight + dp(8f)

        // 2. 60-Second Oscilloscope Graph
        val chartTop = currentY
        val chartBottom = chartTop + dp(56f)
        chartRect.set(left, chartTop, bounds.right - dp(12f), chartBottom)

        canvas.drawRoundRect(chartRect, dp(6f), dp(6f), chartBgPaint)
        canvas.drawRoundRect(chartRect, dp(6f), dp(6f), chartGridPaint)
        val midY = (chartTop + chartBottom) / 2f
        canvas.drawLine(left, midY, bounds.right - dp(12f), midY, chartGridPaint)

        drawChartLines(canvas, chartRect)

        // Graph annotations
        val peakLabel = "Peak: %.1f MB".format(tel.peakHeapMb)
        canvas.drawText(peakLabel, left + dp(6f), chartTop + dp(12f), subLabelPaint)
        canvas.drawText("-60s", left + dp(6f), chartBottom - dp(4f), subLabelPaint)
        canvas.drawText("Now", bounds.right - dp(36f), chartBottom - dp(4f), subLabelPaint)

        currentY = chartBottom + dp(10f)

        // 3. Bottom Pill Counters
        val pillWidth = (availableWidth - dp(12f)) / 3f
        val pillHeight = dp(20f)

        // Pill 1: Steps
        badgeRect.set(left, currentY, left + pillWidth, currentY + pillHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBorderPaint)
        val stepsText = "Steps: %d (%d redo)".format(tel.totalSteps, tel.redoSteps)
        canvas.drawText(stepsText, left + dp(6f), currentY + dp(14f), subLabelPaint)

        // Pill 2: GC
        val p2Left = left + pillWidth + dp(6f)
        badgeRect.set(p2Left, currentY, p2Left + pillWidth, currentY + pillHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBorderPaint)
        val gcText = if (tel.gcCount >= 0) "GC: %d (+%d)".format(tel.gcCount, tel.gcDelta) else "GC: N/A"
        canvas.drawText(gcText, p2Left + dp(6f), currentY + dp(14f), subLabelPaint)

        // Pill 3: Prefetch
        val p3Left = p2Left + pillWidth + dp(6f)
        badgeRect.set(p3Left, currentY, p3Left + pillWidth, currentY + pillHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBorderPaint)
        val prefetchText = "Prefetch: %.0f%%".format(tel.prefetchHitRate)
        canvas.drawText(prefetchText, p3Left + dp(6f), currentY + dp(14f), subLabelPaint)
    }

    private fun drawChartLines(canvas: Canvas, rect: RectF) {
        val points = telemetryPoints
        if (points.isEmpty()) return

        val now = points.last().timestampMs
        val windowDuration = 60_000f
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
