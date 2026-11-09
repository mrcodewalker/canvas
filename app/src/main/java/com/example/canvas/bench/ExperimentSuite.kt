package com.example.canvas.bench

import com.example.canvas.history.HistoryMode
import com.example.canvas.history.HistoryStats
import com.example.canvas.history.TierConfig
import com.example.canvas.metrics.BenchmarkStatus
import com.example.canvas.metrics.MetricsCollector
import java.util.Locale
import kotlin.math.abs

/**
 * An experiment that changes ONE thing between its variants and keeps everything else identical (same canvas,
 * same strokes from the same seed, same scenario), so any difference in the charts is caused by that one thing.
 */
class SuiteVariant(
    val label: String,
    val note: String,
    val mode: HistoryMode,
    /** Patch only the dirty tiles of a stroke instead of its whole bounding box. */
    val tiling: Boolean = false,
    val prefetch: Boolean = true,
    /** Adjusts the Tiered configuration (ignored by the other modes). */
    val tune: (TierConfig) -> TierConfig = { it },
    /** Pause between undos for this variant (overrides the suite's), to compare slow and fast undoing. */
    val thinkMs: Long? = null
)

/** One number the charts compare across variants. */
class SuiteMetric(
    val label: String,
    val unit: String,
    val lowerIsBetter: Boolean,
    val value: (VariantResult) -> Double
)

class ExperimentSuite(
    val id: String,
    val title: String,
    /** The question the experiment answers, in the user's words. */
    val question: String,
    /** What a reader should expect to see, so the chart can be judged against it. */
    val expectation: String,
    val scenario: ScenarioType,
    val strokeType: StrokeType,
    val steps: Int,
    val variants: List<SuiteVariant>,
    val metrics: List<SuiteMetric>,
    /** Index of the variant the experiment is about; variant 0 is the baseline it is compared with. */
    val heroIndex: Int = variants.lastIndex,
    /** Pause between undos, to imitate a person tapping Undo; 0 = as fast as possible. */
    val thinkMs: Long = 0L,
    /** The run ends by undoing everything and comparing the canvas with the blank one, bit by bit. */
    val verifiesPixels: Boolean = false
)

/** What repeated runs of one metric looked like: the middle value and how far the runs spread. */
class MetricStat(val median: Double, val min: Double, val max: Double, val n: Int)

/** Everything measured for one variant of a suite. */
class VariantResult(
    val variant: SuiteVariant,
    val status: BenchmarkStatus,
    val errorMessage: String?,
    val drawnSteps: Int,
    val durationMs: Long,
    val drawLatenciesMs: List<Long>,
    val undoLatenciesMs: List<Long>,
    val stats: HistoryStats,
    val peakHotMb: Double,
    val peakWarmMb: Double,
    val peakColdMb: Double,
    val peakJavaMb: Double,
    val peakNativeMb: Double,
    /** Per metric label: median and range over several runs; empty for a single run. */
    val stat: Map<String, MetricStat> = emptyMap(),
    /** Strokes the prefetcher had prepared ahead after each undo, in order. */
    val lookaheads: List<Int> = emptyList(),
    val undoCount: Int = 0,
    val redoCount: Int = 0
) {
    /** The number the charts show: the median of the runs when there were several, else the one measurement. */
    fun valueOf(m: SuiteMetric): Double = stat[m.label]?.median ?: m.value(this)

    fun withStat(s: Map<String, MetricStat>) = VariantResult(
        variant, status, errorMessage, drawnSteps, durationMs, drawLatenciesMs, undoLatenciesMs, stats,
        peakHotMb, peakWarmMb, peakColdMb, peakJavaMb, peakNativeMb, s, lookaheads, undoCount, redoCount
    )

    val historyMb: Double get() = peakHotMb + peakWarmMb + peakColdMb
    val ramTiersMb: Double get() = peakHotMb + peakWarmMb
    val prefetchHitPercent: Double
        get() {
            val total = stats.prefetchHits + stats.prefetchMisses
            return if (total == 0) 0.0 else stats.prefetchHits * 100.0 / total
        }

    fun undoPercentile(p: Double): Double = percentile(undoLatenciesMs, p)
    fun drawAverage(): Double = if (drawLatenciesMs.isEmpty()) 0.0 else drawLatenciesMs.average()
    fun undoAverage(): Double = if (undoLatenciesMs.isEmpty()) 0.0 else undoLatenciesMs.average()

    companion object {
        private const val MB = 1024.0 * 1024.0

        fun percentile(values: List<Long>, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)].toDouble()
        }

        /** Draw-phase and undo-phase latencies are told apart by the number of strokes that were requested. */
        fun from(variant: SuiteVariant, collector: MetricsCollector, requestedSteps: Int): VariantResult {
            val all = collector.getLatencies()
            val drawn = all.take(requestedSteps)
            val undone = all.drop(requestedSteps)
            return VariantResult(
                variant = variant,
                status = collector.status,
                errorMessage = collector.errorMessage,
                drawnSteps = drawn.size,
                durationMs = collector.durationMs,
                drawLatenciesMs = drawn,
                undoLatenciesMs = undone,
                stats = collector.finalStatsSnapshot,
                peakHotMb = collector.peakHotBytes / MB,
                peakWarmMb = collector.peakWarmBytes / MB,
                peakColdMb = collector.peakColdBytes / MB,
                peakJavaMb = collector.peakJavaHeapBytes / MB,
                peakNativeMb = collector.peakNativeHeapBytes / MB,
                lookaheads = collector.getLookaheads(),
                undoCount = collector.undoCount,
                redoCount = collector.redoCount
            )
        }
    }
}

/** Plain-language reading of a finished suite, computed from the numbers (never written in advance). */
class SuiteVerdict(
    val headline: String,
    val gains: List<String>,
    val costs: List<String>,
    /** Differences that the spread of the runs does not allow to call real. */
    val unclear: List<String> = emptyList(),
    /** Number of runs behind the numbers (1 = a single run). */
    val runs: Int = 1
)

object SuiteAggregation {
    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    /**
     * Turns several runs of a suite (each: one result per variant, in variant order) into one result per variant.
     * Every metric becomes its median with the lowest and highest run next to it, and the time series of the run
     * whose duration is the median stand for the variant in the line chart. One failing run (wrong pixels) makes
     * the variant failed: a median must not hide it.
     */
    fun aggregate(suite: ExperimentSuite, runs: List<List<VariantResult>>): List<VariantResult> {
        if (runs.isEmpty()) return emptyList()
        return suite.variants.indices.map { i ->
            val each = runs.map { it[i] }
            val byDuration = each.sortedBy { it.durationMs }
            val rep = byDuration[byDuration.size / 2]
            val stats = suite.metrics.associate { m ->
                val vals = each.map { m.value(it) }
                m.label to MetricStat(median(vals), vals.min(), vals.max(), vals.size)
            }
            val failed = each.firstOrNull { it.status == BenchmarkStatus.FAILED }
            (failed ?: rep).let { chosen ->
                VariantResult(
                    rep.variant, chosen.status, chosen.errorMessage, rep.drawnSteps, rep.durationMs,
                    rep.drawLatenciesMs, rep.undoLatenciesMs, rep.stats,
                    rep.peakHotMb, rep.peakWarmMb, rep.peakColdMb, rep.peakJavaMb, rep.peakNativeMb, stats,
                    rep.lookaheads, rep.undoCount, rep.redoCount
                )
            }
        }
    }
}

object ExperimentSuites {

    private val steps = SuiteMetric("Bước vẽ hoàn tất", "bước", false) { it.drawnSteps.toDouble() }
    private val history = SuiteMetric("Lịch sử lưu (đỉnh)", "MB", true) { it.historyMb }
    private val ram = SuiteMetric("RAM cho lịch sử (Hot + Warm)", "MB", true) { it.ramTiersMb }
    private val drawAvg = SuiteMetric("Thời gian lưu mỗi nét", "ms", true) { it.drawAverage() }
    private val undoP50 = SuiteMetric("Hoàn tác p50", "ms", true) { it.undoPercentile(50.0) }
    private val undoP95 = SuiteMetric("Hoàn tác p95", "ms", true) { it.undoPercentile(95.0) }
    private val undoAvg = SuiteMetric("Hoàn tác trung bình", "ms", true) { it.undoAverage() }
    private val slowUndos = SuiteMetric("Lần Undo chậm hơn 50 ms", "lần", true) { r -> r.undoLatenciesMs.count { it > 50 }.toDouble() }
    private val drawP95 = SuiteMetric("Thời gian lưu nét p95", "ms", true) { VariantResult.percentile(it.drawLatenciesMs, 95.0) }
    private val drawMax = SuiteMetric("Nét lâu nhất", "ms", true) { (it.drawLatenciesMs.maxOrNull() ?: 0L).toDouble() }
    private val lookahead = SuiteMetric("Độ sâu nạp trước TB", "nét", false) {
        if (it.lookaheads.isEmpty()) 0.0 else it.lookaheads.average()
    }
    private val reused = SuiteMetric("Lần dùng lại bản nén", "lần", false) { it.stats.reusedEncodings.toDouble() }
    private val writeMs = SuiteMetric("Ghi đĩa tổng", "ms", true) { it.stats.costWriteMs }
    private val reusedFiles = SuiteMetric("Lần khỏi ghi lại file Cold", "lần", false) { it.stats.reusedColdFiles.toDouble() }
    private val total = SuiteMetric("Tổng thời gian chạy", "s", true) { it.durationMs / 1000.0 }
    private val hit = SuiteMetric("Nạp trước trúng", "%", false) { it.prefetchHitPercent }
    private val readMs = SuiteMetric("Đọc đĩa tổng", "ms", true) { it.stats.costReadMs }
    private val inflateMs = SuiteMetric("Giải nén tổng", "ms", true) { it.stats.costInflateMs }
    private val compressMs = SuiteMetric("Nén tổng", "ms", true) { it.stats.costCompressMs + it.stats.costRecompressMs }
    private val stored = SuiteMetric("Dung lượng lưu (Warm + Cold)", "MB", true) { it.peakWarmMb + it.peakColdMb }
    private val noDeflate = SuiteMetric("Patch khỏi cần Deflate (đồng màu + RLE)", "patch", false) { (it.stats.codecSolid + it.stats.codecRle).toDouble() }
    private val codecTrial = SuiteMetric("CPU tốn cho việc chọn dạng lưu", "ms", true) { it.stats.codecTrialMs }
    private val javaHeap =SuiteMetric("Đỉnh Java heap", "MB", true) { it.peakJavaMb }

    val all: List<ExperimentSuite> = listOf(
        ExperimentSuite(
            id = "tiles",
            title = "Khung rỗng: lưu ô hay lưu cả hộp?",
            question = "Nét vẽ là một khung chữ nhật rỗng thì ứng dụng có phải lưu cả hình vuông bao quanh không?",
            expectation = "Naive và hộp bao lưu gần hết canvas cho mỗi nét nên chạm trần RAM sớm; ô thích nghi chỉ lưu viền khung.",
            scenario = ScenarioType.E2_DRAW_AND_UNDO_ALL,
            strokeType = StrokeType.HOLLOW,
            steps = 30,
            variants = listOf(
                SuiteVariant("Cả canvas", "Naive: sao chép toàn bộ canvas mỗi nét", HistoryMode.NAIVE),
                SuiteVariant("Hộp bao", "Dirty rect: chỉ hình chữ nhật bao quanh nét", HistoryMode.DIRTY_RECT),
                SuiteVariant("Ô thích nghi", "Dirty rect + ô: chỉ các ô nét vẽ chạm tới", HistoryMode.DIRTY_RECT, tiling = true)
            ),
            metrics = listOf(steps, history, drawAvg, javaHeap)
        ),
        ExperimentSuite(
            id = "prefetch",
            title = "Nạp trước: hoàn tác có nhanh hơn không?",
            question = "Khi lịch sử nằm trên đĩa, nạp và giải nén trước bước sắp hoàn tác có làm Undo nhanh hơn?",
            expectation = "Gần hết lịch sử nằm trên đĩa (Hot và Warm bị thu nhỏ). Tắt nạp trước: mỗi Undo phải đọc đĩa rồi giải nén. Bật: việc đó làm sẵn trong lúc bạn chưa bấm Undo tiếp, nên độ trễ giảm. Giữa hai lần Undo có nghỉ 120 ms như người bấm thật.",
            scenario = ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH,
            strokeType = StrokeType.DIVERSE,
            steps = 40,
            thinkMs = 120,
            variants = listOf(
                SuiteVariant("Tắt nạp trước", "Mỗi lần hoàn tác tự đọc và giải nén", HistoryMode.TIERED, tiling = false, prefetch = false,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024) }),
                SuiteVariant("Nạp trước thông minh", "Đọc sớm và giải nén sẵn theo mô hình học từ thói quen", HistoryMode.TIERED, tiling = false,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024) })
            ),
            metrics = listOf(undoP50, undoP95, undoAvg, slowUndos, hit, lookahead)
        ),
        ExperimentSuite(
            id = "cadence",
            title = "Nhịp Undo: độ sâu nạp trước có tự đổi không?",
            question = "Nạp trước không dùng K cố định. Khi người dùng Undo nhanh hơn, độ sâu nạp trước có tự tăng để kịp không?",
            expectation = "Undo chậm: đọc sẵn 1 nét là kịp. Undo nhanh: đọc một nét lâu hơn khoảng cách giữa hai lần Undo, nên độ sâu nạp trước tự tăng. Biểu đồ độ sâu theo từng lần Undo cho thấy điều đó; K cố định thì là một đường ngang.",
            scenario = ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH,
            strokeType = StrokeType.DIVERSE,
            steps = 40,
            variants = listOf(
                SuiteVariant("Undo chậm (400 ms/lần)", "Một lần Undo mỗi 400 ms", HistoryMode.TIERED, tiling = false, thinkMs = 400,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024) }),
                SuiteVariant("Undo vừa (120 ms/lần)", "Một lần Undo mỗi 120 ms", HistoryMode.TIERED, tiling = false, thinkMs = 120,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024) }),
                SuiteVariant("Undo nhanh (10 ms/lần)", "Bấm Undo liên tục", HistoryMode.TIERED, tiling = false, thinkMs = 10,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024) })
            ),
            metrics = listOf(lookahead, undoP50, undoP95, hit)
        ),
        ExperimentSuite(
            id = "compression",
            title = "Mức nén: cố định hay chọn theo dữ liệu?",
            question = "Nén mức thấp, nén mức cao hay để ứng dụng đo và tự chọn: cái nào đáng CPU nhất?",
            expectation = "Mức 1 nhanh nhưng lưu nhiều byte; mức 9 lưu ít hơn một chút nhưng tốn CPU; thích nghi phải gần mức tốt nhất của cả hai.",
            scenario = ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH,
            strokeType = StrokeType.DIVERSE,
            steps = 30,
            variants = listOf(
                SuiteVariant("Mức 1 cố định", "Nén nhanh nhất cho mọi patch", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(adaptiveCompEnabled = false, compressionLevel = 1, contentAwareCodec = false) }),
                SuiteVariant("Mức 9 cố định", "Nén sâu nhất cho mọi patch", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(adaptiveCompEnabled = false, compressionLevel = 9, contentAwareCodec = false) }),
                SuiteVariant("Thích nghi", "Warm chọn mức theo đo đạc, Cold nén sâu khi đáng", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(contentAwareCodec = false) })
            ),
            metrics = listOf(compressMs, inflateMs, stored, drawAvg, undoP50, total)
        ),
        ExperimentSuite(
            id = "codec",
            title = "Dạng lưu theo nội dung: có cần Deflate cho mọi patch?",
            question = "Patch đồng màu hay toàn mảng phẳng có đáng đi qua Deflate không, hay lưu theo cách khác rẻ hơn cả RAM lẫn CPU?",
            expectation = "Deflate thuần: mọi patch đều nén và giải nén bằng Deflate. Theo nội dung: patch một màu chỉ 5 byte, patch mảng phẳng dùng chạy-lặp (không từ điển), patch mịn thì lọc hiệu trước khi nén; mỗi loại patch tự học dạng nào rẻ nhất trên máy này. Cuối bài Undo hết và so từng điểm ảnh với canvas trắng.",
            scenario = ScenarioType.E2_DRAW_AND_UNDO_ALL,
            strokeType = StrokeType.DIVERSE,
            steps = 60,
            verifiesPixels = true,
            variants = listOf(
                SuiteVariant("Deflate thuần", "Mọi patch vào Warm đều nén bằng Deflate", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 1L * 1024 * 1024, warmBudgetBytes = 512L * 1024, contentAwareCodec = false) }),
                SuiteVariant("Theo nội dung", "Đồng màu / chạy-lặp / lọc hiệu / Deflate, học theo từng loại patch", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 1L * 1024 * 1024, warmBudgetBytes = 512L * 1024, contentAwareCodec = true) })
            ),
            metrics = listOf(compressMs, inflateMs, stored, ram, noDeflate, codecTrial, undoP50, undoP95, total)
        ),
        ExperimentSuite(
            id = "async",
            title = "Chuyển tầng chạy ngầm: nét vẽ có phải chờ không?",
            question = "Khi Hot đầy, việc nén và ghi đĩa diễn ra ngay trong lúc lưu nét hay chạy ngầm bên cạnh?",
            expectation = "Chờ: nét nào làm tràn Hot phải đợi nén và ghi đĩa xong, nên có những nét rất lâu. Chạy ngầm: nét vào Hot là xong, nén và ghi đĩa làm song song; chỉ chờ khi Hot vượt quá một ngân sách.",
            scenario = ScenarioType.E2_DRAW_AND_UNDO_ALL,
            strokeType = StrokeType.DIVERSE,
            steps = 60,
            verifiesPixels = false,
            variants = listOf(
                SuiteVariant("Chờ nén và ghi đĩa", "Mỗi nét đợi tới khi nhường chỗ xong", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024, asyncDemotion = false) }),
                SuiteVariant("Chạy ngầm", "Nén và ghi đĩa song song với việc vẽ", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024, asyncDemotion = true) })
            ),
            metrics = listOf(drawAvg, drawP95, drawMax, compressMs, writeMs, undoP50)
        ),
        ExperimentSuite(
            id = "victims",
            title = "Chọn nét xuống Cold: theo tuổi hay theo giá trị giữ lại?",
            question = "Warm đầy thì nét nào nên xuống đĩa trước: nét cũ nhất, hay nét mà giữ lại trong RAM đáng giá ít nhất?",
            expectation = "Theo tuổi: nét cũ nhất xuống đĩa dù nhỏ, nên nhiều nét nhỏ phải đọc đĩa từng cái khi Undo. Theo giá trị: nét lớn và sâu xuống đĩa trước, nhiều nét nhỏ ở lại Warm nên ít lần đọc đĩa hơn.",
            scenario = ScenarioType.E2_DRAW_AND_UNDO_ALL,
            strokeType = StrokeType.DIVERSE,
            steps = 120,
            variants = listOf(
                SuiteVariant("Theo tuổi", "Nét cũ nhất xuống Cold trước", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 1L * 1024 * 1024, warmBudgetBytes = 768L * 1024, valueAwareVictims = false) }),
                SuiteVariant("Theo giá trị giữ lại", "Nét đáng giữ ít nhất mỗi byte RAM xuống trước", HistoryMode.TIERED, tiling = true,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 1L * 1024 * 1024, warmBudgetBytes = 768L * 1024, valueAwareVictims = true) })
            ),
            metrics = listOf(readMs, undoAvg, undoP95, slowUndos, writeMs, total)
        ),
        ExperimentSuite(
            id = "boundary",
            title = "Ranh giới tầng: có nén đi nén lại không?",
            question = "Dữ liệu nằm sát ranh giới Warm/Cold, bị hoàn tác rồi làm lại liên tục: ứng dụng có phải nén lại từ đầu mỗi vòng?",
            expectation = "Không có cơ chế ranh giới: mỗi lần làm lại, các bước vừa hoàn tác bị nén lại như bước mới. Có cơ chế: bước quay về mang theo bản nén cũ nên không nén lại, không đổi mức nén.",
            scenario = ScenarioType.E7_UNDO_REDO_BOUNDARY,
            strokeType = StrokeType.DIVERSE,
            steps = 24,
            verifiesPixels = true,
            variants = listOf(
                SuiteVariant("Nén lại mỗi vòng", "Mỗi lần làm lại, patch quay về bị nén lại", HistoryMode.TIERED,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024, reuseEncodings = false) }),
                SuiteVariant("Giữ bản nén", "Patch quay về dùng lại bản nén đã có", HistoryMode.TIERED,
                    tune = { it.copy(autoBudgets = false, hotBudgetBytes = 2L * 1024 * 1024, warmBudgetBytes = 128L * 1024, reuseEncodings = true) })
            ),
            metrics = listOf(compressMs, reused, reusedFiles, writeMs, inflateMs, undoP50, undoP95, total)
        ),
        ExperimentSuite(
            id = "modes",
            title = "Ba chế độ lịch sử trên cùng một bản vẽ",
            question = "Naive, Dirty rect và Tiered khác nhau thế nào khi vẽ rồi hoàn tác hết?",
            expectation = "Naive tốn RAM nhất và dễ chạm trần; Dirty rect đỡ hơn; Tiered giữ RAM thấp nhờ nén và đẩy xuống đĩa.",
            scenario = ScenarioType.E2_DRAW_AND_UNDO_ALL,
            strokeType = StrokeType.DIVERSE,
            steps = 40,
            variants = listOf(
                SuiteVariant("Naive", "Sao chép cả canvas mỗi nét", HistoryMode.NAIVE),
                SuiteVariant("Dirty rect", "Chỉ vùng thay đổi, giữ nguyên trong RAM", HistoryMode.DIRTY_RECT, tiling = true),
                SuiteVariant("Tiered", "Hot, Warm, Cold với nén và nạp trước", HistoryMode.TIERED, tiling = true)
            ),
            metrics = listOf(steps, ram, history, undoP50, undoP95, total, javaHeap)
        )
    )

    fun byId(id: String): ExperimentSuite? = all.firstOrNull { it.id == id }

    /** The bar of one metric for every variant, with the change against the baseline. */
    fun deltaPercent(metric: SuiteMetric, baseline: VariantResult, other: VariantResult): Double? {
        val b = baseline.valueOf(metric)
        if (b <= 0.0) return null
        return (other.valueOf(metric) - b) / b * 100.0
    }

    /** True when both variants were run several times and their runs overlap: the difference is not proven. */
    fun rangesOverlap(metric: SuiteMetric, a: VariantResult, b: VariantResult): Boolean {
        val x = a.stat[metric.label] ?: return false
        val y = b.stat[metric.label] ?: return false
        return x.min <= y.max && y.min <= x.max
    }

    /** Reads the finished suite: what the hero variant gained against the baseline and what it paid for it. */
    fun verdict(suite: ExperimentSuite, results: List<VariantResult>): SuiteVerdict {
        if (results.size < 2) return SuiteVerdict("Chưa đủ dữ liệu để so sánh", emptyList(), emptyList())
        val base = results.first()
        val hero = results.getOrElse(suite.heroIndex) { results.last() }
        val gains = ArrayList<String>()
        val costs = ArrayList<String>()
        val unclear = ArrayList<String>()
        for (m in suite.metrics) {
            val d = deltaPercent(m, base, hero) ?: continue
            if (abs(d) < 5.0) continue
            val better = if (m.lowerIsBetter) d < 0 else d > 0
            val line = String.format(
                Locale.US, "%s: %s → %s %s (%s%.0f%%)", m.label,
                fmt(base.valueOf(m), m.unit), fmt(hero.valueOf(m), m.unit), m.unit, if (d >= 0) "+" else "−", abs(d)
            )
            // several runs: a difference whose runs overlap is not a difference we can claim
            if (rangesOverlap(m, base, hero)) unclear.add(line)
            else if (better) gains.add(line) else costs.add(line)
        }
        val runs = hero.stat.values.firstOrNull()?.n ?: 1
        val oom = results.filter { it.status == BenchmarkStatus.OOM_CRASH }
        val headline = when {
            oom.isNotEmpty() && hero.status != BenchmarkStatus.OOM_CRASH ->
                "${oom.joinToString { it.variant.label }} chạm trần RAM; ${hero.variant.label} chạy hết ${hero.drawnSteps} bước."
            gains.isNotEmpty() && costs.isEmpty() -> "${hero.variant.label} tốt hơn ${base.variant.label} ở ${gains.size} chỉ số, không chỉ số nào kém đi."
            gains.isNotEmpty() -> "${hero.variant.label} tốt hơn ${base.variant.label} ở ${gains.size} chỉ số nhưng phải đánh đổi ${costs.size} chỉ số."
            costs.isNotEmpty() -> "${hero.variant.label} không tốt hơn ${base.variant.label} trong bài này: ${costs.size} chỉ số kém đi."
            unclear.isNotEmpty() -> "Chưa phân biệt được: các lần chạy của hai phương án chồng lên nhau ở ${unclear.size} chỉ số."
            else -> "Hai phương án gần như ngang nhau (chênh dưới 5% ở mọi chỉ số)."
        }
        return SuiteVerdict(headline, gains, costs, unclear, runs)
    }

    fun fmt(v: Double, unit: String = ""): String = when {
        unit == "bước" || unit == "%" -> String.format(Locale.US, "%.0f", v)
        v >= 100 -> String.format(Locale.US, "%.0f", v)
        v >= 10 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }
}
