package com.example.canvas.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.canvas.core.Patch
import com.example.canvas.core.Stroke
import com.example.canvas.core.StrokeRenderer
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.NaiveHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/**
 * Pro-grade Custom View hosting a fixed 2048x2048 backing bitmap with:
 * - Smooth single-finger Bézier stroke drawing
 * - Two-finger pinch-to-zoom (0.5x .. 5.0x) and pan
 * - Double-tap or one-tap reset to fit screen
 * - Elevated studio paper shadow, desk dot-grid, and optional paper guide grid
 * - Optional Dirty-Rect bounding box visualizer for SWAM live demos
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val CANVAS_SIZE = 2048
        private const val BYTES_PER_MIB = 1024L * 1024L
        // Keep at least 48 MiB of free heap headroom so Compose UI / RenderThread never crashes
        private const val SAFE_UI_HEADROOM_BYTES = 48L * BYTES_PER_MIB
        // Cap NAIVE full-canvas snapshots at 8 steps (128 MiB) or 50% maxHeap so testing NAIVE never kills the app process
        private const val NAIVE_SAFE_CAP_BYTES = 128L * BYTES_PER_MIB
        // Cap DIRTY_RECT uncompressed RAM snapshots at 160 MiB or 58% maxHeap
        private const val DIRTY_RECT_SAFE_CAP_BYTES = 160L * BYTES_PER_MIB
    }

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var historyManager: HistoryManager = NaiveHistory()
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 16f
    var showDirtyRectOverlay: Boolean = true
    var showPaperGrid: Boolean = false
    var lastDirtyRect: Rect? = null
        private set

    var onStrokeCommitted: ((Patch) -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null
    var onOomCaught: ((String) -> Unit)? = null

    // Backing bitmap (2048 x 2048 ARGB_8888, 16 MiB)
    val backingBitmap: Bitmap = Bitmap.createBitmap(
        CANVAS_SIZE,
        CANVAS_SIZE,
        Bitmap.Config.ARGB_8888
    )
    private val backingCanvas: Canvas = Canvas(backingBitmap)

    private val strokeRenderer = StrokeRenderer()
    private var liveStroke: Stroke? = null

    // Viewport transformation state
    private var baseScale: Float = 1f
    private var userScale: Float = 1f
    private var userPanX: Float = 0f
    private var userPanY: Float = 0f

    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val touchPoints = FloatArray(2)

    // Multi-touch gesture tracking
    private var isMultiTouch = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isMultiTouch = true
                liveStroke = null
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val prevScale = userScale
                userScale = (userScale * factor).coerceIn(0.45f, 6.0f)
                val effectiveFactor = userScale / prevScale

                val focusX = detector.focusX
                val focusY = detector.focusY

                // Zoom around focal point and apply two-finger pan delta
                val dx = focusX - lastFocusX
                val dy = focusY - lastFocusY
                userPanX = (userPanX - (focusX - width / 2f) * (effectiveFactor - 1f)) + dx
                userPanY = (userPanY - (focusY - height / 2f) * (effectiveFactor - 1f)) + dy

                lastFocusX = focusX
                lastFocusY = focusY

                rebuildMatrix()
                onZoomChanged?.invoke(userScale)
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap with 2 fingers or near border resets zoom
                return false
            }
        }
    )

    // Studio paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#14161A")
    }

    private val deskDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#232730")
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#08090C")
        style = Paint.Style.FILL
        setShadowLayer(28f, 0f, 12f, Color.parseColor("#99000000"))
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#2E333E")
    }

    private val paperGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#180284C7")
    }

    private val paperMajorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#2A0284C7")
    }

    private val dirtyRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3B82F6")
    }

    private val dirtyRectFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#143B82F6")
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3B82F6")
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EA181B21")
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val badgeTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 27f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val badgeSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        textSize = 23f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private val badgeRectF = RectF()

    init {
        backingCanvas.drawColor(Color.WHITE)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTransform(w, h)
    }

    private fun updateTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val paddingPx = 10f * resources.displayMetrics.density
        val availW = (viewWidth - paddingPx * 2f).coerceAtLeast(100f)
        val availH = (viewHeight - paddingPx * 2f).coerceAtLeast(100f)
        baseScale = min(availW / CANVAS_SIZE, availH / CANVAS_SIZE)
        rebuildMatrix()
    }

    private fun rebuildMatrix() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val effectiveScale = baseScale * userScale
        val dx = (w - CANVAS_SIZE * effectiveScale) / 2f + userPanX
        val dy = (h - CANVAS_SIZE * effectiveScale) / 2f + userPanY

        viewMatrix.reset()
        viewMatrix.postScale(effectiveScale, effectiveScale)
        viewMatrix.postTranslate(dx, dy)
        viewMatrix.invert(inverseMatrix)
    }

    fun resetViewport() {
        userScale = 1f
        userPanX = 0f
        userPanY = 0f
        rebuildMatrix()
        onZoomChanged?.invoke(1f)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            isMultiTouch = true
            liveStroke = null
            invalidate()
            return true
        }

        if (isMultiTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                isMultiTouch = false
            }
            return true
        }

        touchPoints[0] = event.x
        touchPoints[1] = event.y
        inverseMatrix.mapPoints(touchPoints)

        val canvasX = touchPoints[0].coerceIn(0f, CANVAS_SIZE.toFloat())
        val canvasY = touchPoints[1].coerceIn(0f, CANVAS_SIZE.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val stroke = Stroke(color = strokeColor, width = strokeWidth)
                stroke.addPoint(canvasX, canvasY)
                liveStroke = stroke
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val stroke = liveStroke
                if (stroke != null) {
                    // Sub-pixel smoothing filter to avoid jittery micro-points
                    val pts = stroke.points
                    if (pts.isEmpty() || hypot(canvasX - pts.last().x, canvasY - pts.last().y) >= 1.5f) {
                        stroke.addPoint(canvasX, canvasY)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = liveStroke
                if (stroke != null && stroke.points.isNotEmpty() && !isMultiTouch) {
                    stroke.addPoint(canvasX, canvasY)
                    commitStroke(stroke)
                }
                liveStroke = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Executes and commits a programmatic stroke without requiring touch events.
     */
    fun drawProgrammaticStroke(stroke: Stroke) {
        commitStroke(stroke)
        invalidate()
    }

    /**
     * Suspending version of [drawProgrammaticStroke] that awaits [HistoryManager.commit]
     * so rapid burst tests (+50 / +200 steps) apply backpressure and keep UI 120Hz smooth.
     * Throws [OutOfMemoryError] if snapshot allocation fails (e.g., in NAIVE mode).
     */
    suspend fun drawProgrammaticStrokeSuspend(stroke: Stroke) {
        val beforePatch = captureBeforePatch(stroke)
        strokeRenderer.renderStroke(backingCanvas, stroke)
        lastDirtyRect = beforePatch.rect
        invalidate()

        historyManager.commit(beforePatch)
        onStrokeCommitted?.invoke(beforePatch)
    }

    /**
     * Proactively guards against exhausting process RAM when running un-tiered modes
     * ([HistoryMode.NAIVE] or [HistoryMode.DIRTY_RECT]) so the app never gets killed by OS/RenderThread OOM,
     * while still throwing a catchable [OutOfMemoryError] to demonstrate memory exhaustion!
     */
    private fun ensureMemoryHeadroomOrThrow(requiredBytes: Long) {
        val mode = historyManager.mode
        if (mode == HistoryMode.TIERED) return // TIERED SWAM automatically offloads Hot -> Warm -> Cold

        val stats = historyManager.stats()
        val currentHotBytes = stats.hotBytes
        val rt = Runtime.getRuntime()
        val maxHeapBytes = rt.maxMemory()
        val usedJavaBytes = rt.totalMemory() - rt.freeMemory()
        val freeJavaHeadroom = maxHeapBytes - usedJavaBytes

        val modeSafeCeiling = if (mode == HistoryMode.NAIVE) {
            min((maxHeapBytes * 0.50).toLong(), NAIVE_SAFE_CAP_BYTES)
        } else {
            min((maxHeapBytes * 0.58).toLong(), DIRTY_RECT_SAFE_CAP_BYTES)
        }

        val projectedHotBytes = currentHotBytes + requiredBytes
        if (projectedHotBytes > modeSafeCeiling || freeJavaHeadroom - requiredBytes < SAFE_UI_HEADROOM_BYTES) {
            val currentMb = currentHotBytes / BYTES_PER_MIB
            val reqMb = (requiredBytes / (1024.0 * 1024.0))
            val ceilingMb = modeSafeCeiling / BYTES_PER_MIB
            throw OutOfMemoryError(
                "Chạm ngưỡng bảo vệ Cận-OOM trên chế độ ${mode.name}: Đã dùng ${currentMb} MB + ${String.format(java.util.Locale.US, "%.1f", reqMb)} MB vượt trần an toàn ${ceilingMb} MB (Giữ lại 48 MB bảo vệ UI không bị sập app!)"
            )
        }
    }

    private fun captureBeforePatch(stroke: Stroke): Patch {
        return if (historyManager.mode == HistoryMode.NAIVE) {
            val fullCanvasBytes = CANVAS_SIZE.toLong() * CANVAS_SIZE.toLong() * 4L
            ensureMemoryHeadroomOrThrow(fullCanvasBytes)
            val copy = backingBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw OutOfMemoryError("Không đủ RAM để copy Bitmap 2048x2048 (16 MiB) trong chế độ NAIVE")
            Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE),
                bitmap = copy,
                sizeBytes = copy.allocationByteCount.toLong()
            )
        } else {
            val bounds = stroke.computeBounds(CANVAS_SIZE, CANVAS_SIZE)
            val w = bounds.width().coerceAtLeast(1)
            val h = bounds.height().coerceAtLeast(1)
            ensureMemoryHeadroomOrThrow(w.toLong() * h.toLong() * 4L)
            val patchBmp = Bitmap.createBitmap(
                backingBitmap,
                bounds.left,
                bounds.top,
                w,
                h
            )
            Patch(
                id = System.nanoTime(),
                rect = bounds,
                bitmap = patchBmp,
                sizeBytes = patchBmp.allocationByteCount.toLong()
            )
        }
    }

    private fun commitStroke(stroke: Stroke) {
        val beforePatch: Patch = try {
            captureBeforePatch(stroke)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            onOomCaught?.invoke(
                oom.message ?: "Tràn bộ nhớ History RAM ở chế độ ${historyManager.mode.name}!"
            )
            return
        }

        // 2. Render stroke onto backing bitmap
        strokeRenderer.renderStroke(backingCanvas, stroke)
        lastDirtyRect = beforePatch.rect

        // 3. Commit before patch to history manager
        viewScope.launch {
            historyManager.commit(beforePatch)
            onStrokeCommitted?.invoke(beforePatch)
        }
    }

    /**
     * Fills the entire 2048x2048 canvas with [color].
     * Captures a before snapshot so this operation is undoable.
     */
    fun fillCanvas(color: Int) {
        val fullCanvasBytes = CANVAS_SIZE.toLong() * CANVAS_SIZE.toLong() * 4L
        val beforePatch = try {
            ensureMemoryHeadroomOrThrow(fullCanvasBytes)
            val copy = backingBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw OutOfMemoryError("Không đủ RAM để chụp ảnh toàn Canvas 16 MiB!")
            Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE),
                bitmap = copy,
                sizeBytes = copy.allocationByteCount.toLong()
            )
        } catch (oom: OutOfMemoryError) {
            System.gc()
            onOomCaught?.invoke(
                oom.message ?: "Không thể Fill Canvas do chạm trần RAM ở chế độ ${historyManager.mode.name}!"
            )
            return
        }

        backingCanvas.drawColor(color)
        lastDirtyRect = beforePatch.rect
        viewScope.launch {
            historyManager.commit(beforePatch)
            onStrokeCommitted?.invoke(beforePatch)
        }
        invalidate()
    }

    /**
     * Reverts the most recent stroke.
     */
    fun undo(onComplete: ((Boolean) -> Unit)? = null) {
        viewScope.launch {
            val undone = historyManager.undo(backingBitmap)
            if (undone) {
                invalidate()
            }
            onComplete?.invoke(undone)
        }
    }

    suspend fun undoSuspend(): Boolean {
        val undone = historyManager.undo(backingBitmap)
        if (undone) {
            invalidate()
        }
        return undone
    }

    /**
     * Re-applies the most recently reverted stroke.
     */
    fun redo(onComplete: ((Boolean) -> Unit)? = null) {
        viewScope.launch {
            val redone = historyManager.redo(backingBitmap)
            if (redone) {
                invalidate()
            }
            onComplete?.invoke(redone)
        }
    }

    /**
     * Clears history and resets canvas to white.
     */
    fun clearCanvas() {
        backingCanvas.drawColor(Color.WHITE)
        lastDirtyRect = null
        historyManager.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Studio Desk Background + Subtle Dot Pattern
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        val dotSpacing = 28f * resources.displayMetrics.density
        val dotRadius = 1.3f * resources.displayMetrics.density
        var y = dotSpacing / 2f
        while (y < h) {
            var x = dotSpacing / 2f
            while (x < w) {
                canvas.drawCircle(x, y, dotRadius, deskDotPaint)
                x += dotSpacing
            }
            y += dotSpacing
        }

        // 2. Apply viewport matrix for 2048x2048 artboard drawing
        canvas.save()
        canvas.concat(viewMatrix)

        // Draw subtle artboard elevation shadow
        canvas.drawRect(-10f, 12f, CANVAS_SIZE + 10f, CANVAS_SIZE + 24f, shadowPaint)

        // Draw persistent backing bitmap
        canvas.drawBitmap(backingBitmap, 0f, 0f, null)

        // Optional blueprint sketch grid on paper
        if (showPaperGrid) {
            val step = 128
            for (g in step until CANVAS_SIZE step step) {
                val p = if (g % 512 == 0) paperMajorGridPaint else paperGridPaint
                canvas.drawLine(g.toFloat(), 0f, g.toFloat(), CANVAS_SIZE.toFloat(), p)
                canvas.drawLine(0f, g.toFloat(), CANVAS_SIZE.toFloat(), g.toFloat(), p)
            }
        }

        val modeAccentColor = when (historyManager.mode) {
            HistoryMode.NAIVE -> Color.parseColor("#F06272")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#F59E0B")
            HistoryMode.TIERED -> Color.parseColor("#4F8EF7")
        }

        // Draw live stroke preview with brush cursor ring
        val currentLive = liveStroke
        if (currentLive != null) {
            strokeRenderer.renderLiveStroke(canvas, currentLive, modeAccentColor)
        }

        // Draw Figma-style Bounding Box Selection Overlay
        if (showDirtyRectOverlay) {
            val activeRect: Rect? = if (currentLive != null && currentLive.points.isNotEmpty()) {
                if (historyManager.mode == HistoryMode.NAIVE) {
                    Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
                } else {
                    currentLive.computeBounds(CANVAS_SIZE, CANVAS_SIZE)
                }
            } else {
                lastDirtyRect
            }

            if (activeRect != null) {
                drawCyberBoundingBoxOverlay(
                    canvas = canvas,
                    rect = activeRect,
                    isLiveScanning = currentLive != null,
                    accentColor = modeAccentColor
                )
            }
        }

        // Draw artboard border
        canvas.drawRect(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat(), borderPaint)

        canvas.restore()
    }

    /**
     * Renders a clean Figma/Studio-style selection bounding box with:
     * - Subtle translucent selection fill & crisp 3px stroke
     * - 4 square white-filled corner selection handles (14x14 px)
     * - Compact dimension & exact byte size pill tag above the bounding box
     */
    private fun drawCyberBoundingBoxOverlay(
        canvas: Canvas,
        rect: Rect,
        isLiveScanning: Boolean,
        accentColor: Int
    ) {
        val left = rect.left.toFloat()
        val top = rect.top.toFloat()
        val right = rect.right.toFloat()
        val bottom = rect.bottom.toFloat()
        val boxW = rect.width().coerceAtLeast(1)
        val boxH = rect.height().coerceAtLeast(1)

        dirtyRectPaint.color = accentColor
        dirtyRectFillPaint.color = accentColor
        dirtyRectFillPaint.alpha = if (isLiveScanning) 26 else 16
        handleStrokePaint.color = accentColor

        // 1. Subtle selection fill + crisp selection frame
        canvas.drawRect(left, top, right, bottom, dirtyRectFillPaint)
        canvas.drawRect(left, top, right, bottom, dirtyRectPaint)

        // 2. 4 Figma-style square corner handles (14x14 px)
        val hs = 7f
        val corners = arrayOf(
            floatArrayOf(left, top),
            floatArrayOf(right, top),
            floatArrayOf(left, bottom),
            floatArrayOf(right, bottom)
        )
        for (c in corners) {
            val cx = c[0]
            val cy = c[1]
            canvas.drawRect(cx - hs, cy - hs, cx + hs, cy + hs, handleFillPaint)
            canvas.drawRect(cx - hs, cy - hs, cx + hs, cy + hs, handleStrokePaint)
        }

        // 3. Compact measured dimension & raw KB tag pill
        val rawBytes = boxW.toLong() * boxH.toLong() * 4L
        val rawKb = rawBytes.toDouble() / 1024.0
        val fullCanvasBytes = CANVAS_SIZE.toLong() * CANVAS_SIZE.toLong() * 4L
        val areaReductionPct = (1.0 - rawBytes.toDouble() / fullCanvasBytes.toDouble()) * 100.0

        val stateLabel = if (isLiveScanning) "Đang quét" else "Đã lưu"
        val line1 = "$stateLabel [${historyManager.mode.name}]  •  ${boxW} × ${boxH} px"
        val line2 = if (historyManager.mode == HistoryMode.NAIVE) {
            String.format(java.util.Locale.US, "Bản sao: %.1f KB (Toàn bộ 2048×2048)", rawKb)
        } else {
            String.format(
                java.util.Locale.US,
                "Vùng chọn: %.1f KB (Giảm %.1f%% diện tích so với 16 MB)",
                rawKb,
                areaReductionPct
            )
        }

        badgeTitlePaint.color = accentColor
        val textW = kotlin.math.max(badgeTitlePaint.measureText(line1), badgeSubPaint.measureText(line2))
        val pillW = textW + 36f
        val pillH = 74f

        val pillLeft = left.coerceIn(12f, (CANVAS_SIZE - pillW - 12f).coerceAtLeast(12f))
        val pillTop = if (top - pillH - 10f >= 12f) {
            top - pillH - 10f
        } else {
            (top + 10f).coerceAtMost(CANVAS_SIZE - pillH - 12f)
        }

        badgeRectF.set(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        badgeBorderPaint.color = accentColor
        canvas.drawRoundRect(badgeRectF, 12f, 12f, badgeBgPaint)
        canvas.drawRoundRect(badgeRectF, 12f, 12f, badgeBorderPaint)

        canvas.drawText(line1, pillLeft + 18f, pillTop + 32f, badgeTitlePaint)
        canvas.drawText(line2, pillLeft + 18f, pillTop + 60f, badgeSubPaint)
    }
}
