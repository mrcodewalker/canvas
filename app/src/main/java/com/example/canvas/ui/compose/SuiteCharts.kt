@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.canvas.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.bench.ExperimentSuite
import com.example.canvas.bench.ExperimentSuites
import com.example.canvas.bench.SuiteMetric
import com.example.canvas.bench.VariantResult
import com.example.canvas.history.HistoryMode
import com.example.canvas.metrics.BenchmarkStatus
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.CyberSurfaceVariant
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** First variant is the baseline (stone), the last is the one the experiment is about (green). */
fun variantColor(index: Int, count: Int): Color = when {
    index == count - 1 -> EmeraldGreen
    index == 0 -> Color(0xFF8D877F)
    else -> AmberGold
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

/** Colour key shared by every chart of a suite. */
@Composable
fun SuiteLegend(suite: ExperimentSuite) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        suite.variants.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(variantColor(i, suite.variants.size)))
                Spacer(Modifier.width(6.dp))
                Text(v.label, color = MutedText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SuiteVerdictCard(suite: ExperimentSuite, results: List<VariantResult>, finished: Boolean) {
    if (results.isEmpty()) return
    val verdict = ExperimentSuites.verdict(suite, results)
    Card {
        Text(if (finished) "Kết luận từ số đo" else "Kết quả tạm thời", color = MutedText, fontSize = 12.sp)
        Text(verdict.headline, color = InkText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
        if (verdict.gains.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                verdict.gains.forEach { Text("▲ $it", color = EmeraldGreen, fontSize = 12.5.sp, lineHeight = 17.sp) }
            }
        }
        if (verdict.unclear.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Chưa phân biệt được (các lần chạy chồng nhau)", color = MutedText, fontSize = 11.5.sp)
                verdict.unclear.forEach { Text("≈ $it", color = MutedText, fontSize = 12.5.sp, lineHeight = 17.sp) }
            }
        }
        if (verdict.costs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Đánh đổi", color = MutedText, fontSize = 11.5.sp)
                verdict.costs.forEach { Text("▼ $it", color = AmberGold, fontSize = 12.5.sp, lineHeight = 17.sp) }
            }
        }
        if (suite.verifiesPixels) {
            val bad = results.filter { it.status != BenchmarkStatus.SUCCESS }
            if (bad.isEmpty() && finished) {
                Text("✓ Undo hết về canvas trắng: trùng từng điểm ảnh ở mọi phương án", color = EmeraldGreen, fontSize = 12.5.sp, lineHeight = 17.sp)
            } else if (bad.isNotEmpty()) {
                Text("✗ Sai điểm ảnh hoặc lỗi ở: ${bad.joinToString { it.variant.label }}", color = CoralRose, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
        Text(
            if (verdict.runs > 1) "Trung vị của ${verdict.runs} lần chạy (bỏ lần làm nóng), cùng seed, một thiết bị. Chênh dưới 5%, hoặc khi các lần chạy chồng nhau, không được tính là khác biệt."
            else "Một thiết bị, một lần chạy, cùng seed. Chênh dưới 5% không được tính là khác biệt.",
            color = FaintText, fontSize = 11.sp, lineHeight = 15.sp
        )
    }
}

@Composable
fun MetricBarsCard(metric: SuiteMetric, results: List<VariantResult>) {
    val values = results.map { it.valueOf(metric) }
    val maxV = max(values.maxOrNull() ?: 0.0, 1e-9)
    val best = if (metric.lowerIsBetter) values.indices.minByOrNull { values[it] } else values.indices.maxByOrNull { values[it] }
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(metric.label, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            Text(if (metric.lowerIsBetter) "${metric.unit} · thấp hơn là tốt" else "${metric.unit} · cao hơn là tốt", color = FaintText, fontSize = 11.sp)
        }
        results.forEachIndexed { i, r ->
            val frac by animateFloatAsState(
                targetValue = (values[i] / maxV).toFloat().coerceIn(0.015f, 1f),
                animationSpec = tween(650),
                label = "bar"
            )
            val color = variantColor(i, results.size)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.variant.label, color = MutedText, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 2)
                        val st = r.stat[metric.label]
                        if (st != null) {
                            Text(
                                "${ExperimentSuites.fmt(st.min, metric.unit)}–${ExperimentSuites.fmt(st.max, metric.unit)}",
                                color = FaintText, fontSize = 10.5.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        ExperimentSuites.fmt(values[i], metric.unit),
                        color = if (i == best) InkText else MutedText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        fontWeight = if (i == best) FontWeight.Bold else FontWeight.Medium
                    )
                    if (i > 0) {
                        val d = ExperimentSuites.deltaPercent(metric, results[0], r)
                        Spacer(Modifier.width(8.dp))
                        if (d != null && abs(d) >= 1.0) {
                            val good = if (metric.lowerIsBetter) d < 0 else d > 0
                            Text(
                                String.format(Locale.US, "%s%.0f%%", if (d >= 0) "+" else "−", abs(d)),
                                color = if (good) EmeraldGreen else AmberGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background((if (good) EmeraldGreen else AmberGold).copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        } else if (d != null) {
                            Text("≈", color = FaintText, fontSize = 12.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(CyberSurfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                }
            }
        }
        if (results.any { it.status == BenchmarkStatus.OOM_CRASH }) {
            Text(
                "Phương án chạm trần RAM dừng sớm nên các số của nó chỉ tính đến lúc dừng.",
                color = FaintText, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}

/** Latency of every step, one line per variant: spikes (disk reads, decompression) are visible as peaks. */
@Composable
fun LatencyLineChart(results: List<VariantResult>) {
    val useUndo = results.any { it.undoLatenciesMs.size > 1 }
    val series = results.map { if (useUndo) it.undoLatenciesMs else it.drawLatenciesMs }
    if (series.all { it.size < 2 }) return
    val top = max(1L, series.maxOf { s -> s.maxOrNull() ?: 0L }).toFloat()
    val longest = series.maxOf { it.size }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (useUndo) "Độ trễ từng lần hoàn tác" else "Thời gian lưu từng nét",
                color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("đỉnh ${top.toInt()} ms", color = FaintText, fontSize = 11.sp, maxLines = 1)
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            for (g in 0..2) {
                val y = h * g / 2f
                drawLine(CyberCardBorder, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            series.forEachIndexed { i, s ->
                if (s.size < 2) return@forEachIndexed
                val path = Path()
                s.forEachIndexed { k, v ->
                    val x = w * k / (longest - 1).coerceAtLeast(1)
                    val y = h - (v / top) * h * 0.96f - 2f
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path, variantColor(i, series.size),
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (useUndo) "lần hoàn tác đầu" else "nét đầu", color = FaintText, fontSize = 10.5.sp)
            Text(if (useUndo) "lần cuối" else "nét cuối", color = FaintText, fontSize = 10.5.sp)
        }
    }
}

private val costColors = listOf(AmberGold, CoralRose, SkyBlue, Color(0xFFA78BFA), Color(0xFF94A3B8))
private val costNames = listOf("Nén", "Giải nén", "Đọc đĩa", "Ghi đĩa", "Vẽ patch")

/** Where the time of the history pipeline went, per variant (only the tiered mode has these stages). */
@Composable
fun CostBreakdownCard(results: List<VariantResult>) {
    val rows = results.map { r ->
        val s = r.stats
        listOf(s.costCompressMs + s.costRecompressMs, s.costInflateMs, s.costReadMs, s.costWriteMs, s.costApplyMs)
    }
    val maxTotal = rows.maxOfOrNull { it.sum() } ?: 0.0
    if (maxTotal <= 0.0) return
    Card {
        Text("Chi phí xử lý lịch sử", color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Tổng thời gian CPU và đĩa mỗi công đoạn tiêu tốn (ms)", color = MutedText, fontSize = 12.sp)
        results.forEachIndexed { i, r ->
            val parts = rows[i]
            val sum = parts.sum()
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(variantColor(i, results.size)))
                    Spacer(Modifier.width(6.dp))
                    Text(r.variant.label, color = MutedText, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(String.format(Locale.US, "%.0f ms", sum), color = InkText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Row(
                    Modifier.fillMaxWidth((sum / maxTotal).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                ) {
                    parts.forEachIndexed { k, v ->
                        if (v > 0.0) Box(Modifier.weight(v.toFloat()).fillMaxSize().background(costColors[k]))
                    }
                    if (sum <= 0.0) Box(Modifier.fillMaxSize().background(CyberSurfaceVariant))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            costNames.forEachIndexed { k, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(costColors[k]))
                    Spacer(Modifier.width(4.dp))
                    Text(n, color = FaintText, fontSize = 10.5.sp)
                }
            }
        }
    }
}

/** Hot / Warm / Cold peak per variant as stacked bars on one shared scale. */
@Composable
fun TierSplitCard(results: List<VariantResult>) {
    val maxTotal = results.maxOfOrNull { it.historyMb } ?: 0.0
    if (maxTotal <= 0.0) return
    Card {
        Text("Lịch sử nằm ở tầng nào", color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        results.forEachIndexed { i, r ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(variantColor(i, results.size)))
                    Spacer(Modifier.width(6.dp))
                    Text(r.variant.label, color = MutedText, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(String.format(Locale.US, "%.1f MB", r.historyMb), color = InkText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Row(
                    Modifier.fillMaxWidth((r.historyMb / maxTotal).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                ) {
                    listOf(r.peakHotMb to CoralRose, r.peakWarmMb to AmberGold, r.peakColdMb to SkyBlue).forEach { (v, c) ->
                        if (v > 0.0) Box(Modifier.weight(v.toFloat()).fillMaxSize().background(c))
                    }
                }
                if (r.variant.mode == HistoryMode.TIERED) {
                    Text(
                        String.format(Locale.US, "Hot %.1f · Warm %.1f · Cold %.1f MB", r.peakHotMb, r.peakWarmMb, r.peakColdMb),
                        color = FaintText, fontSize = 11.sp
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Hot" to CoralRose, "Warm" to AmberGold, "Cold" to SkyBlue).forEach { (n, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(n, color = FaintText, fontSize = 10.5.sp)
                }
            }
        }
    }
}

/** Everything a finished (or running) suite shows, in reading order. */
@Composable
fun SuiteResultView(suite: ExperimentSuite, results: List<VariantResult>, finished: Boolean) {
    if (results.isEmpty()) return
    SuiteVerdictCard(suite, results, finished)
    SuiteLegend(suite)
    for (m in suite.metrics) MetricBarsCard(m, results)
    LatencyLineChart(results)
    CostBreakdownCard(results)
    TierSplitCard(results)
    Text(
        "Mỗi phương án chạy lần lượt trên cùng canvas, cùng chuỗi nét từ cùng seed; chỉ khác đúng một thiết lập.",
        color = FaintText, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
