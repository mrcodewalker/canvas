package com.example.canvas.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
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
import kotlin.math.min

/**
 * Custom View hosting a fixed 2048x2048 backing bitmap with zoom-to-fit transformation,
 * live stroke preview, and history management.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val CANVAS_SIZE = 2048
    }

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var historyManager: HistoryManager = NaiveHistory()
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 16f

    // Backing bitmap (2048 x 2048 ARGB_8888, 16 MiB)
    val backingBitmap: Bitmap = Bitmap.createBitmap(
        CANVAS_SIZE,
        CANVAS_SIZE,
        Bitmap.Config.ARGB_8888
    )
    private val backingCanvas: Canvas = Canvas(backingBitmap)

    private val strokeRenderer = StrokeRenderer()
    private var liveStroke: Stroke? = null

    // Transformation matrices
    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val touchPoints = FloatArray(2)

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#333333")
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#181818")
    }

    init {
        // Initialize backing canvas to pure white
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

        val scale = min(viewWidth.toFloat() / CANVAS_SIZE, viewHeight.toFloat() / CANVAS_SIZE)
        val dx = (viewWidth - CANVAS_SIZE * scale) / 2f
        val dy = (viewHeight - CANVAS_SIZE * scale) / 2f

        viewMatrix.reset()
        viewMatrix.postScale(scale, scale)
        viewMatrix.postTranslate(dx, dy)
        viewMatrix.invert(inverseMatrix)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
                liveStroke?.addPoint(canvasX, canvasY)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = liveStroke
                if (stroke != null && stroke.points.isNotEmpty()) {
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

    private fun commitStroke(stroke: Stroke) {
        // 1. Capture before patch
        val beforePatch: Patch = if (historyManager.mode == HistoryMode.NAIVE) {
            // Naive copies full canvas
            val copy = backingBitmap.copy(Bitmap.Config.ARGB_8888, false)
            Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE),
                bitmap = copy,
                sizeBytes = copy?.allocationByteCount?.toLong() ?: 0L
            )
        } else {
            // DirtyRect or Tiered: captures enclosing bounds
            val bounds = stroke.computeBounds(CANVAS_SIZE, CANVAS_SIZE)
            val patchBmp = Bitmap.createBitmap(
                backingBitmap,
                bounds.left,
                bounds.top,
                bounds.width().coerceAtLeast(1),
                bounds.height().coerceAtLeast(1)
            )
            Patch(
                id = System.nanoTime(),
                rect = bounds,
                bitmap = patchBmp,
                sizeBytes = patchBmp.allocationByteCount.toLong()
            )
        }

        // 2. Render stroke onto backing bitmap
        strokeRenderer.renderStroke(backingCanvas, stroke)

        // 3. Commit before patch to history manager
        viewScope.launch {
            historyManager.commit(beforePatch)
        }
    }

    /**
     * Fills the entire 2048x2048 canvas with [color].
     * Captures a before snapshot so this operation is undoable.
     */
    fun fillCanvas(color: Int) {
        val copy = backingBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val beforePatch = Patch(
            id = System.nanoTime(),
            rect = Rect(0, 0, CANVAS_SIZE, CANVAS_SIZE),
            bitmap = copy,
            sizeBytes = copy?.allocationByteCount?.toLong() ?: 0L
        )

        backingCanvas.drawColor(color)
        viewScope.launch {
            historyManager.commit(beforePatch)
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
        historyManager.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw neutral dark workspace background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Apply viewport matrix for canvas drawing
        canvas.save()
        canvas.concat(viewMatrix)

        // 1. Draw persistent backing bitmap
        canvas.drawBitmap(backingBitmap, 0f, 0f, null)

        // 2. Draw live stroke preview directly on screen
        liveStroke?.let { stroke ->
            strokeRenderer.renderStroke(canvas, stroke)
        }

        // 3. Draw border around 2048x2048 canvas
        canvas.drawRect(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat(), borderPaint)

        canvas.restore()
    }
}
