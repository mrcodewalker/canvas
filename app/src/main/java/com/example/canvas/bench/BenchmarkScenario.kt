package com.example.canvas.bench

/**
 * Benchmark experiment scenarios defined in Section 7 of the specification.
 */
enum class ScenarioType(val title: String, val description: String) {
    E1_DRAW_UNTIL_OOM(
        "E1: Draw Until OOM",
        "Draws large strokes until OutOfMemoryError is triggered. Measures max steps."
    ),
    E2_DRAW_AND_UNDO_ALL(
        "E2: Draw 300 & Undo All",
        "Draws N strokes then reverts every stroke. Measures peak memory and GC count."
    ),
    E6_CORRECTNESS_HASH(
        "E6: Pixel Correctness",
        "Draws strokes then reverts all; verifies restored canvas matches initial state bit-for-bit."
    )
}

data class BenchmarkConfig(
    val scenario: ScenarioType,
    val strokeType: StrokeType = StrokeType.LARGE,
    val stepCount: Int = 300,
    val seed: Long = 42L,
    val sampleIntervalMs: Long = 250L
)
