package com.example.canvas.metrics

import java.io.File
import java.io.FileWriter
import java.util.Locale

/**
 * Utility for exporting memory telemetry time-series and benchmark summaries to CSV files.
 */
object CsvExporter {

    private const val SAMPLES_HEADER =
        "timestamp_ms,elapsed_ms,java_heap_mb,native_heap_mb,pss_mb,gc_count,hot_mb,warm_mb,cold_mb,steps"

    private const val SUMMARY_HEADER =
        "scenario,mode,seed,status,completed_steps,peak_java_mb,peak_native_mb,peak_pss_mb,peak_total_mb,gc_delta,duration_ms,avg_step_ms,p50_step_ms,p95_step_ms,error"

    /**
     * Converts a list of [MemorySnapshot] into a CSV string.
     */
    fun samplesToCsv(samples: List<MemorySnapshot>): String {
        val sb = StringBuilder()
        sb.append(SAMPLES_HEADER).append("\n")

        for (s in samples) {
            sb.append(
                String.format(
                    Locale.US,
                    "%d,%d,%.2f,%.2f,%.2f,%d,%.2f,%.2f,%.2f,%d\n",
                    s.timestampMs,
                    s.elapsedMs,
                    s.javaHeapMb,
                    s.nativeHeapMb,
                    s.pssMb,
                    s.gcCount,
                    s.hotMb,
                    s.warmMb,
                    s.coldMb,
                    s.totalSteps
                )
            )
        }
        return sb.toString()
    }

    /**
     * Converts a benchmark run summary into a single-line CSV string (with header).
     */
    fun summaryToCsv(
        scenarioName: String,
        modeName: String,
        seed: Long,
        collector: MetricsCollector
    ): String {
        val sb = StringBuilder()
        sb.append(SUMMARY_HEADER).append("\n")

        val peakJavaMb = collector.peakJavaHeapBytes / (1024.0 * 1024.0)
        val peakNativeMb = collector.peakNativeHeapBytes / (1024.0 * 1024.0)
        val peakPssMb = collector.peakPssBytes / (1024.0 * 1024.0)
        val peakTotalMb = collector.peakCombinedHeapBytes / (1024.0 * 1024.0)

        val escapedError = collector.errorMessage?.replace(",", ";") ?: ""

        sb.append(
            String.format(
                Locale.US,
                "%s,%s,%d,%s,%d,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f,%.2f,%.2f,%s\n",
                scenarioName,
                modeName,
                seed,
                collector.status.name,
                collector.completedSteps,
                peakJavaMb,
                peakNativeMb,
                peakPssMb,
                peakTotalMb,
                collector.gcDelta,
                collector.durationMs,
                collector.averageLatencyMs(),
                collector.percentileLatencyMs(50.0),
                collector.percentileLatencyMs(95.0),
                escapedError
            )
        )
        return sb.toString()
    }

    /**
     * Writes CSV content to a file in [destinationDir].
     */
    fun writeToFile(destinationDir: File, fileName: String, csvContent: String): File {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val file = File(destinationDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(csvContent)
        }
        return file
    }
}
