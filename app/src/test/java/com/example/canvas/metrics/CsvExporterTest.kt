package com.example.canvas.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CsvExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSamplesToCsv() {
        val samples = listOf(
            MemorySnapshot(
                timestampMs = 1000L,
                elapsedMs = 0L,
                javaHeapBytes = 10 * 1024 * 1024L,
                nativeHeapBytes = 20 * 1024 * 1024L,
                pssBytes = 35 * 1024 * 1024L,
                gcCount = 2L,
                hotBytes = 16 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 1
            ),
            MemorySnapshot(
                timestampMs = 1250L,
                elapsedMs = 250L,
                javaHeapBytes = 12 * 1024 * 1024L,
                nativeHeapBytes = 22 * 1024 * 1024L,
                pssBytes = 38 * 1024 * 1024L,
                gcCount = 3L,
                hotBytes = 32 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 2
            )
        )

        val csv = CsvExporter.samplesToCsv(samples)
        val lines = csv.trim().split("\n")

        assertEquals(3, lines.size) // 1 header + 2 data rows
        assertTrue(lines[0].startsWith("timestamp_ms,elapsed_ms,java_heap_mb"))
        assertTrue(lines[1].contains("1000,0,10.00,20.00,35.00,2,16.00,0.00,0.00,1"))
        assertTrue(lines[2].contains("1250,250,12.00,22.00,38.00,3,32.00,0.00,0.00,2"))
    }

    @Test
    fun testSummaryToCsv() {
        val collector = MetricsCollector()
        collector.start()
        collector.recordSample(
            MemorySnapshot(
                timestampMs = 1000L,
                elapsedMs = 0L,
                javaHeapBytes = 16 * 1024 * 1024L,
                nativeHeapBytes = 32 * 1024 * 1024L,
                pssBytes = 50 * 1024 * 1024L,
                gcCount = 1L,
                hotBytes = 16 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 1
            )
        )
        collector.recordStepLatency(15L)
        collector.recordStepLatency(25L)
        collector.markSuccess()

        val csv = CsvExporter.summaryToCsv(
            scenarioName = "E2_DRAW_AND_UNDO_ALL",
            modeName = "NAIVE",
            seed = 42L,
            collector = collector
        )

        val lines = csv.trim().split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("scenario,mode,seed,status,completed_steps"))
        assertTrue(lines[1].contains("E2_DRAW_AND_UNDO_ALL,NAIVE,42,SUCCESS,2"))
    }

    @Test
    fun testWriteToFile() {
        val dir = tempFolder.newFolder("bench_out")
        val content = "a,b,c\n1,2,3\n"
        val file = CsvExporter.writeToFile(dir, "test.csv", content)

        assertTrue(file.exists())
        assertEquals(content, file.readText())
    }
}
