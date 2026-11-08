package com.example.canvas.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.canvas.history.HistoryMode
import kotlin.math.max

/**
 * State-of-the-art telemetry HUD overlay displaying real-time memory dynamics:
 * - 3 glowing high-precision gauge bars: Hot (Coral), Warm (Amber Gold), Cold (Cyan Neon)
 * - 60-second real-time oscilloscope RAM waveform with gradient fill & peak memory tag
 * - 3 sleek glass cards for Steps, GC runs, and Prefetch hit-rate
 * - Smooth Collapse to micro status capsule & Close action
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
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4090E17") // Deep obsidian glass
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#253449")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }

    private val topGlowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2553A")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    // Pulsing radar dot
    private val radarOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#204FA38C")
        style = Paint.Style.FILL
    }
    private val radarInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FA38C")
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FA6C9")
        textSize = sp(11.5f)
        isFakeBoldText = true
        letterSpacing = 0.05f
    }

    private val modeBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#141E2D")
        style = Paint.Style.FILL
    }

    private val modeBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val modeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2EEE7")
        textSize = sp(10.5f)
        isFakeBoldText = true
    }

    private val subMetricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFC9BF")
        textSize = sp(10f)
        isFakeBoldText = true
    }

    private val pctTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9.5f)
        isFakeBoldText = true
    }

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#141C2A")
        style = Paint.Style.FILL
    }

    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222D3E")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val hotBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val warmBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val coldBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Chart paints
    private val chartBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#05080E")
        style = Paint.Style.FILL
    }

    private val chartBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B2536")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val chartGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#172130")
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
    }

    private val javaLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Radiant Emerald
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val nativeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A93B0") // Vibrant Indigo
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val totalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chartAnnotationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A746B")
        textSize = sp(9f)
    }

    private val peakBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FA6C9")
        textSize = sp(9.5f)
        isFakeBoldText = true
    }

    // Stat Cards
    private val statCardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        style = Paint.Style.FILL
    }

    private val statCardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2D40")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val statTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A746B")
        textSize = sp(8.5f)
        isFakeBoldText = true
        letterSpacing = 0.06f
    }

    private val statValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2EEE7")
        textSize = sp(10.5f)
        isFakeBoldText = true
    }

    // Header buttons
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#192233")
        style = Paint.Style.FILL
    }

    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3E56")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFC9BF")
        textSize = sp(9.5f)
        isFakeBoldText = true
    }

    private val closeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26141A")
        style = Paint.Style.FILL
    }

    private val closeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#661D2E")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val closeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EE7A60")
        textSize = sp(11.5f)
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

            // 1. Close Button with generous touch target (at least 48dp)
            val touchClose = RectF(closeBtnRect).apply { inset(-dp(12f), -dp(12f)) }
            if (touchClose.contains(x, y)) {
                onCloseClicked?.invoke()
                return true
            }

            // 2. Toggle Minimize Button with generous touch target
            val touchToggle = RectF(toggleBtnRect).apply { inset(-dp(10f), -dp(10f)) }
            if (touchToggle.contains(x, y) || isMinimized || y <= cardRect.top + dp(40f)) {
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
        val radius = dp(16f)

        cardRect.set(padding, padding, w - padding, h - padding)

        // 1. Draw Glass Card background & borders
        canvas.drawRoundRect(cardRect, radius, radius, cardBgPaint)
        canvas.drawRoundRect(cardRect, radius, radius, cardBorderPaint)

        // Top subtle glow accent line
        val glowInset = dp(24f)
        canvas.drawLine(
            cardRect.left + glowInset,
            cardRect.top + dp(1.2f),
            cardRect.right - glowInset,
            cardRect.top + dp(1.2f),
            topGlowLinePaint
        )

        // 2. Header elements
        val headerY = cardRect.top + dp(22f)
        val startX = cardRect.left + dp(12f)

        // Radar pulsing live indicator
        canvas.drawCircle(startX + dp(4f), headerY - dp(4f), dp(7f), radarOuterPaint)
        canvas.drawCircle(startX + dp(4f), headerY - dp(4f), dp(3.5f), radarInnerPaint)

        // Title
        canvas.drawText("CHRONOS X-RAY", startX + dp(15f), headerY, titlePaint)

        // Mode badge pill
        val modeColor = when (currentMode) {
            HistoryMode.NAIVE -> Color.parseColor("#F43F5E")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#D9A13B")
            HistoryMode.TIERED -> Color.parseColor("#4FA38C")
        }
        modeBadgeBorderPaint.color = modeColor
        modeTextPaint.color = modeColor

        val modeText = currentMode.name
        val titleWidth = titlePaint.measureText("CHRONOS X-RAY")
        val badgeLeft = startX + dp(22f) + titleWidth
        val badgeW = modeTextPaint.measureText(modeText) + dp(14f)
        badgeRect.set(badgeLeft, headerY - dp(14f), badgeLeft + badgeW, headerY + dp(4f))
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), modeBadgeBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), modeBadgeBorderPaint)
        canvas.drawText(modeText, badgeLeft + dp(7f), headerY - dp(1.5f), modeTextPaint)

        // Close Button [X] at top-right
        val closeW = dp(24f)
        val closeH = dp(24f)
        val closeRight = cardRect.right - dp(8f)
        val closeLeft = closeRight - closeW
        val closeTop = headerY - dp(14f)
        val closeBottom = closeTop + closeH
        closeBtnRect.set(closeLeft, closeTop, closeRight, closeBottom)
        canvas.drawRoundRect(closeBtnRect, dp(6f), dp(6f), closeBgPaint)
        canvas.drawRoundRect(closeBtnRect, dp(6f), dp(6f), closeBorderPaint)
        val xTextW = closeTextPaint.measureText("✕")
        canvas.drawText("✕", closeLeft + (closeW - xTextW) / 2f, headerY - dp(1f), closeTextPaint)

        // Toggle Minimize / Expand Button
        val toggleText = if (isMinimized) "▼ MỞ RỘNG" else "▲ THU GỌN"
        val toggleW = btnTextPaint.measureText(toggleText) + dp(14f)
        val toggleH = dp(24f)
        val toggleRight = closeLeft - dp(6f)
        val toggleLeft = toggleRight - toggleW
        toggleBtnRect.set(toggleLeft, closeTop, toggleRight, closeBottom)
        canvas.drawRoundRect(toggleBtnRect, dp(6f), dp(6f), btnBgPaint)
        canvas.drawRoundRect(toggleBtnRect, dp(6f), dp(6f), btnBorderPaint)
        canvas.drawText(toggleText, toggleLeft + dp(7f), headerY - dp(1.5f), btnTextPaint)

        if (isMinimized) {
            drawMinimizedContent(canvas, cardRect)
        } else {
            drawExpandedContent(canvas, cardRect)
        }
    }

    private fun drawMinimizedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        val totalMb = tel.currentJavaHeapMb + tel.currentNativeHeapMb
        val text = "RAM: %.1f MB  |  🔥 %.1fM   ⚡ %.1fM   ❄️ %.1fM  |  %d steps".format(
            totalMb, tel.hotMb, tel.warmMb, tel.coldMb, tel.totalSteps
        )
        canvas.drawText(text, bounds.left + dp(12f), bounds.top + dp(32f), labelPaint)
    }

    private fun drawExpandedContent(canvas: Canvas, bounds: RectF) {
        val tel = currentTelemetry
        var currentY = bounds.top + dp(37f)

        val availableWidth = bounds.width() - dp(24f)
        val left = bounds.left + dp(12f)
        val barHeight = dp(9f)
        val barCapacityMb = max(1f, max(tel.hotMb + tel.warmMb + tel.coldMb, tel.peakHeapMb))

        // 1. HOT STORE GAUGE
        val hotPct = if (barCapacityMb > 0f) ((tel.hotMb / barCapacityMb) * 100f).toInt() else 0
        canvas.drawText("🔥 HOT (In-RAM Bitmaps • O(1))", left, currentY + dp(8f), labelPaint)
        val hotValText = "%.2f MB (%d steps)".format(tel.hotMb, tel.hotSteps)
        val hotPctText = "[$hotPct%]"
        val hotPctWidth = pctTagPaint.measureText(hotPctText)
        val hotValWidth = subMetricPaint.measureText(hotValText)

        pctTagPaint.color = Color.parseColor("#F43F5E")
        canvas.drawText(hotPctText, bounds.right - dp(12f) - hotPctWidth, currentY + dp(8f), pctTagPaint)
        canvas.drawText(hotValText, bounds.right - dp(16f) - hotPctWidth - hotValWidth, currentY + dp(8f), subMetricPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBgPaint)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBorderPaint)

        val hotFrac = (tel.hotMb / barCapacityMb).coerceIn(0f, 1f)
        if (hotFrac > 0f) {
            val fillW = (availableWidth * hotFrac).coerceAtLeast(dp(6f))
            barRect.set(left, currentY, left + fillW, currentY + barHeight)
            hotBarPaint.shader = LinearGradient(
                left, currentY, left + fillW, currentY,
                Color.parseColor("#FF416C"), Color.parseColor("#FF4B2B"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), hotBarPaint)
        }
        currentY += barHeight + dp(5f)

        // 2. WARM STORE GAUGE
        val warmPct = if (barCapacityMb > 0f) ((tel.warmMb / barCapacityMb) * 100f).toInt() else 0
        canvas.drawText("⚡ WARM (Deflate LZ4 • O(log N))", left, currentY + dp(8f), labelPaint)
        val warmValText = "%.2f MB (%d steps)".format(tel.warmMb, tel.warmSteps)
        val warmPctText = "[$warmPct%]"
        val warmPctWidth = pctTagPaint.measureText(warmPctText)
        val warmValWidth = subMetricPaint.measureText(warmValText)

        pctTagPaint.color = Color.parseColor("#D9A13B")
        canvas.drawText(warmPctText, bounds.right - dp(12f) - warmPctWidth, currentY + dp(8f), pctTagPaint)
        canvas.drawText(warmValText, bounds.right - dp(16f) - warmPctWidth - warmValWidth, currentY + dp(8f), subMetricPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBgPaint)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBorderPaint)

        val warmFrac = (tel.warmMb / barCapacityMb).coerceIn(0f, 1f)
        if (warmFrac > 0f) {
            val fillW = (availableWidth * warmFrac).coerceAtLeast(dp(6f))
            barRect.set(left, currentY, left + fillW, currentY + barHeight)
            warmBarPaint.shader = LinearGradient(
                left, currentY, left + fillW, currentY,
                Color.parseColor("#F7971E"), Color.parseColor("#FFD200"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), warmBarPaint)
        }
        currentY += barHeight + dp(5f)

        // 3. COLD STORE GAUGE
        val coldPct = if (barCapacityMb > 0f) ((tel.coldMb / barCapacityMb) * 100f).toInt() else 0
        canvas.drawText("❄️ COLD (Disk Evicted • O(Disk))", left, currentY + dp(8f), labelPaint)
        val coldValText = "%.2f MB (%d steps)".format(tel.coldMb, tel.coldSteps)
        val coldPctText = "[$coldPct%]"
        val coldPctWidth = pctTagPaint.measureText(coldPctText)
        val coldValWidth = subMetricPaint.measureText(coldValText)

        pctTagPaint.color = Color.parseColor("#4FA38C")
        canvas.drawText(coldPctText, bounds.right - dp(12f) - coldPctWidth, currentY + dp(8f), pctTagPaint)
        canvas.drawText(coldValText, bounds.right - dp(16f) - coldPctWidth - coldValWidth, currentY + dp(8f), subMetricPaint)
        currentY += dp(11f)

        barRect.set(left, currentY, left + availableWidth, currentY + barHeight)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBgPaint)
        canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), trackBorderPaint)

        val coldFrac = (tel.coldMb / barCapacityMb).coerceIn(0f, 1f)
        if (coldFrac > 0f) {
            val fillW = (availableWidth * coldFrac).coerceAtLeast(dp(6f))
            barRect.set(left, currentY, left + fillW, currentY + barHeight)
            coldBarPaint.shader = LinearGradient(
                left, currentY, left + fillW, currentY,
                Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(barRect, dp(4.5f), dp(4.5f), coldBarPaint)
        }
        currentY += barHeight + dp(8f)

        // 4. 60-Second Real-Time Oscilloscope Waveform
        val chartTop = currentY
        val chartBottom = chartTop + dp(58f)
        chartRect.set(left, chartTop, bounds.right - dp(12f), chartBottom)

        canvas.drawRoundRect(chartRect, dp(8f), dp(8f), chartBgPaint)
        canvas.drawRoundRect(chartRect, dp(8f), dp(8f), chartBorderPaint)

        // Subtle Gridlines
        val stepY = chartRect.height() / 3f
        canvas.drawLine(left, chartTop + stepY, bounds.right - dp(12f), chartTop + stepY, chartGridPaint)
        canvas.drawLine(left, chartTop + stepY * 2f, bounds.right - dp(12f), chartTop + stepY * 2f, chartGridPaint)

        val stepX = chartRect.width() / 4f
        for (i in 1..3) {
            val gx = left + (stepX * i)
            canvas.drawLine(gx, chartTop, gx, chartBottom, chartGridPaint)
        }

        drawChartLines(canvas, chartRect)

        // Graph annotations
        val peakLabel = "▲ Peak: %.1f MB".format(tel.peakHeapMb)
        canvas.drawText(peakLabel, left + dp(8f), chartTop + dp(12f), peakBadgePaint)

        // Legend: Java (Emerald) + Native (Indigo)
        val legendText = "● Java Heap   ● Native Heap"
        val legendW = chartAnnotationPaint.measureText(legendText)
        canvas.drawText(legendText, bounds.right - dp(16f) - legendW, chartTop + dp(12f), chartAnnotationPaint)

        canvas.drawText("-60s", left + dp(8f), chartBottom - dp(4f), chartAnnotationPaint)
        canvas.drawText("-30s", left + (chartRect.width() / 2f) - dp(8f), chartBottom - dp(4f), chartAnnotationPaint)
        canvas.drawText("Live", bounds.right - dp(32f), chartBottom - dp(4f), chartAnnotationPaint)

        currentY = chartBottom + dp(8f)

        // 5. Three Telemetry Stat Metric Cards
        val cardGap = dp(6f)
        val cardWidth = (availableWidth - (cardGap * 2f)) / 3f
        val cardHeight = dp(28f)

        // Card 1: Steps
        val c1Left = left
        badgeRect.set(c1Left, currentY, c1Left + cardWidth, currentY + cardHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBorderPaint)
        canvas.drawText("TOTAL STEPS", c1Left + dp(7f), currentY + dp(10f), statTitlePaint)
        val stepsVal = "%d (%d redo)".format(tel.totalSteps, tel.redoSteps)
        canvas.drawText(stepsVal, c1Left + dp(7f), currentY + dp(22f), statValPaint)

        // Card 2: GC Runs
        val c2Left = c1Left + cardWidth + cardGap
        badgeRect.set(c2Left, currentY, c2Left + cardWidth, currentY + cardHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBorderPaint)
        canvas.drawText("ART GC RUNS", c2Left + dp(7f), currentY + dp(10f), statTitlePaint)
        val gcVal = if (tel.gcCount >= 0) "%d (+%d)".format(tel.gcCount, tel.gcDelta) else "N/A"
        canvas.drawText(gcVal, c2Left + dp(7f), currentY + dp(22f), statValPaint)

        // Card 3: Prefetch Hit
        val c3Left = c2Left + cardWidth + cardGap
        badgeRect.set(c3Left, currentY, c3Left + cardWidth, currentY + cardHeight)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBgPaint)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), statCardBorderPaint)
        canvas.drawText("PREFETCH HIT", c3Left + dp(7f), currentY + dp(10f), statTitlePaint)
        val prefetchVal = "%.0f%% rate".format(tel.prefetchHitRate)
        statValPaint.color = Color.parseColor("#4FA38C")
        canvas.drawText(prefetchVal, c3Left + dp(7f), currentY + dp(22f), statValPaint)
        statValPaint.color = Color.parseColor("#F2EEE7") // restore
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

        // Fill total under-curve with subtle vertical gradient
        if (!firstPoint) {
            val lastX = rect.left + (((points.last().timestampMs - windowStart) / windowDuration).coerceIn(0f, 1f) * chartWidth)
            val fillPath = Path(pathTotal).apply {
                lineTo(lastX, rect.bottom)
                lineTo(startX, rect.bottom)
                close()
            }
            totalFillPaint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.parseColor("#334FA38C"), Color.parseColor("#004FA38C"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, totalFillPaint)
        }

        // Draw curves
        canvas.drawPath(pathJava, javaLinePaint)
        canvas.drawPath(pathNative, nativeLinePaint)
    }
}
