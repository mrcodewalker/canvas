package com.example.canvas.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.canvas.R
import com.example.canvas.bench.DemoScenarioRunner
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.TieredHistory
import com.example.canvas.metrics.MemorySampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var tvMode: TextView
    private lateinit var tvStats: TextView
    private lateinit var xRayView: XRayView

    // Demo banner views
    private lateinit var demoBanner: LinearLayout
    private lateinit var tvDemoPhase: TextView
    private lateinit var pbDemoProgress: ProgressBar
    private lateinit var tvDemoInstruction: TextView
    private lateinit var btnStopDemo: Button

    private val xRayTelemetryTracker = XRayTelemetryTracker(windowDurationMs = 60_000L)
    private var memorySampler: MemorySampler? = null
    private var demoRunner: DemoScenarioRunner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure edge-to-edge so insets can be measured accurately
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Dynamically pad top and bottom containers according to status bar, display cutout, and navigation bar
        val topContainer = findViewById<View>(R.id.topContainer)
        val bottomContainer = findViewById<View>(R.id.bottomContainer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { _, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topContainer.setPadding(
                topContainer.paddingLeft,
                sysInsets.top,
                topContainer.paddingRight,
                topContainer.paddingBottom
            )
            bottomContainer.setPadding(
                bottomContainer.paddingLeft,
                bottomContainer.paddingTop,
                bottomContainer.paddingRight,
                sysInsets.bottom
            )
            insets
        }

        drawingView = findViewById(R.id.drawingView)
        tvMode = findViewById(R.id.tvMode)
        tvStats = findViewById(R.id.tvStats)
        xRayView = findViewById(R.id.xRayView)

        demoBanner = findViewById(R.id.demoBanner)
        tvDemoPhase = findViewById(R.id.tvDemoPhase)
        pbDemoProgress = findViewById(R.id.pbDemoProgress)
        tvDemoInstruction = findViewById(R.id.tvDemoInstruction)
        btnStopDemo = findViewById(R.id.btnStopDemo)

        // Default to TIERED (main theme of the project)
        switchMode(HistoryMode.TIERED)

        // Click mode badge to cycle modes manually
        tvMode.setOnClickListener {
            val nextMode = when (drawingView.historyManager.mode) {
                HistoryMode.NAIVE -> HistoryMode.DIRTY_RECT
                HistoryMode.DIRTY_RECT -> HistoryMode.TIERED
                HistoryMode.TIERED -> HistoryMode.NAIVE
            }
            switchMode(nextMode)
            Toast.makeText(this, "Chế độ: ${nextMode.name}", Toast.LENGTH_SHORT).show()
        }

        setupToolbar()
        initSampler()
        setupDemoRunner()
    }

    private fun initSampler() {
        memorySampler = MemorySampler(
            historyManager = drawingView.historyManager,
            intervalMs = 250L
        ) { snapshot ->
            runOnUiThread {
                xRayTelemetryTracker.addSample(snapshot, drawingView.historyManager.stats())
                xRayView.updateTelemetry(xRayTelemetryTracker, drawingView.historyManager.mode)
                updateStats()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        memorySampler?.start(lifecycleScope)
    }

    override fun onPause() {
        super.onPause()
        memorySampler?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        demoRunner?.stop()
    }

    fun switchMode(mode: HistoryMode) {
        val newManager: HistoryManager = when (mode) {
            HistoryMode.NAIVE -> NaiveHistory()
            HistoryMode.DIRTY_RECT -> DirtyRectHistory()
            HistoryMode.TIERED -> TieredHistory.create(this)
        }
        drawingView.historyManager = newManager
        memorySampler?.updateHistoryManager(newManager)
        tvMode.text = "${newManager.mode.name} ▾"
        val modeColor = when (mode) {
            HistoryMode.NAIVE -> Color.parseColor("#FF5252")
            HistoryMode.DIRTY_RECT -> Color.parseColor("#FFB300")
            HistoryMode.TIERED -> Color.parseColor("#00E676")
        }
        tvMode.setTextColor(modeColor)
        xRayView.currentMode = newManager.mode
        updateStats()
    }

    private fun setupToolbar() {
        val btnToggleXRay = findViewById<Button>(R.id.btnToggleXRay)

        xRayView.onCloseClicked = {
            xRayView.visibility = View.GONE
            btnToggleXRay.setBackgroundResource(R.drawable.bg_toolbar_pill)
            Toast.makeText(this, "Đã ẩn X-Ray HUD (bấm nút 'HUD X-Ray' để mở lại)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            drawingView.undo { undone ->
                updateStats()
                if (!undone) {
                    Toast.makeText(this, "Không có nét vẽ để Undo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            drawingView.redo { redone ->
                updateStats()
                if (!redone) {
                    Toast.makeText(this, "Không có nét vẽ để Redo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            drawingView.clearCanvas()
            updateStats()
            Toast.makeText(this, "Đã xóa toàn bộ Canvas", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnFill).setOnClickListener {
            drawingView.fillCanvas(drawingView.strokeColor)
            updateStats()
            Toast.makeText(this, "Đã Fill 16MB snapshot (2048x2048 ARGB_8888)", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStress).setOnClickListener {
            @Suppress("DEPRECATION")
            drawingView.historyManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            updateStats()
            Toast.makeText(this, "⚡ Memory Stress: Đẩy Hot sang Warm/Cold!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBenchmark).setOnClickListener {
            val intent = Intent(this, BenchmarkActivity::class.java)
            startActivity(intent)
        }

        btnToggleXRay.setOnClickListener {
            val isVis = xRayView.visibility == View.VISIBLE
            if (isVis) {
                xRayView.visibility = View.GONE
                btnToggleXRay.setBackgroundResource(R.drawable.bg_toolbar_pill)
                Toast.makeText(this, "Đã ẩn X-Ray HUD", Toast.LENGTH_SHORT).show()
            } else {
                xRayView.visibility = View.VISIBLE
                btnToggleXRay.setBackgroundResource(R.drawable.bg_toolbar_pill_accent)
                Toast.makeText(this, "Đã mở X-Ray HUD", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnDemo3Min).setOnClickListener {
            if (demoRunner?.isDemoRunning == true) {
                demoRunner?.stop()
                Toast.makeText(this, "Đã dừng Demo", Toast.LENGTH_SHORT).show()
            } else {
                startDemo()
                Toast.makeText(this, "Bắt đầu kịch bản Demo 3 phút SWAM...", Toast.LENGTH_SHORT).show()
            }
        }

        btnStopDemo.setOnClickListener {
            demoRunner?.stop()
            Toast.makeText(this, "Đã dừng Demo", Toast.LENGTH_SHORT).show()
        }

        // Brush size selector buttons with active visual feedback
        val btnS = findViewById<Button>(R.id.btnBrushSmall)
        val btnM = findViewById<Button>(R.id.btnBrushMedium)
        val btnL = findViewById<Button>(R.id.btnBrushLarge)
        val btnXL = findViewById<Button>(R.id.btnBrushXL)
        val brushButtons = listOf(btnS, btnM, btnL, btnXL)

        fun selectBrush(btn: Button, width: Float) {
            drawingView.strokeWidth = width
            brushButtons.forEach {
                if (it == btn) {
                    it.setBackgroundResource(R.drawable.bg_brush_button_selected)
                    it.setTextColor(Color.WHITE)
                    it.paint.isFakeBoldText = true
                    it.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                } else {
                    it.setBackgroundResource(R.drawable.bg_brush_button_normal)
                    it.setTextColor(Color.parseColor("#CBD5E1"))
                    it.paint.isFakeBoldText = false
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        btnS.setOnClickListener { selectBrush(btnS, 8f) }
        btnM.setOnClickListener { selectBrush(btnM, 24f) }
        btnL.setOnClickListener { selectBrush(btnL, 64f) }
        btnXL.setOnClickListener { selectBrush(btnXL, 160f) }

        // Color palette buttons with active visual feedback
        val cBlack = findViewById<View>(R.id.colorBlack)
        val cRed = findViewById<View>(R.id.colorRed)
        val cBlue = findViewById<View>(R.id.colorBlue)
        val cGreen = findViewById<View>(R.id.colorGreen)
        val cWhite = findViewById<View>(R.id.colorWhite)
        val colorViews = listOf(cBlack, cRed, cBlue, cGreen, cWhite)

        fun selectColor(view: View, color: Int) {
            drawingView.strokeColor = color
            colorViews.forEach {
                if (it == view) {
                    it.animate().scaleX(1.28f).scaleY(1.28f).setDuration(120).start()
                } else {
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        cBlack.setOnClickListener { selectColor(cBlack, Color.BLACK) }
        cRed.setOnClickListener { selectColor(cRed, Color.parseColor("#EF4444")) }
        cBlue.setOnClickListener { selectColor(cBlue, Color.parseColor("#3B82F6")) }
        cGreen.setOnClickListener { selectColor(cGreen, Color.parseColor("#10B981")) }
        cWhite.setOnClickListener { selectColor(cWhite, Color.WHITE) }

        // Default to black color selected
        selectColor(cBlack, Color.BLACK)
    }

    private fun setupDemoRunner() {
        demoRunner = DemoScenarioRunner(
            timeScale = 1.0f,
            onStateUpdate = { state ->
                runOnUiThread {
                    if (state.isRunning) {
                        demoBanner.visibility = View.VISIBLE
                        val min = state.elapsedSeconds / 60
                        val sec = state.elapsedSeconds % 60
                        tvDemoPhase.text = "%s (%d:%02d / 3:00)".format(
                            state.currentPhase.title, min, sec
                        )
                        pbDemoProgress.progress = state.elapsedSeconds
                        tvDemoInstruction.text = state.currentInstruction
                    } else {
                        demoBanner.visibility = View.GONE
                    }
                }
            },
            onSwitchMode = { mode ->
                runOnUiThread {
                    switchMode(mode)
                }
            },
            onDrawStroke = { stroke ->
                runOnUiThread {
                    drawingView.drawProgrammaticStroke(stroke)
                }
            },
            onUndo = {
                runOnUiThread {
                    drawingView.undo { updateStats() }
                }
            },
            onStress = {
                runOnUiThread {
                    @Suppress("DEPRECATION")
                    drawingView.historyManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                    updateStats()
                }
            },
            onClearCanvas = {
                runOnUiThread {
                    drawingView.clearCanvas()
                }
            }
        )
    }

    private fun startDemo() {
        // Ensure X-Ray overlay is visible when demo runs
        xRayView.visibility = View.VISIBLE
        demoRunner?.start(lifecycleScope)
    }

    private fun updateStats() {
        val stats = drawingView.historyManager.stats()
        val hotMiB = stats.hotBytes / (1024.0 * 1024.0)
        val warmMiB = stats.warmBytes / (1024.0 * 1024.0)
        val coldMiB = stats.coldBytes / (1024.0 * 1024.0)
        tvStats.text = "Steps: %d (Hot:%d, W:%d, C:%d) | H:%.1fMB | W:%.1fMB | C:%.1fMB".format(
            stats.totalSteps,
            stats.hotSteps,
            stats.warmSteps,
            stats.coldSteps,
            hotMiB,
            warmMiB,
            coldMiB
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        drawingView.historyManager.onTrimMemory(level)
    }
}
