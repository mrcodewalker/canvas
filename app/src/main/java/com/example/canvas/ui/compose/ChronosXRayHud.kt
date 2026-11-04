package com.example.canvas.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.XRayCurrentTelemetry
import com.example.canvas.ui.XRayPoint
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.ElectricIndigo
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.max

@Composable
fun ChronosXRayHud(
    telemetry: XRayCurrentTelemetry,
    points: List<XRayPoint>,
    mode: HistoryMode,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onClose: () -> Unit,
    onOpenSwamSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modeColor = when (mode) {
        HistoryMode.NAIVE -> CoralRose
        HistoryMode.DIRTY_RECT -> AmberGold
        HistoryMode.TIERED -> EmeraldGreen
    }

    AuroraGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        accentColors = listOf(modeColor, NeonCyan, ElectricIndigo),
        containerColor = Color(0xF2181B21)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top Row: Live Status Dot + Title + Mode Pill + Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Bộ nhớ thời gian thực",
                    color = Color(0xFFF3F4F6),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Mode Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = modeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.55f)),
                    modifier = Modifier.clickable { onOpenSwamSheet() }
                ) {
                    Text(
                        text = mode.name,
                        color = modeColor,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Minimize / Expand pill button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF22262F),
                    border = BorderStroke(1.dp, Color(0xFF2E333E)),
                    modifier = Modifier.clickable { onToggleMinimize() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Thu gọn hoặc mở rộng",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isMinimized) "Chi tiết" else "Thu gọn",
                            color = Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF22262F))
                        .border(1.dp, Color(0xFF2E333E), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng bảng thống kê",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Minimized Quick Summary Bar
            if (isMinimized) {
                Spacer(modifier = Modifier.height(5.dp))
                val totalRam = telemetry.currentJavaHeapMb + telemetry.currentNativeHeapMb
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Heap %.1f MB (Đỉnh %.1f MB)  •  Hot %.2fM  Warm %.2fM  Cold %.2fM",
                            totalRam,
                            telemetry.peakHeapMb,
                            telemetry.hotMb,
                            telemetry.warmMb,
                            telemetry.coldMb
                        ),
                        color = Color(0xFFD1D5DB),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${telemetry.totalSteps} bước",
                        color = SkyBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Expanded Memory Profiler View
            AnimatedVisibility(
                visible = !isMinimized,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val capacityMb = max(
                        8f,
                        max(telemetry.hotMb + telemetry.warmMb + telemetry.coldMb, 4f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonArcGauge(
                            title = "Hot (RAM)",
                            valueText = String.format(Locale.US, "%.2fM", telemetry.hotMb),
                            subText = "${telemetry.hotSteps} bước",
                            fraction = (telemetry.hotMb / capacityMb).coerceIn(0f, 1f),
                            primaryColor = CoralRose,
                            secondaryColor = CoralRose,
                            sizeDp = 56.dp
                        )

                        val compLabel = if (telemetry.compressionRatio > 1.05f) {
                            String.format(Locale.US, "%.1fx", telemetry.compressionRatio)
                        } else "${telemetry.warmSteps} bước"
                        NeonArcGauge(
                            title = "Warm (Nén)",
                            valueText = String.format(Locale.US, "%.2fM", telemetry.warmMb),
                            subText = compLabel,
                            fraction = (telemetry.warmMb / capacityMb).coerceIn(0f, 1f),
                            primaryColor = AmberGold,
                            secondaryColor = AmberGold,
                            sizeDp = 56.dp
                        )

                        NeonArcGauge(
                            title = "Cold (Flash)",
                            valueText = String.format(Locale.US, "%.2fM", telemetry.coldMb),
                            subText = "${telemetry.coldSteps} tệp",
                            fraction = (telemetry.coldMb / capacityMb).coerceIn(0f, 1f),
                            primaryColor = SkyBlue,
                            secondaryColor = SkyBlue,
                            sizeDp = 56.dp
                        )

                        RamOscilloscopeCanvas(
                            points = points,
                            peakHeapMb = telemetry.peakHeapMb,
                            currentJavaMb = telemetry.currentJavaHeapMb,
                            currentNativeMb = telemetry.currentNativeHeapMb,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TelemetryMicroCard(
                            title = "Số bước lưu",
                            value = "${telemetry.totalSteps} (${telemetry.redoSteps} redo)",
                            valueColor = Color(0xFFF3F4F6),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMicroCard(
                            title = "So với Naive",
                            value = if (telemetry.totalSteps > 0) {
                                String.format(Locale.US, "-%.1f%% RAM", telemetry.ramSavedVsNaivePercent)
                            } else "0.0% RAM",
                            valueColor = RadiantMint,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMicroCard(
                            title = "Prefetch / GC",
                            value = String.format(Locale.US, "Hit %.0f%% • %d GC", telemetry.prefetchHitRate, telemetry.gcDelta),
                            valueColor = SkyBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMicroCard(
                            title = "Khử trùng lặp",
                            value = if (telemetry.deduplicatedPatches > 0) {
                                String.format(Locale.US, "%d bản (%d KB)", telemetry.deduplicatedPatches, telemetry.oomCleanerReclaimedKb.toInt())
                            } else "0 bản (0 KB)",
                            valueColor = AmberGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamOscilloscopeCanvas(
    points: List<XRayPoint>,
    peakHeapMb: Float,
    currentJavaMb: Float,
    currentNativeMb: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111317))
            .border(1.dp, Color(0xFF282D37), RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val gridColor = Color(0xFF1E222B)
            for (i in 1..2) {
                val y = h * (i / 3f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            for (i in 1..3) {
                val x = w * (i / 4f)
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            }

            if (points.isEmpty()) return@Canvas

            val now = points.last().timestampMs
            val windowDuration = 60_000f
            val windowStart = now - windowDuration
            val maxMb = max(12f, peakHeapMb * 1.18f)

            val pathTotal = Path()
            val pathJava = Path()
            val pathNative = Path()

            var first = true
            var firstX = 0f
            var lastX = 0f

            for (pt in points) {
                val tFrac = ((pt.timestampMs - windowStart) / windowDuration).coerceIn(0f, 1f)
                val px = tFrac * w
                val pyJava = h - ((pt.javaHeapMb / maxMb).coerceIn(0f, 1f) * h)
                val pyNative = h - ((pt.nativeHeapMb / maxMb).coerceIn(0f, 1f) * h)
                val pyTotal = h - ((pt.totalHeapMb / maxMb).coerceIn(0f, 1f) * h)

                if (first) {
                    firstX = px
                    pathTotal.moveTo(px, pyTotal)
                    pathJava.moveTo(px, pyJava)
                    pathNative.moveTo(px, pyNative)
                    first = false
                } else {
                    pathTotal.lineTo(px, pyTotal)
                    pathJava.lineTo(px, pyJava)
                    pathNative.lineTo(px, pyNative)
                }
                lastX = px
            }

            if (!first) {
                val fillPath = Path().apply {
                    addPath(pathTotal)
                    lineTo(lastX, h)
                    lineTo(firstX, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x284F8EF7),
                            Color(0x044F8EF7)
                        )
                    )
                )
                drawPath(
                    path = pathJava,
                    color = RadiantMint,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = pathNative,
                    color = SkyBlue,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Top Overlay Labels inside Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "Đỉnh %.1fM (J %.1f + N %.1f)",
                    peakHeapMb,
                    currentJavaMb,
                    currentNativeMb
                ),
                color = Color(0xFFD1D5DB),
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Java",
                    color = RadiantMint,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Native",
                    color = SkyBlue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom time markers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "-60s", color = Color(0xFF6B7280), fontSize = 8.sp)
            Text(text = "-30s", color = Color(0xFF6B7280), fontSize = 8.sp)
            Text(text = "Hiện tại", color = Color(0xFF9CA3AF), fontSize = 8.sp)
        }
    }
}

@Composable
private fun TelemetryMicroCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF14171D),
        border = BorderStroke(1.dp, Color(0xFF282D37))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF9CA3AF),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
