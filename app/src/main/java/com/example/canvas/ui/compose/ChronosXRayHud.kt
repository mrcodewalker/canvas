package com.example.canvas.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    val pulseTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEB080E1A),
        border = BorderStroke(1.dp, Color(0xFF23344E)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top Row: Live Indicator + Title + Mode Pill + Infinite Undo Badge + Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(RadiantMint.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "SWAM X-RAY",
                    color = SkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Mode Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = modeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.7f)),
                    modifier = Modifier.clickable { onOpenSwamSheet() }
                ) {
                    Text(
                        text = mode.name,
                        color = modeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (mode == HistoryMode.TIERED && telemetry.infiniteUndoEnabled) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NeonCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.clickable { onOpenSwamSheet() }
                    ) {
                        Text(
                            text = "∞ UNDO",
                            color = NeonCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Minimize / Expand pill button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF152033),
                    border = BorderStroke(1.dp, Color(0xFF2B3E5E)),
                    modifier = Modifier.clickable { onToggleMinimize() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Thu gọn / Mở rộng",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isMinimized) "Mở rộng" else "Thu gọn",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A131B))
                        .border(1.dp, Color(0xFF6E2035), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng X-Ray",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Minimized Quick Bar
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
                            "RAM %.1fM (Đỉnh %.1fM) • 🔥%.1fM ⚡%.1fM ❄️%.1fM",
                            totalRam,
                            telemetry.peakHeapMb,
                            telemetry.hotMb,
                            telemetry.warmMb,
                            telemetry.coldMb
                        ),
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${telemetry.totalSteps} bước",
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Expanded Full Telemetry HUD
            AnimatedVisibility(
                visible = !isMinimized,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val capacityMb = max(
                        1f,
                        max(telemetry.hotMb + telemetry.warmMb + telemetry.coldMb, telemetry.peakHeapMb)
                    )

                    // 1. HOT TIER GAUGE
                    SwamTierGaugeRow(
                        label = "🔥 HOT (RAM Bitmap • O(1))",
                        valueText = String.format(Locale.US, "%.2f MB (%d bước)", telemetry.hotMb, telemetry.hotSteps),
                        fraction = (telemetry.hotMb / capacityMb).coerceIn(0f, 1f),
                        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
                        accentColor = CoralRose
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    // 2. WARM TIER GAUGE
                    val compTag = if (telemetry.compressionRatio > 1.05f) {
                        String.format(Locale.US, " • Nén %.1fx", telemetry.compressionRatio)
                    } else ""
                    SwamTierGaugeRow(
                        label = "⚡ WARM (zRAM Deflate$compTag)",
                        valueText = String.format(Locale.US, "%.2f MB (%d bước)", telemetry.warmMb, telemetry.warmSteps),
                        fraction = (telemetry.warmMb / capacityMb).coerceIn(0f, 1f),
                        gradientColors = listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
                        accentColor = AmberGold
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    // 3. COLD TIER GAUGE
                    SwamTierGaugeRow(
                        label = "❄️ COLD (Flash Swap .chrf • ∞ Disk)",
                        valueText = String.format(Locale.US, "%.2f MB (%d bước)", telemetry.coldMb, telemetry.coldSteps),
                        fraction = (telemetry.coldMb / capacityMb).coerceIn(0f, 1f),
                        gradientColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                        accentColor = NeonCyan
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. 60-Second Real-Time Oscilloscope Chart
                    RamOscilloscopeCanvas(
                        points = points,
                        peakHeapMb = telemetry.peakHeapMb,
                        currentJavaMb = telemetry.currentJavaHeapMb,
                        currentNativeMb = telemetry.currentNativeHeapMb,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 5. Responsive 2x2 Bento Grid for Telemetry Stat Cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TelemetryMicroCard(
                                title = "LỊCH SỬ HOÀN TÁC (STEPS)",
                                value = "${telemetry.totalSteps} bước (${telemetry.redoSteps} redo)",
                                valueColor = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMicroCard(
                                title = "HIỆU QUẢ BỘ NHỚ VS NAIVE",
                                value = if (telemetry.totalSteps > 0) {
                                    String.format(Locale.US, "Tiết kiệm %.1f%% RAM", telemetry.ramSavedVsNaivePercent)
                                } else "0.0% (Chưa vẽ)",
                                valueColor = RadiantMint,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TelemetryMicroCard(
                                title = "PREFETCH HIT / ART GC",
                                value = String.format(Locale.US, "Hit %.0f%%  •  +%d lần GC", telemetry.prefetchHitRate, telemetry.gcDelta),
                                valueColor = SkyBlue,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryMicroCard(
                                title = "SWAM OOM CLEANER",
                                value = if (telemetry.deduplicatedPatches > 0) {
                                    String.format(Locale.US, "Gộp %d patch (%d KB)", telemetry.deduplicatedPatches, telemetry.oomCleanerReclaimedKb.toInt())
                                } else "Sẵn sàng khử trùng lặp",
                                valueColor = AmberGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwamTierGaugeRow(
    label: String,
    valueText: String,
    fraction: Float,
    gradientColors: List<Color>,
    accentColor: Color
) {
    val pct = (fraction * 100f).toInt().coerceIn(0, 100)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFF1F5F9),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueText,
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[$pct%]",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF131C2E))
                .border(0.8.dp, Color(0xFF22304A), RoundedCornerShape(4.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(gradientColors))
                )
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF050811))
            .border(1.dp, Color(0xFF1B273D), RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Draw subtle oscilloscope grid
            val gridColor = Color(0xFF141E30)
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
                            Color(0x4400E5FF),
                            Color(0x0500E5FF)
                        )
                    )
                )
                drawPath(
                    path = pathJava,
                    color = RadiantMint,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = pathNative,
                    color = ElectricIndigo,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Top Overlay Labels inside Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "▲ Đỉnh: %.1f MB (Java %.1fM + Native %.1fM)",
                    peakHeapMb,
                    currentJavaMb,
                    currentNativeMb
                ),
                color = SkyBlue,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "● Java",
                    color = RadiantMint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "● Native (Bitmap)",
                    color = ElectricIndigo,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom time markers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "-60s", color = Color(0xFF64748B), fontSize = 8.5.sp)
            Text(text = "-30s", color = Color(0xFF64748B), fontSize = 8.5.sp)
            Text(text = "Live (250ms)", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
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
        color = Color(0xFF101829),
        border = BorderStroke(1.dp, Color(0xFF1E2D46))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

