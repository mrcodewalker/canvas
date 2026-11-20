package com.example.canvas.bench

/**
 * Benchmark experiment scenarios defined in Section 7 of the specification.
 */
enum class ScenarioType(
    val title: String,
    val description: String,
    val goal: String,
    val focusMetrics: String,
    val expectedWin: String
) {
    E1_DRAW_UNTIL_OOM(
        title = "E1 · Vẽ đến khi hết RAM",
        description = "Vẽ nét lớn liên tục để kiểm tra ngưỡng sập bộ nhớ (OOM) của từng chế độ.",
        goal = "Chứng minh Naive & Dirty Rect cạn RAM sớm, còn Tiered vẽ bền bỉ không bị OOM.",
        focusMetrics = "Số bước hoàn tất · Đỉnh Heap (MB) · Số lần GC thêm (+ΔGC)",
        expectedWin = "Naive sập sau ~8 bước (16 MB/nét); Tiered chạy trọn kịch bản với RAM phẳng."
    ),
    E2_DRAW_AND_UNDO_ALL(
        title = "E2 · Vẽ rồi hoàn tác hết",
        description = "Vẽ N nét rồi Undo toàn bộ; đo đỉnh tiêu thụ RAM, áp lực GC và độ trễ tổng.",
        goal = "Đo mức tiết kiệm RAM thực tế (giảm bao nhiêu × và %) và áp lực dọn rác ART GC.",
        focusMetrics = "Đỉnh Heap · Phân bổ Hot/Warm/Cold · Tỷ lệ nén (×) · p50/p95/p99/p100",
        expectedWin = "Tiered giữ đỉnh RAM ổn định thấp hơn nhiều lần so với Naive & Dirty Rect."
    ),
    E3_UNDO_BY_TIER(
        title = "E3 · Hoàn tác theo tầng",
        description = "Đẩy dữ liệu qua đủ 3 tầng Hot, Warm, Cold rồi đo riêng độ trễ Undo của từng tầng.",
        goal = "Làm rõ đánh đổi RAM–Tốc độ: Hot tức thì, Warm giải nén nhanh, Cold đọc đĩa.",
        focusMetrics = "HOT p50/p95 · WARM p50/p95 · COLD p50/p95 (ms)",
        expectedWin = "Hot nhanh nhất (~vài ms); Warm nằm trong 1 frame 60Hz; Cold cần Prefetcher hỗ trợ."
    ),
    E4_CONTINUOUS_UNDO_PREFETCH(
        title = "E4 · Hoàn tác liên tiếp (Prefetch)",
        description = "Hoàn tác liên tục nhiều bước từ Cold; đánh giá bộ nạp trước thích nghi.",
        goal = "Chứng minh Prefetcher đọc & giải nén trước giúp che giấu độ trễ đọc đĩa Cold.",
        focusMetrics = "Nạp trước trúng (Hit Rate %) · Độ trễ Undo p95, p99 & p100 (ms)",
        expectedWin = "Tỷ lệ nạp trước trúng cao (>80%), kéo p95/p99/p100 của Cold về gần mức RAM."
    ),
    E5_STRESS_AND_RECOVER(
        title = "E5 · Áp lực bộ nhớ (Stress & Hồi phục)",
        description = "Gây áp lực TRIM_MEMORY_COMPLETE tại 75% tiến trình, vẽ tiếp rồi Undo hết để kiểm tra.",
        goal = "Chứng minh Tiered tự xả Hot về 0 MB theo lệnh Android mà không mất 1 điểm ảnh nào.",
        focusMetrics = "Hot sau Stress (0 MB) · Tổng bước bảo toàn · CRC32 khớp 100%",
        expectedWin = "Naive/Dirty Rect không xả được lịch sử; Tiered xả sạch Hot và khôi phục 100%."
    ),
    E7_UNDO_REDO_BOUNDARY(
        title = "E7 · Hoàn tác/Làm lại sát ranh giới tầng",
        description = "Lặp lại chu kỳ Undo rồi Redo các bước nằm ở ranh giới Warm/Cold.",
        goal = "Đo hiệu quả của cơ chế giữ bản nén (Patch.reuse) & giữ file cũ (keptFile).",
        focusMetrics = "Thời gian nén tổng · Số lần dùng lại bản nén/file · p95/p99/p100",
        expectedWin = "Không phải nén lại và ghi đĩa lại mỗi lần người dùng Undo/Redo qua ranh giới."
    ),
    E6_CORRECTNESS_HASH(
        title = "E6 · Đúng từng điểm ảnh (CRC32)",
        description = "Vẽ nhiều nét đè lên nhau rồi Undo hết; kiểm tra mã băm CRC32 từng bit.",
        goal = "Khẳng định 100% lossless: nén + ghi đĩa + giải nén + PorterDuff.SRC không sai lệch.",
        focusMetrics = "Trạng thái SUCCESS · Khớp CRC32 từng bit với canvas trắng ban đầu",
        expectedWin = "Mọi định dạng (SOLID, RLE, DELTA, DEFLATE) khôi phục chính xác tuyệt đối 100%."
    )
}

data class BenchmarkConfig(
    val scenario: ScenarioType,
    val strokeType: StrokeType = StrokeType.LARGE,
    val stepCount: Int = 300,
    val seed: Long = 42L,
    val sampleIntervalMs: Long = 250L,
    /** Pause between two undos of the continuous-undo scenario, like a person tapping Undo (not counted as latency). */
    val undoThinkMs: Long = 0L,
    /** E7: how many steps are undone and then redone in one round, and how many rounds are made. */
    val cycleDepth: Int = 4,
    val cycleCount: Int = 10
)
