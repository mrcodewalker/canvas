package com.example.canvas.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.ColdFullPolicy
import com.example.canvas.history.SwamEvictionStrategy
import com.example.canvas.history.TierConfig
import com.example.canvas.ui.XRayCurrentTelemetry
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.CyberSurfaceVariant
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwamPaperSheet(
    currentConfig: TierConfig,
    prefetchEnabled: Boolean,
    telemetry: XRayCurrentTelemetry,
    onUpdateConfig: (TierConfig) -> Unit,
    onTogglePrefetch: (Boolean) -> Unit,
    onRunOomCleaner: () -> Unit,
    onBurstInfiniteUndoTest: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024f * 1024f)).coerceAtLeast(64f)
    var hotMb by remember(currentConfig.hotBudgetBytes) {
        mutableFloatStateOf((currentConfig.hotBudgetBytes / (1024f * 1024f)).coerceIn(1f, maxHeapMb * 0.4f))
    }
    var warmMb by remember(currentConfig.warmBudgetBytes) {
        mutableFloatStateOf((currentConfig.warmBudgetBytes / (1024f * 1024f)).coerceIn(0.25f, maxHeapMb * 0.4f))
    }
    var prefetch by remember(prefetchEnabled) { mutableStateOf(prefetchEnabled) }

    fun push(config: TierConfig) = onUpdateConfig(config)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberSurface,
        contentColor = InkText,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Các tầng bộ nhớ",
                color = InkText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                "Hot giữ ảnh gốc trong RAM, Warm giữ bản nén trong RAM, Cold giữ bản nén trên đĩa.",
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp)
            )
            Row(modifier = Modifier.padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SheetTab("Tham số", tab == 0) { tab = 0 }
                SheetTab("Cơ sở lý thuyết", tab == 1) { tab = 1 }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                if (tab == 0) {
                    LabSection("Lưu trữ")
                    LabSwitchRow(
                        "Dùng tầng Cold trên đĩa",
                        "Các bước cũ được nén xuống tệp .chrf thay vì chiếm RAM",
                        currentConfig.infiniteUndo
                    ) {
                        val on = !currentConfig.infiniteUndo
                        push(
                            currentConfig.copy(
                                infiniteUndo = on,
                                coldMaxSteps = if (on) Int.MAX_VALUE else 1000,
                                coldMaxBytes = if (on) 4L * 1024 * 1024 * 1024 else 500L * 1024 * 1024
                            )
                        )
                    }
                    LabSwitchRow(
                        "Tự tính ngân sách Hot và Warm",
                        "Theo độ dài chuỗi undo và độ nặng nét vẽ của bạn",
                        currentConfig.autoBudgets
                    ) { push(currentConfig.copy(autoBudgets = !currentConfig.autoBudgets)) }

                    LabSection("Khi Cold đầy")
                    for (policy in ColdFullPolicy.values()) {
                        ChoiceRow(policy.title, policy.subtitle, currentConfig.coldFullPolicy == policy) {
                            push(currentConfig.copy(coldFullPolicy = policy))
                        }
                    }

                    LabSection("Ngân sách RAM")
                    Text(
                        if (currentConfig.autoBudgets) "Đang tự tính; thanh trượt chỉ dùng khi tắt hoặc chưa đủ dữ liệu."
                        else "Giới hạn bạn đặt; trần heap của máy là ${maxHeapMb.toInt()} MB.",
                        color = MutedText, fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    BudgetSlider("Hot (ảnh gốc)", hotMb, 1f, maxHeapMb * 0.4f, CoralRose, !currentConfig.autoBudgets) {
                        hotMb = it
                        push(currentConfig.copy(hotBudgetBytes = (it * 1024L * 1024L).toLong()))
                    }
                    BudgetSlider("Warm (nén trong RAM)", warmMb, 0.25f, maxHeapMb * 0.4f, AmberGold, !currentConfig.autoBudgets) {
                        warmMb = it
                        push(currentConfig.copy(warmBudgetBytes = (it * 1024L * 1024L).toLong()))
                    }

                    LabSection("Nén")
                    LabSwitchRow(
                        "Chọn mức nén theo từng nét",
                        "Warm nén nhanh (mức ${currentConfig.compressionLevel}); mức của Cold do mỗi patch tự quyết, chỉ nén sâu khi đáng CPU",
                        currentConfig.adaptiveCompEnabled
                    ) { push(currentConfig.copy(adaptiveCompEnabled = !currentConfig.adaptiveCompEnabled)) }

                    LabSection("Hạ cấp và nạp trước")
                    for (strategy in SwamEvictionStrategy.values()) {
                        ChoiceRow(strategy.title, strategy.subtitle, currentConfig.evictionStrategy == strategy) {
                            push(currentConfig.copy(evictionStrategy = strategy))
                        }
                    }
                    LabSwitchRow(
                        "Nạp trước thích ứng",
                        String.format(
                            Locale.US, "Trúng %.0f%% (%d trúng, %d trượt)",
                            telemetry.prefetchHitRate, telemetry.prefetchHits, telemetry.prefetchMisses
                        ),
                        prefetch
                    ) {
                        prefetch = !prefetch
                        onTogglePrefetch(prefetch)
                    }
                    LabRow(
                        "Gộp trang trùng lặp",
                        "Đã gộp ${telemetry.deduplicatedPatches} bản, thu hồi ${telemetry.oomCleanerReclaimedKb.toInt()} KB",
                        onClick = onRunOomCleaner
                    )

                    LabSection("Thử tải")
                    LabRow("Vẽ thêm 50 nét", null) { onBurstInfiniteUndoTest(50); onDismiss() }
                    LabRow("Vẽ thêm 200 nét", null) { onBurstInfiniteUndoTest(200); onDismiss() }
                } else {
                    Paper(
                        "Ariadne · HPCA 2025 · arXiv:2502.12826",
                        "Hotness-Aware and Size-Adaptive Compressed Swap",
                        "Dữ liệu chia ba tầng: Hot (chưa nén, RAM), Warm (nén nhanh, RAM), Cold (nén sâu, Flash). Độ nóng suy ra từ khoảng cách tới đỉnh ngăn xếp undo. Nén thích ứng theo tầng, và giải nén trước dữ liệu sắp cần.",
                        SkyBlue
                    )
                    Paper(
                        "SWAM · arXiv:2306.08345",
                        "Revisiting Swap and OOMK for Improving Application Responsiveness on Mobile Devices",
                        "Hạ cấp vùng nhớ thay vì chấm dứt tiến trình khi thiếu RAM; chọn nạn nhân theo chi phí và thu hồi các trang dùng chung. Trong ứng dụng: onTrimMemory hạ cấp Hot, Warm, Cold, và các patch trùng nhau được gộp.",
                        AmberGold
                    )
                    Paper(
                        "App-aware Swap Resource Allocation · ACM 2025",
                        "Nạp trước từ Flash",
                        "Kết hợp bộ nhớ nén trên RAM với Flash và nạp trước để giảm độ trễ đọc. Trong ứng dụng: độ sâu nạp trước không cố định, tính lại ở mỗi lần undo từ nhịp undo, thời gian đọc và giải nén đo được, thói quen vẽ và bộ nhớ còn trống.",
                        EmeraldGreen
                    )
                    Paper(
                        "Phần do ứng dụng tự thiết kế",
                        "Những gì không có trong các bài báo",
                        "Lưu theo ô thích ứng thay vì một khung bao; redo hợp nhất với nét vẽ mới; dự đoán độ dài chuỗi undo từ cách vẽ; mức nén chọn riêng cho từng patch; kiểu xóa khi Cold đầy.",
                        RadiantMint
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) InkText else MutedText,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 24.dp else 0.dp)
                .background(NeonCyan)
        )
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) NeonCyan else FaintText, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(NeonCyan))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = InkText, fontSize = 15.sp)
            Text(subtitle, color = MutedText, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CyberSurfaceVariant else Color.Transparent)
            .border(1.dp, if (selected) InkText.copy(alpha = 0.5f) else CyberCardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) InkText else MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BudgetSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    color: Color,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (enabled) InkText else MutedText, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%.1f MB", value), color = MutedText, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max.coerceAtLeast(min + 1f),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = InkText,
                activeTrackColor = color,
                inactiveTrackColor = CyberCardBorder,
                disabledThumbColor = FaintText,
                disabledActiveTrackColor = CyberCardBorder,
                disabledInactiveTrackColor = CyberCardBorder
            )
        )
    }
}

@Composable
private fun Paper(source: String, title: String, body: String, accent: Color) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(source, color = MutedText, fontSize = 12.sp)
        }
        Text(title, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(body, color = MutedText, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
