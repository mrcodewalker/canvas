package com.example.canvas.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberObsidian
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.max

/**
 * Real measured telemetry and derived capacity metrics captured for one mode
 * during the 3-Mode Auto Demo or Benchmark comparison.
 */
data class ModeEvaluationMetrics(
    val mode: HistoryMode,
    val stepsExecuted: Int,
    val stoppedNearOom: Boolean,
    val peakRamMb: Double,
    val avgStepSizeKb: Double,
    val hotMb: Double,
    val warmMb: Double,
    val coldMb: Double,
    val compressionRatio: Double,
    val avgLatencyMs: Double,
    val prefetchHitRate: Double,
    val oomCleanerSavedKb: Double,
    val maxUndoInRamSteps: Int,
    val maxUndoWithDiskSteps: Int
)

data class SwamComparisonReport(
    val deviceHeapLimitMb: Int,
    val availableHeapForHistoryMb: Int,
    val coldStorageBudgetMb: Int,
    val naive: ModeEvaluationMetrics,
    val dirtyRect: ModeEvaluationMetrics,
    val tieredSwam: ModeEvaluationMetrics
)

// A flat page: one background, hairline rules between sections, almost no rounding.
private val Ink = Color(0xFFF2EEE7)
private val Body = Color(0xFFCFC9BF)
private val Muted = Color(0xFF9A948A)
private val Rule = Color(0xFF34312D)
private val Track = Color(0xFF2A2825)
private val Well = Color(0xFF1D1B19)
private val Corner = 2.dp

@Composable
fun SwamDemoReportModal(
    report: SwamComparisonReport,
    onRerunDemo: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberObsidian)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("So sánh 3 chế độ lưu lịch sử", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Naive · Dirty rect · Tiered, đo trên thiết bị này", color = Muted, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier.size(48.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Body, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = Rule)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SwamComparisonReportContent(report = report)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onRerunDemo,
                        shape = RoundedCornerShape(Corner),
                        border = BorderStroke(1.dp, Rule),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("Chạy lại phép đo", fontSize = 13.sp, maxLines = 1) }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Corner),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9482D), contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("Quay lại bàn vẽ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SwamComparisonReportContent(
    report: SwamComparisonReport
) {
    val capacityMultiplier = if (report.naive.maxUndoInRamSteps > 0) {
        (report.tieredSwam.maxUndoWithDiskSteps.toDouble() / report.naive.maxUndoInRamSteps.toDouble()).toInt()
    } else 0
    val t = report.tieredSwam

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // ---------------------------------------------------------------- 1. capacity
        Section("1", "Số bước hoàn tác chứa được") {
            Text(
                "Ước tính, chưa vẽ tới giới hạn: ngân sách ${report.availableHeapForHistoryMb} MB RAM (Tiered có thêm " +
                    "${report.coldStorageBudgetMb} MB đĩa) chia cho dung lượng một bước đã đo, giả định các nét sau giống " +
                    "các nét đã vẽ. Heap tối đa của máy: ${report.deviceHeapLimitMb} MB.",
                color = Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
            Column {
                TableHeader("Chế độ", "Mỗi bước", "Số bước")
                HorizontalDivider(color = Rule)
                CapacityRow(
                    name = "Naive", note = "toàn bộ canvas, trong RAM", color = CoralRose,
                    perStep = String.format(Locale.US, "%.1f MB", report.naive.avgStepSizeKb / 1024.0),
                    steps = "${report.naive.maxUndoInRamSteps}"
                )
                HorizontalDivider(color = Rule)
                CapacityRow(
                    name = "Dirty rect", note = "chỉ vùng bao nét, trong RAM", color = AmberGold,
                    perStep = String.format(Locale.US, "%.1f KB", report.dirtyRect.avgStepSizeKb),
                    steps = "${report.dirtyRect.maxUndoInRamSteps}"
                )
                HorizontalDivider(color = Rule)
                CapacityRow(
                    name = "Tiered", note = String.format(Locale.US, "Hot · Warm · Cold, nén %.1fx", t.compressionRatio), color = RadiantMint,
                    perStep = String.format(Locale.US, "%.1f KB", t.avgStepSizeKb),
                    steps = String.format(Locale.US, "%,d", t.maxUndoWithDiskSteps)
                )
                HorizontalDivider(color = Rule)
            }
            Text(
                "Với các nét của lần chạy này, Tiered lưu trung bình ${String.format(Locale.US, "%.0f", t.avgStepSizeKb)} KB " +
                    "một bước (đã tính cả Hot, Warm và Cold), Naive ${String.format(Locale.US, "%.0f", report.naive.avgStepSizeKb)} KB. " +
                    "Nếu các nét sau cũng như vậy, cùng ngân sách chứa được cỡ ${String.format(Locale.US, "%,d", capacityMultiplier)} lần số bước. " +
                    "Nét vẽ thật phức tạp hơn nét thử, nên đây chỉ là con số ước tính.",
                color = Body, fontSize = 12.5.sp, lineHeight = 18.sp
            )
        }

        // ---------------------------------------------------------------- 2. measurements
        Section("2", "Số đo") {
            val maxPeakRam = max(16.0, max(report.naive.peakRamMb, max(report.dirtyRect.peakRamMb, t.peakRamMb)))
            Group("RAM lịch sử dùng (Hot + Warm)") {
                Bar("Naive", "${report.naive.stepsExecuted} bước", String.format(Locale.US, "%.2f MB", report.naive.peakRamMb),
                    report.naive.peakRamMb / maxPeakRam, CoralRose)
                Bar("Dirty rect", "${report.dirtyRect.stepsExecuted} bước", String.format(Locale.US, "%.2f MB", report.dirtyRect.peakRamMb),
                    report.dirtyRect.peakRamMb / maxPeakRam, AmberGold)
                Bar("Tiered",
                    String.format(Locale.US, "%d bước · Hot %.2f · Warm %.2f · Cold %.2f MB (đĩa)", t.stepsExecuted, t.hotMb, t.warmMb, t.coldMb),
                    String.format(Locale.US, "%.2f MB", t.hotMb + t.warmMb),
                    (t.hotMb + t.warmMb) / maxPeakRam, RadiantMint)
            }

            val maxStepKb = max(16384.0, report.naive.avgStepSizeKb)
            Group("Dung lượng một bước") {
                Bar("Naive", "bản sao 2048×2048", String.format(Locale.US, "%.1f KB", report.naive.avgStepSizeKb),
                    report.naive.avgStepSizeKb / maxStepKb, CoralRose)
                Bar("Dirty rect", "vùng bao chưa nén", String.format(Locale.US, "%.1f KB", report.dirtyRect.avgStepSizeKb),
                    report.dirtyRect.avgStepSizeKb / maxStepKb, AmberGold)
                Bar("Tiered",
                    String.format(Locale.US, "nén %.1fx · gộp trùng %.1f KB", t.compressionRatio, t.oomCleanerSavedKb),
                    String.format(Locale.US, "%.1f KB", t.avgStepSizeKb),
                    t.avgStepSizeKb / maxStepKb, RadiantMint)
            }

            val maxLatency = max(1.0, max(report.naive.avgLatencyMs, max(report.dirtyRect.avgLatencyMs, t.avgLatencyMs)))
            Group("Thời gian một bước") {
                Bar("Naive", "chép bitmap 16 MB", String.format(Locale.US, "%.2f ms", report.naive.avgLatencyMs),
                    report.naive.avgLatencyMs / maxLatency, CoralRose)
                Bar("Dirty rect", "cắt vùng bao", String.format(Locale.US, "%.2f ms", report.dirtyRect.avgLatencyMs),
                    report.dirtyRect.avgLatencyMs / maxLatency, AmberGold)
                Bar("Tiered", String.format(Locale.US, "khôi phục khi Undo · nạp trước trúng %.0f%%", t.prefetchHitRate),
                    String.format(Locale.US, "%.2f ms", t.avgLatencyMs),
                    t.avgLatencyMs / maxLatency, SkyBlue)
            }
        }

        // ---------------------------------------------------------------- 3. how the numbers were derived
        Section("3", "Cách tính") {
            Formula(
                tag = "Naive", color = CoralRose,
                title = "Chép cả canvas mỗi nét",
                formula = "S_canvas = 2048 × 2048 × 4 B = 16 384 KB\n" +
                    "RAM(N) = 16 MiB × N          → O(N)\n" +
                    "N_max = ⌊ ${report.availableHeapForHistoryMb} MB / 16 MB ⌋ = ${report.naive.maxUndoInRamSteps} bước",
                explanation = "Mỗi nét tạo một bản sao bitmap đầy đủ trong RAM nên ngân sách ${report.availableHeapForHistoryMb} MB chỉ đủ cho ${report.naive.maxUndoInRamSteps} bước."
            )
            val alphaPct = (report.dirtyRect.avgStepSizeKb / 16384.0) * 100.0
            Formula(
                tag = "Dirty rect", color = AmberGold,
                title = "Chỉ lưu vùng bao quanh nét",
                formula = "α = S_dirty / S_canvas = ${String.format(Locale.US, "%.2f%%", alphaPct)}\n" +
                    "S_dirty = ${String.format(Locale.US, "%.1f", report.dirtyRect.avgStepSizeKb)} KB / bước\n" +
                    "N_max = ⌊ ${report.availableHeapForHistoryMb} × 1024 KB / ${String.format(Locale.US, "%.1f", report.dirtyRect.avgStepSizeKb.coerceAtLeast(1.0))} KB ⌋ = ${report.dirtyRect.maxUndoInRamSteps} bước",
                explanation = "Mỗi bước nhỏ hơn nhiều, nhưng dữ liệu vẫn nằm trong RAM chưa nén nên vẫn tăng tuyến tính khi vẽ nhiều nét lớn."
            )
            Formula(
                tag = "Tiered", color = RadiantMint,
                title = "Hot · Warm · Cold, RAM giữ cố định",
                formula = "RAM(N) = Hot + Warm ≤ B_Hot + B_Warm     → O(1)\n" +
                    "r = RawEquivalent / TotalActual = ${String.format(Locale.US, "%.2f", t.compressionRatio)}x\n" +
                    "S_tiered = ${String.format(Locale.US, "%.1f", t.avgStepSizeKb)} KB / bước\n" +
                    "N_max = N_RAM + ⌊ ${report.coldStorageBudgetMb} × 1024 KB / S_tiered ⌋ = ${String.format(Locale.US, "%,d", t.maxUndoWithDiskSteps)} bước",
                explanation = "Nét mới nhất ở Hot (bitmap) để hoàn tác tức thì. Vượt ngân sách Hot thì nén sang Warm, rồi xuống Cold là file trên đĩa. " +
                    "Khi hoàn tác liên tục, bộ nạp trước tự tính độ sâu từ nhịp Undo, tốc độ đọc đĩa và RAM Warm còn trống."
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ pieces

@Composable
private fun Section(number: String, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(number, color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(22.dp))
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun TableHeader(a: String, b: String, c: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(a, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
        Text(b, color = Muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(c, color = Muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CapacityRow(name: String, note: String, color: Color, perStep: String, steps: String) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1.5f).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(color))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(note, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        Text(perStep, color = Body, fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(steps, color = Ink, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Body, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

/** One measurement: name and value on a line, a plain thin bar under it, the detail in small type. */
@Composable
private fun Bar(name: String, detail: String, value: String, fraction: Double, color: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Track)) {
            Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.01f, 1f)).height(4.dp).background(color))
        }
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun Formula(tag: String, color: Color, title: String, formula: String, explanation: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(tag, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Text(title, color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                formula,
                color = Ink, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().background(Well).padding(10.dp)
            )
            Text(explanation, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
