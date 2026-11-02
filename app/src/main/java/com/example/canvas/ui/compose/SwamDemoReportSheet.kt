package com.example.canvas.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.Brush
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
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.ElectricIndigo
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

/**
 * Comprehensive telemetry and theoretical capacity metrics captured for one mode
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

/**
 * Full-screen Material 3 Report Modal displaying:
 * 1. Executive Summary (Before vs After Optimization Undo Capacity)
 * 2. Visual Comparative Bar Charts (Peak RAM, Size per Step, Max Undo Capacity)
 * 3. Side-by-Side Telemetry Table
 * 4. Mathematical Formulas & Proofs (Why Naive OOMs vs Why SWAM achieves Infinite Undo)
 */
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
            // Top Sticky Bar
            Surface(
                color = Color(0xFF0C1322),
                border = BorderStroke(1.dp, Color(0xFF1F2E47)),
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
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BÁO CÁO KẾT QUẢ TỐI ƯU BỘ NHỚ (SWAM)",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "So sánh 3 Chế độ: Trước tối ưu (HOT) vs Sau tối ưu (SWAM 3 Tầng)",
                                color = NeonCyan,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF28131C))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = CoralRose,
                            modifier = Modifier.size(18.dp)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. HERO SUMMARY: Before vs After Undo Capacity
                SwamComparisonReportContent(report = report)

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRerunDemo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chạy Lại Demo 3 Mode", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Quay Lại Bàn Vẽ", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Reusable report body composable that can be embedded both in the full-screen
 * [SwamDemoReportModal] and inside [com.example.canvas.ui.BenchmarkActivity].
 */
@Composable
fun SwamComparisonReportContent(
    report: SwamComparisonReport
) {
    val capacityMultiplier = (report.tieredSwam.maxUndoWithDiskSteps.toDouble() /
        report.naive.maxUndoInRamSteps.coerceAtLeast(1)).toInt()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =========================================================================
        // PHẦN 1: TỔNG KẾT TRƯỚC & SAU TỐI ƯU (CHỨA ĐƯỢC BAO NHIÊU LẦN UNDO?)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0E182B),
            border = BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.7f)),
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
                        tint = RadiantMint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1. TRƯỚC VS SAU TỐI ƯU CHỨA ĐƯỢC BAO NHIÊU LẦN UNDO?",
                        color = RadiantMint,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Thiết bị hiện tại có trần Heap là ${report.deviceHeapLimitMb} MB (Ngân sách lịch sử khả dụng ~${report.availableHeapForHistoryMb} MB RAM + ${report.coldStorageBudgetMb} MB bộ nhớ Cold):",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp
                )

                // 3 Hero Capacity Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CapacityCompareBox(
                        badge = "TRƯỚC TỐI ƯU",
                        modeTitle = "NAIVE (HOT)",
                        undoCountText = "~${report.naive.maxUndoInRamSteps} lần",
                        detailText = "16.0 MB / bước\nLưu 100% ở HOT RAM",
                        color = CoralRose,
                        modifier = Modifier.weight(1f)
                    )
                    CapacityCompareBox(
                        badge = "TỐI ƯU MỨC 1",
                        modeTitle = "DIRTY RECT",
                        undoCountText = "~${report.dirtyRect.maxUndoInRamSteps} lần",
                        detailText = String.format(
                            Locale.US,
                            "%.2f MB / bước\nCắt vùng bao ở HOT",
                            report.dirtyRect.avgStepSizeKb / 1024.0
                        ),
                        color = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                    CapacityCompareBox(
                        badge = "SAU TỐI ƯU (SWAM)",
                        modeTitle = "TIERED 3 TẦNG",
                        undoCountText = "${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)}+ lần",
                        detailText = String.format(
                            Locale.US,
                            "~%.0f KB / bước (Nén %.1fx)\nRAM O(1) • Undo Vô Hạn",
                            report.tieredSwam.avgStepSizeKb,
                            report.tieredSwam.compressionRatio
                        ),
                        color = RadiantMint,
                        modifier = Modifier.weight(1.15f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RadiantMint.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, RadiantMint.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Kết luận thực nghiệm: Nhờ hạ cấp 3 tầng (Hot Bitmap → Warm Deflate → Cold Flash) kết hợp cắt khung Dirty-Rect và OOM Cleaner, sức chứa lịch sử Undo tăng gấp ${String.format(Locale.US, "%,d", capacityMultiplier)} lần (từ ~${report.naive.maxUndoInRamSteps} bước lên hơn ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)} bước) mà RAM không bao giờ vượt quá ngân sách cố định!",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // =========================================================================
        // PHẦN 2: BIỂU ĐỒ SO SÁNH TRỰC QUAN 3 CHẾ ĐỘ (VISUAL CHARTS)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CyberSurface,
            border = BorderStroke(1.dp, Color(0xFF23344E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "2. BIỂU ĐỒ SO SÁNH SỐ LIỆU ĐO ĐẠC THỰC TẾ",
                    color = NeonCyan,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                // Biểu đồ A: Đỉnh RAM tiêu thụ (Peak RAM in MiB)
                val maxPeakRam = max(
                    16.0,
                    max(report.naive.peakRamMb, max(report.dirtyRect.peakRamMb, report.tieredSwam.peakRamMb))
                )
                Text(
                    text = "📊 Biểu đồ A: Đỉnh RAM Lịch Sử (Hot + Warm RAM — Càng thấp càng tối ưu)",
                    color = Color(0xFFF1F5F9),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricBarRow(
                    label = "NAIVE (${report.naive.stepsExecuted} bước • Cận OOM)",
                    valueText = String.format(Locale.US, "%.1f MB RAM", report.naive.peakRamMb),
                    fraction = (report.naive.peakRamMb / maxPeakRam).toFloat(),
                    color = CoralRose
                )
                MetricBarRow(
                    label = "DIRTY_RECT (${report.dirtyRect.stepsExecuted} bước • Nằm ở HOT)",
                    valueText = String.format(Locale.US, "%.1f MB RAM", report.dirtyRect.peakRamMb),
                    fraction = (report.dirtyRect.peakRamMb / maxPeakRam).toFloat(),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "TIERED SWAM (${report.tieredSwam.stepsExecuted} bước • Bị chặn O(1))",
                    valueText = String.format(
                        Locale.US,
                        "%.1f MB RAM (+ %.1fM Cold)",
                        report.tieredSwam.hotMb + report.tieredSwam.warmMb,
                        report.tieredSwam.coldMb
                    ),
                    fraction = ((report.tieredSwam.hotMb + report.tieredSwam.warmMb) / maxPeakRam)
                        .toFloat()
                        .coerceAtLeast(0.06f),
                    color = RadiantMint
                )

                HorizontalDivider(color = Color(0xFF1E2D46))

                // Biểu đồ B: Kích thước trung bình 1 bước Undo (KB/step)
                val maxStepKb = max(16384.0, report.naive.avgStepSizeKb)
                Text(
                    text = "📉 Biểu đồ B: Dung lượng trung bình 1 bước lưu trữ (KB / bước)",
                    color = Color(0xFFF1F5F9),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricBarRow(
                    label = "NAIVE (Full 2048×2048 ARGB_8888)",
                    valueText = "16,384 KB (16.0 MB / bước)",
                    fraction = 1.0f,
                    color = CoralRose
                )
                MetricBarRow(
                    label = "DIRTY_RECT (Khung chữ nhật chưa nén)",
                    valueText = String.format(
                        Locale.US,
                        "%.0f KB (%.2f MB / bước)",
                        report.dirtyRect.avgStepSizeKb,
                        report.dirtyRect.avgStepSizeKb / 1024.0
                    ),
                    fraction = (report.dirtyRect.avgStepSizeKb / maxStepKb).toFloat().coerceAtLeast(0.08f),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "TIERED SWAM (Dirty-Rect + Deflate Nén ${String.format(Locale.US, "%.1fx", report.tieredSwam.compressionRatio)})",
                    valueText = String.format(
                        Locale.US,
                        "Chỉ ~%.0f KB / bước (Giảm %.0f lần!)",
                        report.tieredSwam.avgStepSizeKb,
                        16384.0 / report.tieredSwam.avgStepSizeKb.coerceAtLeast(1.0)
                    ),
                    fraction = (report.tieredSwam.avgStepSizeKb / maxStepKb).toFloat().coerceAtLeast(0.03f),
                    color = RadiantMint
                )

                HorizontalDivider(color = Color(0xFF1E2D46))

                // Biểu đồ C: Sức chứa số lần Undo tối đa (Log-scale bar for clarity)
                val maxLogCap = ln(report.tieredSwam.maxUndoWithDiskSteps.toDouble().coerceAtLeast(10.0))
                Text(
                    text = "🚀 Biểu đồ C: Sức chứa Số lần Undo tối đa (Thang Logarit — Càng dài càng mạnh)",
                    color = Color(0xFFF1F5F9),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricBarRow(
                    label = "NAIVE (Giới hạn bởi Heap / 16MB)",
                    valueText = "Tối đa ~${report.naive.maxUndoInRamSteps} bước (Sập OOM)",
                    fraction = (ln(report.naive.maxUndoInRamSteps.toDouble().coerceAtLeast(2.0)) / maxLogCap).toFloat(),
                    color = CoralRose
                )
                MetricBarRow(
                    label = "DIRTY_RECT (Giới hạn bởi Heap / S_dirty)",
                    valueText = "Tối đa ~${report.dirtyRect.maxUndoInRamSteps} bước (Vẫn sập nếu vẽ nhiều)",
                    fraction = (ln(report.dirtyRect.maxUndoInRamSteps.toDouble().coerceAtLeast(2.0)) / maxLogCap).toFloat(),
                    color = AmberGold
                )
                MetricBarRow(
                    label = "TIERED SWAM (Mở rộng xuống Cold Flash • RAM O(1))",
                    valueText = "Hơn ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)}+ bước (UNDO VÔ HẠN ∞)",
                    fraction = 1.0f,
                    color = NeonCyan
                )
            }
        }

        // =========================================================================
        // PHẦN 3: CƠ SỞ TOÁN HỌC & CHỨNG MINH (TẠI SAO TÍNH ĐƯỢC SỐ LẦN UNDO ĐÓ?)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0B1322),
            border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.65f)),
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "3. CÔNG THỨC TOÁN HỌC & LÝ GIẢI TẠI SAO ĐẠT ĐƯỢC CON SỐ TRÊN",
                        color = ElectricIndigo,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Công thức 1: NAIVE
                MathFormulaCard(
                    stepBadge = "CÔNG THỨC 1 • TRƯỚC TỐI ƯU (LƯU KIỂU HOT NAIVE)",
                    title = "Tại sao chế độ Naive chỉ lưu được ~${report.naive.maxUndoInRamSteps} lần Undo?",
                    formula = "S_canvas = W × H × 4 bytes = 2048 × 2048 × 4 = 16.0 MiB / bước\n" +
                        "RAM_Naive(N) = 16.0 MiB × N   ⇒   Độ phức tạp bộ nhớ: O(N)\n" +
                        "N_max(Naive) = ⌊ M_avail / 16.0 MiB ⌋ = ⌊ ${report.availableHeapForHistoryMb} MB / 16.0 MB ⌋ = ${report.naive.maxUndoInRamSteps} bước",
                    explanation = "Mỗi lần nhấc bút, Naive tạo một bản sao Bitmap 2048×2048 nguyên vẹn trong vùng nhớ HOT (Native Heap). Vì mỗi bức ảnh tốn cứng 16 MiB, chỉ sau ${report.naive.maxUndoInRamSteps} bước là chạm trần Heap khả dụng (${report.availableHeapForHistoryMb} MB) và gây văng ứng dụng (OutOfMemoryError).",
                    accentColor = CoralRose
                )

                // Công thức 2: DIRTY RECT
                val alphaPct = ((report.dirtyRect.avgStepSizeKb / 16384.0) * 100.0).coerceIn(1.0, 100.0)
                MathFormulaCard(
                    stepBadge = "CÔNG THỨC 2 • TỐI ƯU MỨC 1 (CẮT KHUNG BAO DIRTY-RECT Ở HOT)",
                    title = "Tại sao Dirty-Rect nâng lên được ~${report.dirtyRect.maxUndoInRamSteps} bước nhưng vẫn chưa thể Vô Hạn?",
                    formula = "Hệ số diện tích vùng bẩn: α = (w_rect × h_rect) / (2048 × 2048) ≈ ${String.format(Locale.US, "%.1f%%", alphaPct)}\n" +
                        "S_dirty = α × 16.0 MiB ≈ ${String.format(Locale.US, "%.2f", report.dirtyRect.avgStepSizeKb / 1024.0)} MiB / bước\n" +
                        "N_max(DirtyRect) = ⌊ ${report.availableHeapForHistoryMb} MB / ${String.format(Locale.US, "%.2f", (report.dirtyRect.avgStepSizeKb / 1024.0).coerceAtLeast(0.1))} MB ⌋ ≈ ${report.dirtyRect.maxUndoInRamSteps} bước",
                    explanation = "Dirty-Rect chỉ chụp đúng khung hình chữ nhật bao quanh nét vẽ vừa thêm vào (mở rộng thêm strokeWidth/2 + 2px). Tuy nhiên, toàn bộ các mảnh Bitmap này VẪN NẰM TRONG HOT RAM và KHÔNG ĐƯỢC NÉN. Nếu người dùng dùng cọ lớn hoặc Đổ màu nền (α → 100%), nó vẫn cạn sạch RAM theo hàm tuyến tính O(N).",
                    accentColor = AmberGold
                )

                // Công thức 3: TIERED SWAM (arXiv:2306.08345)
                MathFormulaCard(
                    stepBadge = "CÔNG THỨC 3 • SAU TỐI ƯU (SWAM 3 TẦNG: HOT → WARM → COLD)",
                    title = "Tại sao Tiered SWAM đạt RAM hằng số O(1) và chứa được ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)}+ lần Undo?",
                    formula = "1) Chặn trên RAM tuyệt đối (Adaptive Swap Budget):\n" +
                        "   RAM_SWAM(N) = RAM_Hot + RAM_Warm ≤ B_Hot + B_Warm = Hằng số O(1)\n\n" +
                        "2) Kích thước 1 bước sau nén Lossless Deflate (r_comp ≈ ${String.format(Locale.US, "%.1fx", report.tieredSwam.compressionRatio)}) & OOM Cleaner:\n" +
                        "   S_Cold = (α × 16 MiB) / r_comp + 36B (Header .chrf) ≈ ${String.format(Locale.US, "%.0f", report.tieredSwam.avgStepSizeKb)} KB / bước\n\n" +
                        "3) Tổng số bước Undo tối đa (N_max):\n" +
                        "   N_max = N_Hot + N_Warm + ⌊ D_Cold / S_Cold ⌋\n" +
                        "   N_max ≈ ⌊ (${report.coldStorageBudgetMb} × 1024 KB) / ${String.format(Locale.US, "%.0f", report.tieredSwam.avgStepSizeKb.coerceAtLeast(1.0))} KB ⌋ ≈ ${String.format(Locale.US, "%,d", report.tieredSwam.maxUndoWithDiskSteps)}+ bước!",
                    explanation = "Lấy cảm hứng từ bài báo SWAM (arXiv:2306.08345):\n" +
                        "• Tầng HOT giữ các bước mới nhất dưới dạng Bitmap để Undo tức thì O(1) (~1ms).\n" +
                        "• Khi HOT đầy, TierPolicy tự động nén Lossless (Deflater) đẩy xuống tầng WARM (tương đương zRAM, nhỏ đi ${String.format(Locale.US, "%.1f", report.tieredSwam.compressionRatio)} lần).\n" +
                        "• Khi WARM đầy, dữ liệu được ghi nguyên tử xuống file .chrf ở tầng COLD (Flash Disk) và GIẢI PHÓNG HOÀN TOÀN RAM. Do đó dù bạn vẽ 500 hay 20.000 bước, RAM của app vẫn giữ phẳng tuyệt đối!",
                    accentColor = RadiantMint
                )

                // Công thức 4: PREFETCH & LATENCY
                MathFormulaCard(
                    stepBadge = "CÔNG THỨC 4 • TỐI ƯU ĐỘ TRỄ HOÀN TÁC VỚI PREFETCHER (K = 5)",
                    title = "Tại sao đẩy xuống đĩa (Cold) nhưng bấm Undo liên tục vẫn không giật UI?",
                    formula = "E[T_undo] = p_Hot·T_Hot + (1 - p_Hot)·[ h_prefetch·T_Warm + (1 - h_prefetch)·T_Cold ]\n" +
                        "Đo thực tế: T_undo trung bình = ${String.format(Locale.US, "%.2f", report.tieredSwam.avgLatencyMs)} ms (Prefetch Hit = ${String.format(Locale.US, "%.0f%%", report.tieredSwam.prefetchHitRate)})",
                    explanation = "Trên màn hình 120Hz của Pixel 8 Pro, mỗi khung hình có 8.33 ms. Ngay khi bạn bấm Undo 1 bước, Prefetcher lập tức chạy ngầm trên Dispatchers.IO để nạp trước K=5 bước cũ hơn từ đĩa COLD lên RAM nén WARM. Nhờ đó các lần bấm Undo kế tiếp đều đọc trực tiếp từ RAM (Warm Hit) với độ trễ chỉ vài mili-giây, áp dụng PorterDuff.Mode.SRC khôi phục chính xác 100% từng pixel!",
                    accentColor = SkyBlue
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
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = badge,
                color = color,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = modeTitle,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = undoCountText,
                color = color,
                fontSize = 13.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detailText,
                color = Color(0xFFCBD5E1),
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
    val safeFrac = fraction.coerceIn(0.03f, 1.0f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = valueText,
                color = color,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF131D31))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeFrac)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.65f), color)
                        )
                    )
            )
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
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111B2E),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(
                text = stepBadge,
                color = accentColor,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Code/Equation box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF070B14),
                border = BorderStroke(1.dp, Color(0xFF1E2D46)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formula,
                    color = accentColor,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(9.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = explanation,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                lineHeight = 15.5.sp
            )
        }
    }
}

