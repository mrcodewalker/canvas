package com.example.canvas.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.canvas.R
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.TieredHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView
    private lateinit var tvMode: TextView
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        tvMode = findViewById(R.id.tvMode)
        tvStats = findViewById(R.id.tvStats)

        // Default to TIERED (main theme of the project)
        drawingView.historyManager = TieredHistory.create(this)
        tvMode.text = drawingView.historyManager.mode.name

        // Click to cycle modes
        tvMode.setOnClickListener {
            val nextManager: com.example.canvas.history.HistoryManager = when (drawingView.historyManager.mode) {
                com.example.canvas.history.HistoryMode.NAIVE -> com.example.canvas.history.DirtyRectHistory()
                com.example.canvas.history.HistoryMode.DIRTY_RECT -> com.example.canvas.history.TieredHistory.create(this)
                com.example.canvas.history.HistoryMode.TIERED -> com.example.canvas.history.NaiveHistory()
            }
            drawingView.historyManager = nextManager
            tvMode.text = nextManager.mode.name
            updateStats()
        }

        setupToolbar()
        startStatsMonitor()
    }

    private fun setupToolbar() {
        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            drawingView.undo { updateStats() }
        }

        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            drawingView.redo { updateStats() }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            drawingView.clearCanvas()
            updateStats()
        }

        findViewById<Button>(R.id.btnFill).setOnClickListener {
            drawingView.fillCanvas(drawingView.strokeColor)
            updateStats()
        }

        findViewById<Button>(R.id.btnStress).setOnClickListener {
            @Suppress("DEPRECATION")
            drawingView.historyManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            android.widget.Toast.makeText(this, "Memory Stress: Demoted Hot to Warm/Cold", android.widget.Toast.LENGTH_SHORT).show()
            updateStats()
        }

        findViewById<Button>(R.id.btnBenchmark).setOnClickListener {
            val intent = android.content.Intent(this, BenchmarkActivity::class.java)
            startActivity(intent)
        }

        // Brush sizes
        findViewById<Button>(R.id.btnBrushSmall).setOnClickListener {
            drawingView.strokeWidth = 8f
        }
        findViewById<Button>(R.id.btnBrushMedium).setOnClickListener {
            drawingView.strokeWidth = 24f
        }
        findViewById<Button>(R.id.btnBrushLarge).setOnClickListener {
            drawingView.strokeWidth = 64f
        }
        findViewById<Button>(R.id.btnBrushXL).setOnClickListener {
            drawingView.strokeWidth = 160f
        }

        // Color palette
        findViewById<android.view.View>(R.id.colorBlack).setOnClickListener {
            drawingView.strokeColor = Color.BLACK
        }
        findViewById<android.view.View>(R.id.colorRed).setOnClickListener {
            drawingView.strokeColor = Color.parseColor("#E53935")
        }
        findViewById<android.view.View>(R.id.colorBlue).setOnClickListener {
            drawingView.strokeColor = Color.parseColor("#1E88E5")
        }
        findViewById<android.view.View>(R.id.colorGreen).setOnClickListener {
            drawingView.strokeColor = Color.parseColor("#43A047")
        }
        findViewById<android.view.View>(R.id.colorWhite).setOnClickListener {
            drawingView.strokeColor = Color.WHITE
        }
    }

    private fun startStatsMonitor() {
        lifecycleScope.launch {
            while (isActive) {
                updateStats()
                delay(300)
            }
        }
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
