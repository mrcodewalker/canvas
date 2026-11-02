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
    E4_CONTINUOUS_UNDO_PREFETCH(
        "E4: Continuous 100 Undos (Prefetch)",
        "Consecutively undos 100 strokes. Evaluates prefetch hit rate and latency."
    ),
    E5_STRESS_AND_RECOVER(
        "E5: Stress at Step 150 & Recover",
        "Triggers memory stress at step 150, continues drawing, then undos to verify zero data loss."
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
