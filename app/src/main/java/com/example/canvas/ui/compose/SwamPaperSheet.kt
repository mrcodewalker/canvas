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
        mutableFloatStateOf((currentConfig.hotBudgetBytes / (1024f * 1024f)).coerceIn(1f, maxHeapMb * 0.4f))
    }
    var warmMbSlider by remember(currentConfig.warmBudgetBytes) {
        mutableFloatStateOf((currentConfig.warmBudgetBytes / (1024f * 1024f)).coerceIn(0.25f, maxHeapMb * 0.4f))
    }
    var compressionLevel by remember(currentConfig.compressionLevel) {
        mutableIntStateOf(currentConfig.compressionLevel)
    }
    var adaptiveCompEnabled by remember(currentConfig.adaptiveCompEnabled) {
        mutableStateOf(currentConfig.adaptiveCompEnabled)
    }
    var coldCompressionLevel by remember(currentConfig.coldCompressionLevel) {
        mutableIntStateOf(currentConfig.coldCompressionLevel)
    }
    var adaptiveCompBlockSize by remember(currentConfig.adaptiveCompBlockSize) {
        mutableIntStateOf(currentConfig.adaptiveCompBlockSize)
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
        newAdaptiveComp: Boolean = adaptiveCompEnabled,
        newColdCompLevel: Int = coldCompressionLevel,
        newBlockSize: Int = adaptiveCompBlockSize,
        newInfinite: Boolean = infiniteUndo,
        newStrategy: SwamEvictionStrategy = evictionStrategy
    ) {
        onUpdateConfig(
            currentConfig.copy(
                hotBudgetBytes = (newHotMb * 1024L * 1024L).toLong(),
                warmBudgetBytes = (newWarmMb * 1024L * 1024L).toLong(),
                compressionLevel = newCompLevel,
                adaptiveCompEnabled = newAdaptiveComp,
                coldCompressionLevel = newColdCompLevel,
                adaptiveCompBlockSize = newBlockSize,
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
        contentColor = Color(0xFFF3F4F6)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Column {
                Text(
                    text = "Cấu hình bộ nhớ phân tầng (Ariadne & SWAM)",
                    color = Color(0xFFF3F4F6),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Hot / Warm / Cold · HotnessOrg · AdaptiveComp · Prefetch · Khử trùng lặp",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberSurfaceVariant,
                contentColor = SkyBlue
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Cấu hình tham số", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Cơ sở lý thuyết", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Infinite Undo Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF14171D),
                        border = BorderStroke(1.dp, Color(0xFF282D37))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mở rộng lưu trữ sang tầng Cold (Flash)",
                                        color = Color(0xFFF3F4F6),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Giữ RAM trong ngân sách (Hot + Warm). Các bước cũ được nén xuống tệp .chrf trên bộ nhớ trong.",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 11.sp
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
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = SkyBlue
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
                                        containerColor = Color(0xFF22262F),
                                        contentColor = Color(0xFFF3F4F6)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Vẽ +50 bước", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        onBurstInfiniteUndoTest(200)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2563EB),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Vẽ +200 bước", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 2. Eviction Policy
                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF282D37))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Chính sách chọn bản vá hạ cấp (Eviction Strategy)",
                                color = Color(0xFFF3F4F6),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
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
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SkyBlue.copy(alpha = 0.2f),
                                            selectedLabelColor = SkyBlue
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text(
                                text = evictionStrategy.subtitle,
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 3. Tier Budgets & AdaptiveComp
                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF282D37))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Ngân sách bộ nhớ tầng Hot & Warm (Trần Heap: ${maxHeapMb.toInt()} MB)",
                                color = Color(0xFFF3F4F6),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = String.format(Locale.US, "Ngân sách tầng Hot (Bitmap RAM): %.1f MB", hotMbSlider),
                                color = CoralRose,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = hotMbSlider,
                                onValueChange = { hotMbSlider = it },
                                onValueChangeFinished = { pushConfigUpdate() },
                                valueRange = 1f..(maxHeapMb * 0.35f).coerceAtLeast(16f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CoralRose,
                                    activeTrackColor = CoralRose
                                )
                            )

                            Text(
                                text = String.format(Locale.US, "Ngân sách tầng Warm (Nén trên RAM): %.2f MB", warmMbSlider),
                                color = AmberGold,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = warmMbSlider,
                                onValueChange = { warmMbSlider = it },
                                onValueChangeFinished = { pushConfigUpdate() },
                                valueRange = 0.25f..(maxHeapMb * 0.35f).coerceAtLeast(16f),
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
                                    text = "Mức nén Warm: Level $compressionLevel",
                                    color = Color(0xFFD1D5DB),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
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

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color(0xFF282D37)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ariadne AdaptiveComp (Nén thích ứng theo tầng)",
                                        color = Color(0xFFF3F4F6),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Warm nén nhanh (L$compressionLevel); Cold tái nén sâu (L$coldCompressionLevel, khối $adaptiveCompBlockSize bản vá).",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 10.5.sp
                                    )
                                }
                                Switch(
                                    checked = adaptiveCompEnabled,
                                    onCheckedChange = {
                                        adaptiveCompEnabled = it
                                        pushConfigUpdate(newAdaptiveComp = it)
                                    }
                                )
                            }

                            if (adaptiveCompEnabled) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mức nén Cold & cỡ khối:",
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(6, 9).forEach { cLvl ->
                                            FilterChip(
                                                selected = coldCompressionLevel == cLvl,
                                                onClick = {
                                                    coldCompressionLevel = cLvl
                                                    pushConfigUpdate(newColdCompLevel = cLvl)
                                                },
                                                label = { Text("Cold L$cLvl", fontSize = 10.sp) }
                                            )
                                        }
                                        listOf(1, 4, 8).forEach { blk ->
                                            FilterChip(
                                                selected = adaptiveCompBlockSize == blk,
                                                onClick = {
                                                    adaptiveCompBlockSize = blk
                                                    pushConfigUpdate(newBlockSize = blk)
                                                },
                                                label = { Text("${blk}P", fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Prefetcher & OOM Cleaner Actions
                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF282D37))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Nạp trước bất đồng bộ (Prefetcher K = 5)",
                                        color = Color(0xFFF3F4F6),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "Khi hoàn tác liên tục, tự động nạp trước 5 tệp từ Cold lên Warm trên luồng I/O.",
                                        color = Color(0xFF9CA3AF),
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
                                color = Color(0xFF282D37)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bộ dọn trùng lặp (CRC32 Deduplication)",
                                        color = Color(0xFFF3F4F6),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "Đã gộp: ${telemetry.deduplicatedPatches} bản vá (${telemetry.oomCleanerReclaimedKb.toInt()} KB thu hồi)",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = onRunOomCleaner,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF22262F),
                                        contentColor = Color(0xFFF3F4F6)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dọn ngay", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaperConceptCard(
                        badge = "BÀI BÁO CHÍNH • ARIADNE (HPCA 2025 • arXiv:2502.12826)",
                        title = "Ariadne: Hotness-Aware and Size-Adaptive Compressed Swap (CMU-SAFARI)",
                        body = "• Mã nguồn: github.com/CMU-SAFARI/Ariadne (Đánh giá trên Google Pixel 7, Android 14).\n" +
                            "• Mô hình 3 tầng:\n" +
                            "  1. Hot: Dữ liệu chưa nén trong RAM chính.\n" +
                            "  2. Warm: Dữ liệu nén nhanh trong vùng nhớ nén trên RAM.\n" +
                            "  3. Cold: Dữ liệu nén mật độ cao lưu xuống bộ nhớ Flash.\n" +
                            "• HotnessOrg: Phân loại độ nóng dữ liệu chi phí thấp dựa trên khoảng cách tới đỉnh ngăn xếp Undo.\n" +
                            "• AdaptiveComp: Nén nhanh (Deflater Level 1) ở tầng Warm để giảm độ trễ khôi phục, và tái nén sâu (Level 9) khi chuyển xuống tầng Cold.",
                        accentColor = SkyBlue
                    )

                    PaperConceptCard(
                        badge = "BÀI BÁO BỔ SUNG 1 • ACM 2025 (doi:10.1145/3760385)",
                        title = "App-aware Swap Resource Allocation (Prefetching)",
                        body = "• Kết hợp bộ nhớ nén trên RAM và Flash Swap, sử dụng cơ chế nạp trước (Prefetching) để giảm độ trễ đọc từ bộ nhớ Flash.\n" +
                            "• Ánh xạ vào ứng dụng: Bộ Prefetcher nạp trước cửa sổ K = 5 bước từ tầng Cold lên tầng Warm khi người dùng hoàn tác liên tục.",
                        accentColor = EmeraldGreen
                    )

                    PaperConceptCard(
                        badge = "BÀI BÁO BỔ SUNG 2 • SWAM (arXiv:2306.08345)",
                        title = "SWAM: Revisiting Swap and OOMK for Improving Application Responsiveness",
                        body = "• Hạ cấp vùng nhớ thay vì chấm dứt tiến trình khi thiếu RAM, kết hợp chọn nạn nhân theo chi phí (Cost-Aware) và khử trùng lặp (OOM Cleaner).\n" +
                            "• Ánh xạ vào ứng dụng: Khi hệ thống báo thiếu bộ nhớ (onTrimMemory), ứng dụng hạ cấp Hot → Warm → Cold và gộp các bản vá trùng mã kiểm tra CRC32.",
                        accentColor = AmberGold
                    )

                    PaperConceptCard(
                        badge = "ĐỐI CHIẾU KIẾN TRÚC • OS-LEVEL ↔ APPLICATION-LEVEL",
                        title = "Ánh xạ từ trang bộ nhớ hệ điều hành sang bản vá ảnh ứng dụng",
                        body = "• Hot (DRAM chưa nén) ↔ HotStore: Bitmap Patch trong RAM\n" +
                            "• Warm (zpool / zRAM) ↔ WarmStore: Bản vá nén nhanh (Deflater L1) trong RAM\n" +
                            "• Cold (Flash Swap) ↔ ColdStore: Tệp nhị phân .chrf trên bộ nhớ trong\n" +
                            "• HotnessOrg ↔ TierPolicy (Khoảng cách tới đỉnh ngăn xếp Undo)\n" +
                            "• AdaptiveComp ↔ Warm nén nhanh L1; Cold tái nén sâu L9 theo khối\n" +
                            "• Swap Prefetch ↔ Prefetcher nạp trước K = 5 bước từ Cold lên Warm",
                        accentColor = RadiantMint
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF14171D),
        border = BorderStroke(1.dp, Color(0xFF282D37)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = badge,
                color = accentColor,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = Color(0xFFF3F4F6),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = body,
                color = Color(0xFFD1D5DB),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
