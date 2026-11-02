package com.example.canvas.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.SwamEvictionStrategy
import com.example.canvas.history.TierConfig
import com.example.canvas.ui.XRayCurrentTelemetry
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.CyberSurfaceVariant
import com.example.canvas.ui.theme.EmeraldGreen
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
    var selectedTab by remember { mutableIntStateOf(0) }

    val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024f * 1024f)).coerceAtLeast(64f)
    var hotMbSlider by remember(currentConfig.hotBudgetBytes) {
        mutableFloatStateOf((currentConfig.hotBudgetBytes / (1024f * 1024f)).coerceIn(4f, maxHeapMb * 0.4f))
    }
    var warmMbSlider by remember(currentConfig.warmBudgetBytes) {
        mutableFloatStateOf((currentConfig.warmBudgetBytes / (1024f * 1024f)).coerceIn(4f, maxHeapMb * 0.4f))
    }
    var compressionLevel by remember(currentConfig.compressionLevel) {
        mutableIntStateOf(currentConfig.compressionLevel)
    }
    var infiniteUndo by remember(currentConfig.infiniteUndo) {
        mutableStateOf(currentConfig.infiniteUndo)
    }
    var evictionStrategy by remember(currentConfig.evictionStrategy) {
        mutableStateOf(currentConfig.evictionStrategy)
    }

    fun pushConfigUpdate(
        newHotMb: Float = hotMbSlider,
        newWarmMb: Float = warmMbSlider,
        newCompLevel: Int = compressionLevel,
        newInfinite: Boolean = infiniteUndo,
        newStrategy: SwamEvictionStrategy = evictionStrategy
    ) {
        onUpdateConfig(
            currentConfig.copy(
                hotBudgetBytes = (newHotMb * 1024L * 1024L).toLong(),
                warmBudgetBytes = (newWarmMb * 1024L * 1024L).toLong(),
                compressionLevel = newCompLevel,
                infiniteUndo = newInfinite,
                coldMaxSteps = if (newInfinite) Int.MAX_VALUE else 1000,
                coldMaxBytes = if (newInfinite) 4L * 1024 * 1024 * 1024 else 500L * 1024 * 1024,
                evictionStrategy = newStrategy
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SWAM Optimization Lab (arXiv:2306.08345)",
                        color = NeonCyan,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Adaptive Swap • OOM Cleaner • EOOM Cost-Aware • Infinite Undo",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberSurfaceVariant,
                contentColor = NeonCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("⚙️ Cấu Hình SWAM & Undo Vô Hạn", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("📄 Lý Thuyết Bài Báo SWAM", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Infinite Undo Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF101D33),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "♾️ Chế độ Undo Vô Hạn (Infinite Undo — O(1) RAM)",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "RAM luôn bị chặn trên bởi (Hot + Warm). Mọi bước cũ tự động nén & đẩy xuống bộ nhớ Flash (.chrf), cho phép hoàn tác hàng nghìn bước không bao giờ OOM!",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = infiniteUndo,
                                    onCheckedChange = {
                                        infiniteUndo = it
                                        pushConfigUpdate(newInfinite = it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = RadiantMint
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onBurstInfiniteUndoTest(50)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreen,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bơm +50 Nét Vẽ", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        onBurstInfiniteUndoTest(200)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test +200 Bước ∞", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 2. SWAM EOOM Eviction Policy
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF283A5A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🎯 Chính sách chọn nạn nhân (SWAM EOOM Victim Selection)",
                                color = SkyBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SwamEvictionStrategy.values().forEach { strategy ->
                                    val selected = evictionStrategy == strategy
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            evictionStrategy = strategy
                                            pushConfigUpdate(newStrategy = strategy)
                                        },
                                        label = {
                                            Text(
                                                text = strategy.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = NeonCyan
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text(
                                text = evictionStrategy.subtitle,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 3. Adaptive Swap Budgets (Hot & Warm Sliders)
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF283A5A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🔥 Ngân sách Adaptive Swap (Giới hạn Heap thiết bị: ${maxHeapMb.toInt()} MB)",
                                color = AmberGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = String.format(Locale.US, "Ngân sách HOT (Bitmap RAM): %.1f MB", hotMbSlider),
                                color = CoralRose,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = hotMbSlider,
                                onValueChange = { hotMbSlider = it },
                                onValueChangeFinished = { pushConfigUpdate() },
                                valueRange = 4f..(maxHeapMb * 0.35f).coerceAtLeast(16f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CoralRose,
                                    activeTrackColor = CoralRose
                                )
                            )

                            Text(
                                text = String.format(Locale.US, "Ngân sách WARM (zRAM Deflate): %.1f MB", warmMbSlider),
                                color = AmberGold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = warmMbSlider,
                                onValueChange = { warmMbSlider = it },
                                onValueChangeFinished = { pushConfigUpdate() },
                                valueRange = 4f..(maxHeapMb * 0.35f).coerceAtLeast(16f),
                                colors = SliderDefaults.colors(
                                    thumbColor = AmberGold,
                                    activeTrackColor = AmberGold
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mức nén Deflate: Level $compressionLevel (${if (compressionLevel == 1) "Nhanh nhất - Chuẩn zRAM" else "Nén sâu"})",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1, 3, 6, 9).forEach { lvl ->
                                        FilterChip(
                                            selected = compressionLevel == lvl,
                                            onClick = {
                                                compressionLevel = lvl
                                                pushConfigUpdate(newCompLevel = lvl)
                                            },
                                            label = { Text("L$lvl", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Prefetcher & SWAM OOM Cleaner Actions
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF283A5A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🚀 Prefetcher (Nạp trước K=5 bước từ Cold → Warm)",
                                        color = RadiantMint,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Khi Undo liên tục, tự động đọc trước 5 file Cold lên Warm ở luồng nền để tránh giật UI.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = prefetchEnabled,
                                    onCheckedChange = onTogglePrefetch
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color(0xFF22324C)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🧹 SWAM OOM Cleaner (Khử trùng lặp CRC32)",
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Đã gộp: ${telemetry.deduplicatedPatches} patch (${telemetry.oomCleanerReclaimedKb.toInt()} KB giải phóng)",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = onRunOomCleaner,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberGold,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dọn Ngay", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 2: SWAM Paper Breakdown (arXiv:2306.08345)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaperConceptCard(
                        badge = "BÀI BÁO GỐC • arXiv:2306.08345",
                        title = "SWAM: Revisiting Swap and OOMK for Improving Application Responsiveness on Mobile Devices",
                        body = "Tác giả: Geunsik Lim, Donghyun Kang, MyungJoo Ham, Young Ik Eom (Samsung Research & SKKU).\n" +
                            "Vấn đề: Trên thiết bị di động Android, cơ chế Swap truyền thống (zRAM/Flash) và OOM Killer (LMKD) hoạt động rời rạc, gây giật lag (frame drop) hoặc giết nhầm ứng dụng đang giữ trạng thái quan trọng.\n" +
                            "Kết quả của SWAM: Giảm 6.5× số lần OOM kill, tăng 41% tốc độ phản hồi ứng dụng.",
                        accentColor = NeonCyan
                    )

                    PaperConceptCard(
                        badge = "TRỤ CỘT 1 • ADAPTIVE SWAP",
                        title = "Hoán đổi đa tầng thích ứng: Hot RAM → Warm zRAM → Cold Flash",
                        body = "• Trong Kernel (SWAM): Điều phối linh hoạt việc swap trang nhớ vào RAM nén (zRAM) hay bộ nhớ Flash dựa trên áp lực bộ nhớ.\n" +
                            "• Trong Chronos Canvas: Thay vì lưu toàn bộ Bitmap 2048×2048 (16 MiB/bước), ta chỉ trích xuất vùng Dirty-Rect và đẩy qua 3 tầng:\n" +
                            "  1. HOT (Bitmap trong RAM): Undo tức thời O(1) (~1ms).\n" +
                            "  2. WARM (Deflate Lossless trong RAM - tương đương zRAM): Giảm dung lượng 8×–25×.\n" +
                            "  3. COLD (File .chrf trên Flash - tương đương Swap File): Ghi nguyên tử (temp → rename) kèm mã kiểm tra CRC32.",
                        accentColor = EmeraldGreen
                    )

                    PaperConceptCard(
                        badge = "TRỤ CỘT 2 • OOM CLEANER",
                        title = "Thu hồi vùng nhớ trùng lặp trong không gian Swap",
                        body = "• Trong Kernel (SWAM): OOM Cleaner quét và giải phóng các trang thư viện/dữ liệu trùng lặp trong vùng swap trước khi phải gọi OOM Killer.\n" +
                            "• Trong Chronos Canvas: Khi người dùng vẽ trên vùng nền giống nhau hoặc lặp lại thao tác, OOM Cleaner đối chiếu mã băm (CRC32 + RawLength) để dùng chung mảng byte nén ở tầng Warm và dùng chung file .chrf ở tầng Cold, tiết kiệm cả RAM lẫn Flash.",
                        accentColor = AmberGold
                    )

                    PaperConceptCard(
                        badge = "TRỤ CỘT 3 • EOOM & UNDO VÔ HẠN",
                        title = "Vì sao Chronos Canvas đạt được Undo Vô Hạn (Infinite Undo)?",
                        body = "• Với chế độ Naive: RAM(N) = 16 MiB × N → Tăng tuyến tính O(N), chắc chắn tràn Heap (OOM) sau 15–25 bước.\n" +
                            "• Với chế độ Tiered SWAM: RAM(N) ≤ Ngân_sách_Hot + Ngân_sách_Warm = Hằng số O(1)!\n" +
                            "• Mọi bước vượt ngân sách RAM đều được chuyển xuống Cold Flash. Với mỗi patch nén chỉ ~20–80 KB, 1 GB bộ nhớ trong lưu được hơn 20.000 bước hoàn tác mà RAM ứng dụng không hề tăng thêm!",
                        accentColor = CoralRose
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperConceptCard(
    badge: String,
    title: String,
    body: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F1829),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = badge,
                color = accentColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = body,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp
            )
        }
    }
}

