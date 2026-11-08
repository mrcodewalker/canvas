package com.example.canvas.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.canvas.ui.theme.ElectricIndigo
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.ln
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
            // Top Header Bar
            Surface(
                color = Color(0xFF1F1D1B),
                border = BorderStroke(1.dp, Color(0xFF3A3733)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Báo cáo thực nghiệm so sánh 3 chế độ lưu lịch sử",
                                color = Color(0xFFF2EEE7),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Số liệu đo trực tiếp trên thiết bị (Naive · Dirty Rect · Tiered 3 tầng)",
                                color = Color(0xFF9A948A),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2825))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color(0xFFCFC9BF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Scrollable Report Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SwamComparisonReportContent(report = report)

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRerunDemo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A2825),
                            contentColor = Color(0xFFF2EEE7)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chạy lại phép đo", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9482D),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quay lại bàn vẽ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // =========================================================================
        // PHẦN 1: TỔNG KẾT SỨC CHỨA LỊCH SỬ HOÀN TÁC
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F1D1B),
            border = BorderStroke(1.dp, Color(0xFF3A3733)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = null,
                        tint = SkyBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1. Sức chứa lịch sử hoàn tác trước và sau tối ưu",
                        color = Color(0xFFF2EEE7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Giới hạn Heap của thiết bị: ${report.deviceHeapLimitMb} MB (Ngân sách lịch sử khả dụng: ${report.availableHeapForHistoryMb} MB RAM + ${report.coldStorageBudgetMb} MB bộ nhớ Cold):",
                    color = Color(0xFF9A948A),
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CapacityCompareBox(
                        badge = "Trước tối ưu",
                        modeTitle = "Naive",
                        undoCountText = "${report.naive.maxUndoInRamSteps} bước",
                        detailText = String.format(
                            Locale.US,
                            "%.1f MB / bước\nLưu toàn bộ trong RAM",
                            report.naive.avgStepSizeKb / 1024.0
                        ),
                        color = CoralRose,
                        modifier = Modifier.weight(1f)
                    )
                    CapacityCompareBox(
                        badge = "Tối ưu mức 1",
                        modeTitle = "Dirty Rect",
                        undoCountText = "${report.dirtyRect.maxUndoInRamSteps} bước",
                        detailText = String.format(
                            Locale.US,
                            "%.1f KB / bước\nCắt vùng bao trong RAM",
                            report.dirtyRect.avgStepSizeKb
                        ),
                        color = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                    CapacityCompareBox(
                        badge = "Sau tối ưu (3 tầng)",
                        modeTitle = "Tiered",
                        undoCountText = "${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)} bước",
                        detailText = String.format(
                            Locale.US,
                            "%.1f KB / bước (Nén %.1fx)\nHot · Warm · Cold",
                            report.tieredSwam.avgStepSizeKb,
                            report.tieredSwam.compressionRatio
                        ),
                        color = RadiantMint,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1B1A18),
                    border = BorderStroke(1.dp, Color(0xFF3A3733)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nhận xét từ số liệu thực nghiệm: Khi kết hợp cắt khung bao (Dirty Rect) với phân tầng bộ nhớ Hot → Warm (nén Deflate) → Cold (tệp .chrf trên Flash), dung lượng trung bình mỗi bước giảm còn ${String.format(Locale.US, "%.1f", report.tieredSwam.avgStepSizeKb)} KB, giúp số bước hoàn tác tối đa tăng gấp ${String.format(Locale.US, "%,d", capacityMultiplier)} lần (từ ${report.naive.maxUndoInRamSteps} bước lên ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)} bước).",
                        color = Color(0xFFCFC9BF),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // =========================================================================
        // PHẦN 2: BIỂU ĐỒ ĐỐI CHIẾU SỐ LIỆU ĐO THỰC TẾ
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F1D1B),
            border = BorderStroke(1.dp, Color(0xFF3A3733)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. Biểu đồ số liệu đo thực tế",
                    color = Color(0xFFF2EEE7),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val maxPeakRam = max(
                    16.0,
                    max(report.naive.peakRamMb, max(report.dirtyRect.peakRamMb, report.tieredSwam.peakRamMb))
                )
                Text(
                    text = "A. Bộ nhớ RAM lịch sử tiêu thụ (Hot + Warm RAM)",
                    color = Color(0xFFCFC9BF),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                MetricBarRow(
                    label = "Naive (${report.naive.stepsExecuted} bước thực thi)",
                    valueText = String.format(Locale.US, "%.2f MB RAM", report.naive.peakRamMb),
                    fraction = (report.naive.peakRamMb / maxPeakRam).toFloat(),
                    color = CoralRose
                )
                MetricBarRow(
                    label = "Dirty Rect (${report.dirtyRect.stepsExecuted} bước thực thi)",
                    valueText = String.format(Locale.US, "%.2f MB RAM", report.dirtyRect.peakRamMb),
                    fraction = (report.dirtyRect.peakRamMb / maxPeakRam).toFloat(),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "Tiered (${report.tieredSwam.stepsExecuted} bước: Hot ${String.format(Locale.US, "%.2fM", report.tieredSwam.hotMb)}, Warm ${String.format(Locale.US, "%.2fM", report.tieredSwam.warmMb)}, Cold ${String.format(Locale.US, "%.2fM", report.tieredSwam.coldMb)})",
                    valueText = String.format(
                        Locale.US,
                        "%.2f MB RAM (+ %.2f MB Flash)",
                        report.tieredSwam.hotMb + report.tieredSwam.warmMb,
                        report.tieredSwam.coldMb
                    ),
                    fraction = ((report.tieredSwam.hotMb + report.tieredSwam.warmMb) / maxPeakRam).toFloat(),
                    color = RadiantMint
                )

                HorizontalDivider(color = Color(0xFF3A3733))

                val maxStepKb = max(16384.0, report.naive.avgStepSizeKb)
                Text(
                    text = "B. Dung lượng trung bình mỗi bước lưu trữ (KB / bước)",
                    color = Color(0xFFCFC9BF),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                MetricBarRow(
                    label = "Naive (Bản sao 2048×2048 ARGB_8888)",
                    valueText = String.format(Locale.US, "%.1f KB / bước", report.naive.avgStepSizeKb),
                    fraction = (report.naive.avgStepSizeKb / maxStepKb).toFloat(),
                    color = CoralRose
                )
                MetricBarRow(
                    label = "Dirty Rect (Vùng bao chưa nén)",
                    valueText = String.format(Locale.US, "%.1f KB / bước", report.dirtyRect.avgStepSizeKb),
                    fraction = (report.dirtyRect.avgStepSizeKb / maxStepKb).toFloat(),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "Tiered (Nén ${String.format(Locale.US, "%.1fx", report.tieredSwam.compressionRatio)} • Gộp trùng ${String.format(Locale.US, "%.1f KB", report.tieredSwam.oomCleanerSavedKb)})",
                    valueText = String.format(Locale.US, "%.1f KB / bước", report.tieredSwam.avgStepSizeKb),
                    fraction = (report.tieredSwam.avgStepSizeKb / maxStepKb).toFloat(),
                    color = RadiantMint
                )

                HorizontalDivider(color = Color(0xFF3A3733))

                Text(
                    text = "C. Độ trễ thực thi trung bình & tỷ lệ Prefetch Hit",
                    color = Color(0xFFCFC9BF),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                val maxLatency = max(1.0, max(report.naive.avgLatencyMs, max(report.dirtyRect.avgLatencyMs, report.tieredSwam.avgLatencyMs)))
                MetricBarRow(
                    label = "Naive (Thời gian sao chép Bitmap 16 MB)",
                    valueText = String.format(Locale.US, "%.2f ms / bước", report.naive.avgLatencyMs),
                    fraction = (report.naive.avgLatencyMs / maxLatency).toFloat(),
                    color = CoralRose
                )
                MetricBarRow(
                    label = "Dirty Rect (Thời gian cắt vùng bao)",
                    valueText = String.format(Locale.US, "%.2f ms / bước", report.dirtyRect.avgLatencyMs),
                    fraction = (report.dirtyRect.avgLatencyMs / maxLatency).toFloat(),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "Tiered (Độ trễ khôi phục Undo • Prefetch Hit ${String.format(Locale.US, "%.0f%%", report.tieredSwam.prefetchHitRate)})",
                    valueText = String.format(Locale.US, "%.2f ms / bước", report.tieredSwam.avgLatencyMs),
                    fraction = (report.tieredSwam.avgLatencyMs / maxLatency).toFloat(),
                    color = SkyBlue
                )
            }
        }

        // =========================================================================
        // PHẦN 3: CƠ SỞ TÍNH TOÁN & LÝ GIẢI KỸ THUẬT
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F1D1B),
            border = BorderStroke(1.dp, Color(0xFF3A3733)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = null,
                        tint = ElectricIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "3. Cơ sở tính toán từ số liệu thực nghiệm",
                        color = Color(0xFFF2EEE7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                MathFormulaCard(
                    stepBadge = "MÔ HÌNH 1 • NAIVE",
                    title = "Giới hạn bộ nhớ khi sao chép toàn bộ Canvas (${report.naive.maxUndoInRamSteps} bước)",
                    formula = "S_canvas = 2048 × 2048 × 4 bytes = 16,384 KB (16.0 MiB / bước)\n" +
                        "RAM_Naive(N) = 16.0 MiB × N   ⇒   O(N)\n" +
                        "N_max = ⌊ ${report.availableHeapForHistoryMb} MB / 16.0 MB ⌋ = ${report.naive.maxUndoInRamSteps} bước",
                    explanation = "Mỗi thao tác vẽ tạo một bản sao Bitmap 2048×2048 nguyên vẹn trong RAM. Với ngân sách lịch sử khả dụng ${report.availableHeapForHistoryMb} MB, ứng dụng chỉ lưu được tối đa ${report.naive.maxUndoInRamSteps} bước trước khi cạn bộ nhớ.",
                    accentColor = CoralRose
                )

                val alphaPct = (report.dirtyRect.avgStepSizeKb / 16384.0) * 100.0
                MathFormulaCard(
                    stepBadge = "MÔ HÌNH 2 • DIRTY RECT",
                    title = "Cắt khung bao nét vẽ (${report.dirtyRect.maxUndoInRamSteps} bước trong RAM)",
                    formula = "Tỷ lệ diện tích trung bình: α = S_dirty / S_canvas = ${String.format(Locale.US, "%.2f%%", alphaPct)}\n" +
                        "S_dirty = ${String.format(Locale.US, "%.1f", report.dirtyRect.avgStepSizeKb)} KB / bước\n" +
                        "N_max = ⌊ (${report.availableHeapForHistoryMb} × 1024 KB) / ${String.format(Locale.US, "%.1f", report.dirtyRect.avgStepSizeKb.coerceAtLeast(1.0))} KB ⌋ = ${report.dirtyRect.maxUndoInRamSteps} bước",
                    explanation = "Chỉ lưu vùng hình chữ nhật bao quanh nét vẽ vừa thực hiện. Tuy giảm đáng kể dung lượng mỗi bước, toàn bộ dữ liệu vẫn nằm trên RAM chưa nén nên vẫn tăng tuyến tính O(N) khi vẽ nhiều nét lớn.",
                    accentColor = AmberGold
                )

                MathFormulaCard(
                    stepBadge = "MÔ HÌNH 3 • TIERED (ARIADNE HPCA '25 + SWAM)",
                    title = "Phân tầng Hot · Warm · Cold với ngân sách RAM cố định O(1)",
                    formula = "RAM_Tiered(N) = RAM_Hot + RAM_Warm ≤ B_Hot + B_Warm = O(1)\n" +
                        "Tỷ lệ nén đo được: r = RawEquivalent / TotalActual = ${String.format(Locale.US, "%.2fx", report.tieredSwam.compressionRatio)}\n" +
                        "Dung lượng trung bình sau nén: S_tiered = ${String.format(Locale.US, "%.1f", report.tieredSwam.avgStepSizeKb)} KB / bước\n" +
                        "N_max = N_RAM + ⌊ (${report.coldStorageBudgetMb} × 1024 KB) / S_tiered ⌋ = ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)} bước",
                    explanation = "Các bước mới nhất giữ ở tầng Hot (Bitmap RAM) để hoàn tác tức thì. Khi vượt ngân sách Hot, dữ liệu được nén nhanh sang tầng Warm (Deflater L1) và tái nén sâu (AdaptiveComp L9) xuống tầng Cold (.chrf trên bộ nhớ Flash), đồng thời Prefetcher nạp trước thích ứng (độ sâu tự tính từ nhịp undo, tốc độ đọc đĩa, độ dài chuỗi undo và RAM Warm còn trống) khi hoàn tác liên tục.",
                    accentColor = RadiantMint
                )
            }
        }
    }
}

@Composable
private fun CapacityCompareBox(
    badge: String,
    modeTitle: String,
    undoCountText: String,
    detailText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B1A18),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = badge,
                color = color,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = modeTitle,
                color = Color(0xFFF2EEE7),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = undoCountText,
                color = color,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detailText,
                color = Color(0xFF9A948A),
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MetricBarRow(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color
) {
    val safeFrac = fraction.coerceIn(0.01f, 1.0f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFCFC9BF),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueText,
                color = color,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF34312D))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(safeFrac)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun MathFormulaCard(
    stepBadge: String,
    title: String,
    formula: String,
    explanation: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B1A18),
        border = BorderStroke(1.dp, Color(0xFF3A3733)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(
                text = stepBadge,
                color = accentColor,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = Color(0xFFF2EEE7),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF171614),
                border = BorderStroke(1.dp, Color(0xFF2A2825)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formula,
                    color = Color(0xFFE4DFD6),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.5.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = explanation,
                color = Color(0xFF9A948A),
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}
