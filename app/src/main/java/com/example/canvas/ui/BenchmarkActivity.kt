package com.example.canvas.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.canvas.R
import com.example.canvas.bench.BenchmarkConfig
import com.example.canvas.bench.BenchmarkRunner
import com.example.canvas.bench.ScenarioType
import com.example.canvas.bench.StrokeType
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.TieredHistory
import com.example.canvas.metrics.CsvExporter
import com.example.canvas.metrics.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class BenchmarkActivity : AppCompatActivity() {

    private lateinit var spinnerMode: Spinner
    private lateinit var spinnerScenario: Spinner
    private lateinit var spinnerStrokeType: Spinner
    private lateinit var etSteps: EditText
    private lateinit var etSeed: EditText
    private lateinit var btnRun: Button
    private lateinit var btnExportCsv: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvResults: TextView

    private var lastCollector: MetricsCollector? = null
    private var lastConfig: BenchmarkConfig? = null
    private var lastMode: HistoryMode = HistoryMode.NAIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        spinnerMode = findViewById(R.id.spinnerMode)
        spinnerScenario = findViewById(R.id.spinnerScenario)
        spinnerStrokeType = findViewById(R.id.spinnerStrokeType)
        etSteps = findViewById(R.id.etSteps)
        etSeed = findViewById(R.id.etSeed)
        btnRun = findViewById(R.id.btnRunBenchmark)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvResults = findViewById(R.id.tvResults)

        setupSpinners()
        setupListeners()
    }

    private fun setupSpinners() {
        val modes = HistoryMode.values().map { it.name }
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)

        val scenarios = ScenarioType.values().map { it.title }
        spinnerScenario.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scenarios)

        val strokeTypes = StrokeType.values().map { it.name }
        spinnerStrokeType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, strokeTypes)
    }

    private fun setupListeners() {
        btnRun.setOnClickListener {
            startBenchmark()
        }

        btnExportCsv.setOnClickListener {
            exportCsv()
        }
    }

    private fun startBenchmark() {
        val selectedMode = HistoryMode.valueOf(spinnerMode.selectedItem.toString())
        val selectedScenario = ScenarioType.values()[spinnerScenario.selectedItemPosition]
        val selectedStrokeType = StrokeType.valueOf(spinnerStrokeType.selectedItem.toString())

        val steps = etSteps.text.toString().toIntOrNull() ?: 50
        val seed = etSeed.text.toString().toLongOrNull() ?: 42L

        val config = BenchmarkConfig(
            scenario = selectedScenario,
            strokeType = selectedStrokeType,
            stepCount = steps,
            seed = seed
        )

        lastConfig = config
        lastMode = selectedMode

        val historyManager: HistoryManager = when (selectedMode) {
            HistoryMode.NAIVE -> NaiveHistory()
            HistoryMode.DIRTY_RECT -> DirtyRectHistory()
            HistoryMode.TIERED -> TieredHistory.create(this)
        }

        btnRun.isEnabled = false
        btnExportCsv.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgress.text = "Executing ${selectedScenario.title}..."
        tvResults.text = "Benchmark running, please wait..."

        lifecycleScope.launch {
            val runner = BenchmarkRunner(historyManager)
            val collector = runner.runBenchmark(
                config = config,
                scope = this,
                onProgress = { current, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        progressBar.max = total
                        progressBar.progress = current
                        tvProgress.text = "Progress: $current / $total steps"
                    }
                }
            )

            lastCollector = collector
            displayResults(collector, config, selectedMode)

            btnRun.isEnabled = true
            btnExportCsv.isEnabled = true
            progressBar.visibility = View.GONE
            tvProgress.text = "Benchmark completed with status: ${collector.status.name}"
        }
    }

    private fun displayResults(
        collector: MetricsCollector,
        config: BenchmarkConfig,
        mode: HistoryMode
    ) {
        val peakJavaMb = collector.peakJavaHeapBytes / (1024.0 * 1024.0)
        val peakNativeMb = collector.peakNativeHeapBytes / (1024.0 * 1024.0)
        val peakPssMb = collector.peakPssBytes / (1024.0 * 1024.0)
        val peakTotalMb = collector.peakCombinedHeapBytes / (1024.0 * 1024.0)

        val sb = StringBuilder()
        sb.append("=== BENCHMARK REPORT ===\n")
        sb.append(String.format(Locale.US, "Mode:             %s\n", mode.name))
        sb.append(String.format(Locale.US, "Scenario:         %s\n", config.scenario.name))
        sb.append(String.format(Locale.US, "Stroke Type:      %s\n", config.strokeType.name))
        sb.append(String.format(Locale.US, "Seed:             %d\n", config.seed))
        sb.append(String.format(Locale.US, "Status:           %s\n", collector.status.name))
        if (collector.errorMessage != null) {
            sb.append(String.format(Locale.US, "Message:          %s\n", collector.errorMessage))
        }
        sb.append(String.format(Locale.US, "Completed Steps:  %d\n", collector.completedSteps))
        sb.append(String.format(Locale.US, "Duration:         %d ms\n", collector.durationMs))
        sb.append(String.format(Locale.US, "Delta GC Count:   %d\n", collector.gcDelta))
        sb.append("------------------------\n")
        sb.append(String.format(Locale.US, "Peak Java Heap:   %.2f MiB\n", peakJavaMb))
        sb.append(String.format(Locale.US, "Peak Native Heap: %.2f MiB\n", peakNativeMb))
        sb.append(String.format(Locale.US, "Peak PSS:         %.2f MiB\n", peakPssMb))
        sb.append(String.format(Locale.US, "Peak Combined:    %.2f MiB\n", peakTotalMb))
        sb.append("------------------------\n")
        sb.append(String.format(Locale.US, "Avg Step Latency: %.2f ms\n", collector.averageLatencyMs()))
        sb.append(String.format(Locale.US, "p50 Step Latency: %.2f ms\n", collector.percentileLatencyMs(50.0)))
        sb.append(String.format(Locale.US, "p95 Step Latency: %.2f ms\n", collector.percentileLatencyMs(95.0)))
        if (collector.getTierLatencies("HOT").isNotEmpty() ||
            collector.getTierLatencies("WARM").isNotEmpty() ||
            collector.getTierLatencies("COLD").isNotEmpty()) {
            sb.append("------------------------\n")
            sb.append(String.format(Locale.US, "HOT  p50/p95:     %.2f / %.2f ms\n",
                collector.percentileTierLatencyMs("HOT", 50.0),
                collector.percentileTierLatencyMs("HOT", 95.0)))
            sb.append(String.format(Locale.US, "WARM p50/p95:     %.2f / %.2f ms\n",
                collector.percentileTierLatencyMs("WARM", 50.0),
                collector.percentileTierLatencyMs("WARM", 95.0)))
            sb.append(String.format(Locale.US, "COLD p50/p95:     %.2f / %.2f ms\n",
                collector.percentileTierLatencyMs("COLD", 50.0),
                collector.percentileTierLatencyMs("COLD", 95.0)))
        }

        tvResults.text = sb.toString()
    }

    private fun exportCsv() {
        val collector = lastCollector ?: return
        val config = lastConfig ?: return
        val mode = lastMode

        val dir = File(filesDir, "benchmarks")
        val timestamp = System.currentTimeMillis()

        // 1. Export samples CSV
        val samplesCsv = CsvExporter.samplesToCsv(collector.getSamples())
        val samplesFile = CsvExporter.writeToFile(
            dir,
            "samples_${mode.name}_${config.scenario.name}_$timestamp.csv",
            samplesCsv
        )

        // 2. Export summary CSV
        val summaryCsv = CsvExporter.summaryToCsv(
            config.scenario.name,
            mode.name,
            config.seed,
            collector
        )
        val summaryFile = CsvExporter.writeToFile(
            dir,
            "summary_${mode.name}_${config.scenario.name}_$timestamp.csv",
            summaryCsv
        )

        val msg = "CSVs exported to:\n${samplesFile.absolutePath}\n${summaryFile.absolutePath}"
        Toast.makeText(this, "Exported successfully!", Toast.LENGTH_SHORT).show()
        tvResults.append("\n\n$msg")
    }
}
