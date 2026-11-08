package com.example.canvas.bench

/**
 * Benchmark experiment scenarios defined in Section 7 of the specification.
 */
enum class ScenarioType(val title: String, val description: String) {
    E1_DRAW_UNTIL_OOM(
        "E1 · Vẽ đến khi hết RAM",
        "Vẽ nét lớn liên tục cho đến khi gặp OutOfMemoryError; đo số bước tối đa."
    ),
    E2_DRAW_AND_UNDO_ALL(
        "E2 · Vẽ rồi hoàn tác hết",
        "Vẽ N nét rồi hoàn tác từng nét; đo RAM đỉnh và số lần GC."
    ),
    E3_UNDO_BY_TIER(
        "E3 · Hoàn tác theo tầng",
        "Dựng sẵn dữ liệu ở Hot, Warm và Cold rồi hoàn tác; đo độ trễ p50 và p95 của từng tầng."
    ),
    E4_CONTINUOUS_UNDO_PREFETCH(
        "E4 · 100 lần hoàn tác liên tiếp",
        "Hoàn tác liên tục 100 nét; đo tỷ lệ nạp trước trúng và độ trễ."
    ),
    E5_STRESS_AND_RECOVER(
        "E5 · Áp lực bộ nhớ ở bước 150",
        "Gây áp lực bộ nhớ ở bước 150, vẽ tiếp, rồi hoàn tác để kiểm tra không mất dữ liệu."
    ),
    E6_CORRECTNESS_HASH(
        "E6 · Đúng từng điểm ảnh",
        "Vẽ nhiều nét rồi hoàn tác hết; canvas phải trùng khớp từng bit với trạng thái ban đầu."
    )
}

data class BenchmarkConfig(
    val scenario: ScenarioType,
    val strokeType: StrokeType = StrokeType.LARGE,
    val stepCount: Int = 300,
    val seed: Long = 42L,
    val sampleIntervalMs: Long = 250L
)
