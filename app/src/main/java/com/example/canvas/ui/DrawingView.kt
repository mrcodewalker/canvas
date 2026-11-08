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
import com.example.canvas.core.StrokeOp
import com.example.canvas.core.StrokeRenderer
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.MemorySignal
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
        set(value) {
            field = value
            value.opRenderer = opRenderer
        }
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 16f
    var showDirtyRectOverlay: Boolean = true
    var showPaperGrid: Boolean = false
    /** true = save only the dirty tiles a stroke touches; false = one bounding box (paper baseline). */
    var tilingEnabled: Boolean = true
    var lastDirtyRect: Rect? = null
        private set
    /** The patches the last stroke really saved (tiles), shown by the overlay. */
    var lastDirtyRegions: List<Rect> = emptyList()
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

    /** Lets the history replay a recorded step on the current canvas (redo with merge). */
    private val opRenderer: (Canvas, StrokeOp) -> Unit = { c, op ->
        val fill = op.fillColor
        if (fill != null) c.drawColor(fill) else op.stroke?.let { strokeRenderer.renderStroke(c, it) }
    }

    init {
        historyManager.opRenderer = opRenderer
    }
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
        color = Color.parseColor("#262320")          // the desk: a calm warm grey, a step lighter than the bars
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#262320")
        style = Paint.Style.FILL
        setShadowLayer(40f, 0f, 14f, Color.parseColor("#66000000"))
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#1A000000")
    }

    private val paperGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#18E2553A")
    }

    private val paperMajorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#2AE2553A")
    }

    // "What was saved" overlay: the real patches of the last stroke, drawn quietly
    private val regionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val regionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F21F1D1B")
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2EEE7")
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val labelRectF = RectF()
    private var labelKey = Long.MIN_VALUE
    private var labelText = ""

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
                    // Skip points closer than one screen pixel (jitter); the minimum follows the zoom
                    val pts = stroke.points
                    val minStep = 1f / (baseScale * userScale).coerceAtLeast(1e-3f)
                    // The system batches several touch samples per event: keep them all, oldest first
                    for (h in 0 until event.historySize) {
                        touchPoints[0] = event.getHistoricalX(h)
                        touchPoints[1] = event.getHistoricalY(h)
                        inverseMatrix.mapPoints(touchPoints)
                        val hx = touchPoints[0].coerceIn(0f, CANVAS_SIZE.toFloat())
                        val hy = touchPoints[1].coerceIn(0f, CANVAS_SIZE.toFloat())
                        if (pts.isEmpty() || hypot(hx - pts.last().x, hy - pts.last().y) >= minStep) {
                            stroke.addPoint(hx, hy)
                        }
                    }
                    if (pts.isEmpty() || hypot(canvasX - pts.last().x, canvasY - pts.last().y) >= minStep) {
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
        val beforePatches = captureBeforePatches(stroke)
        strokeRenderer.renderStroke(backingCanvas, stroke)
        val summary = summarize(beforePatches, stroke)
        lastDirtyRect = summary.rect
        lastDirtyRegions = beforePatches.map { it.rect }
        invalidate()

        commitAll(beforePatches)
        onStrokeCommitted?.invoke(summary)
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
        val freeJavaHeadroom = minOf(maxHeapBytes - usedJavaBytes, MemorySignal.nativeFree())

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

    /**
     * Captures the "before" pixels of the area [stroke] will modify.
     * NAIVE: one full-canvas copy. Otherwise one patch per dirty tile run (see
     * [Stroke.computeDirtyRegions]) when [tilingEnabled], or a single bounding-box patch.
     * Multiple patches of one stroke share a [Patch.groupId] so undo/redo treats them as one step.
     */
    private fun captureBeforePatches(stroke: Stroke): List<Patch> {
        if (historyManager.mode == HistoryMode.NAIVE) {
            val fullCanvasBytes = CANVAS_SIZE.toLong() * CANVAS_SIZE.toLong() * 4L
            ensureMemoryHeadroomOrThrow(fullCanvasBytes)
            val copy = backingBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw OutOfMemoryError("Không đủ RAM để copy Bitmap 2048x2048 (16 MiB) trong chế độ NAIVE")
            return listOf(
                Patch(
                    id = System.nanoTime(),
                    rect = Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE),
                    bitmap = copy,
                    sizeBytes = copy.allocationByteCount.toLong()
                )
            )
        }

        val regions = if (tilingEnabled) {
            stroke.computeDirtyRegions(CANVAS_SIZE, CANVAS_SIZE, historyManager.patchOverheadBytes() ?: Stroke.PAGE_BYTES)
        } else {
            listOf(stroke.computeBounds(CANVAS_SIZE, CANVAS_SIZE))
        }
        var totalBytes = 0L
        for (r in regions) totalBytes += r.width().coerceAtLeast(1).toLong() * r.height().coerceAtLeast(1) * 4L
        ensureMemoryHeadroomOrThrow(totalBytes)

        val groupId = if (regions.size > 1) System.nanoTime() else 0L
        val patches = ArrayList<Patch>(regions.size)
        try {
            for (rect in regions) {
                val bmp = Bitmap.createBitmap(
                    backingBitmap,
                    rect.left,
                    rect.top,
                    rect.width().coerceAtLeast(1),
                    rect.height().coerceAtLeast(1)
                )
                patches.add(
                    Patch(
                        id = System.nanoTime() + patches.size,
                        rect = rect,
                        bitmap = bmp,
                        sizeBytes = bmp.allocationByteCount.toLong(),
                        groupId = groupId,
                        op = StrokeOp(stroke = stroke)
                    )
                )
            }
        } catch (oom: OutOfMemoryError) {
            patches.forEach { it.recycle() }
            throw oom
        }
        return patches
    }

    /** Summary patch (no bitmap, never stored) used for UI callbacks about one committed stroke. */
    private fun summarize(patches: List<Patch>, stroke: Stroke): Patch {
        val bounds = if (patches.size == 1) patches[0].rect else stroke.computeBounds(CANVAS_SIZE, CANVAS_SIZE)
        return Patch(
            id = patches.firstOrNull()?.id ?: 0L,
            rect = bounds,
            sizeBytes = patches.sumOf { it.sizeBytes }
        )
    }

    private suspend fun commitAll(patches: List<Patch>) {
        for (p in patches) historyManager.commit(p)
    }

    private fun commitStroke(stroke: Stroke) {
        val beforePatches: List<Patch> = try {
            captureBeforePatches(stroke)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            onOomCaught?.invoke(
                oom.message ?: "Tràn bộ nhớ History RAM ở chế độ ${historyManager.mode.name}!"
            )
            return
        }

        // 2. Render stroke onto backing bitmap
        strokeRenderer.renderStroke(backingCanvas, stroke)
        val summary = summarize(beforePatches, stroke)
        lastDirtyRect = summary.rect
        lastDirtyRegions = beforePatches.map { it.rect }

        // 3. Commit before patches to history manager (in order, one coroutine)
        viewScope.launch {
            commitAll(beforePatches)
            onStrokeCommitted?.invoke(summary)
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
                sizeBytes = copy.allocationByteCount.toLong(),
                op = StrokeOp(fillColor = color)
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
        lastDirtyRegions = listOf(beforePatch.rect)
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
        lastDirtyRegions = emptyList()
        historyManager.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. The desk: one flat colour
        canvas.drawRect(0f, 0f, w, h, bgPaint)

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
            HistoryMode.NAIVE -> Color.parseColor("#CF4F3A")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#D9A13B")
            HistoryMode.TIERED -> Color.parseColor("#E2553A")
        }

        // Draw live stroke preview with brush cursor ring
        val currentLive = liveStroke
        if (currentLive != null) {
            strokeRenderer.renderLiveStroke(canvas, currentLive, modeAccentColor)
        }

        // What this stroke really saved (patches), and the box a single bounding rect would have saved
        if (showDirtyRectOverlay) {
            if (currentLive != null && currentLive.points.isNotEmpty()) {
                val box = if (historyManager.mode == HistoryMode.NAIVE) Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE)
                else currentLive.computeBounds(CANVAS_SIZE, CANVAS_SIZE)
                drawSavedOverlay(canvas, listOf(box), box, modeAccentColor, live = true)
            } else {
                val box = lastDirtyRect
                if (box != null) drawSavedOverlay(canvas, lastDirtyRegions.ifEmpty { listOf(box) }, box, modeAccentColor, live = false)
            }
        }

        // Draw artboard border
        canvas.drawRect(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat(), borderPaint)

        canvas.restore()
    }

    /**
     * Draws the patches a stroke saved as light tinted rectangles with a hairline, inside a dashed outline of
     * the bounding box a plain dirty-rect history would have copied, plus one small label. Line widths and the
     * text follow the zoom so they stay hairlines on screen instead of growing with the canvas.
     */
    private fun drawSavedOverlay(canvas: Canvas, regions: List<Rect>, box: Rect, accent: Int, live: Boolean) {
        val density = resources.displayMetrics.density
        val scale = (baseScale * userScale).coerceAtLeast(1e-3f)
        val hair = density / scale

        regionFillPaint.color = accent
        regionFillPaint.alpha = if (live) 22 else 30
        regionLinePaint.color = accent
        regionLinePaint.alpha = 200
        regionLinePaint.strokeWidth = hair
        boundsPaint.color = accent
        boundsPaint.alpha = 110
        boundsPaint.strokeWidth = hair
        boundsPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * hair, 5f * hair), 0f)

        var coveredPx = 0L
        for (r in regions) {
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), regionFillPaint)
            canvas.drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), regionLinePaint)
            coveredPx += r.width().toLong() * r.height()
        }
        if (regions.size > 1 || regions[0] != box) {
            canvas.drawRect(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), boundsPaint)
        }

        // One label, rebuilt only when what it says changes
        val key = coveredPx xor (regions.size.toLong() shl 40) xor (historyManager.mode.ordinal.toLong() shl 56) xor
            (if (live) 1L else 0L)
        if (key != labelKey || labelText.isEmpty()) {
            labelKey = key
            val kb = coveredPx * 4.0 / 1024.0
            val size = if (kb >= 1024) String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
            else String.format(java.util.Locale.US, "%.0f KB", kb)
            labelText = when {
                historyManager.mode == HistoryMode.NAIVE -> "Cả canvas · $size"
                regions.size == 1 -> "1 vùng · $size"
                else -> "${regions.size} vùng · $size"
            }
        }
        val textSize = 11f * density / scale
        labelTextPaint.textSize = textSize
        val padX = 7f * density / scale
        val padY = 4f * density / scale
        val textW = labelTextPaint.measureText(labelText)
        val tagW = textW + padX * 2
        val tagH = textSize + padY * 2
        val tagLeft = box.left.toFloat().coerceIn(0f, (CANVAS_SIZE - tagW).coerceAtLeast(0f))
        val tagTop = if (box.top - tagH - 4f * hair >= 0f) box.top - tagH - 4f * hair else box.top + 4f * hair
        labelRectF.set(tagLeft, tagTop, tagLeft + tagW, tagTop + tagH)
        canvas.drawRoundRect(labelRectF, 4f * hair, 4f * hair, labelBgPaint)
        canvas.drawText(labelText, tagLeft + padX, tagTop + padY + textSize * 0.82f, labelTextPaint)
    }
}
