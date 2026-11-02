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

        // Default to DIRTY_RECT or NAIVE
        drawingView.historyManager = DirtyRectHistory()
        tvMode.text = drawingView.historyManager.mode.name

        // Click to toggle mode
        tvMode.setOnClickListener {
            val newMode = if (drawingView.historyManager.mode == com.example.canvas.history.HistoryMode.NAIVE) {
                com.example.canvas.history.DirtyRectHistory()
            } else {
                com.example.canvas.history.NaiveHistory()
            }
            drawingView.historyManager = newMode
            tvMode.text = newMode.mode.name
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
        tvStats.text = "Steps: %d (Redo: %d) | Hot: %.1f MiB".format(
            stats.totalSteps,
            stats.redoSteps,
            hotMiB
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        drawingView.historyManager.onTrimMemory(level)
    }
}
