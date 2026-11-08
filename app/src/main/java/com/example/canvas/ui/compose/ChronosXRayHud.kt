package com.example.canvas.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.XRayCurrentTelemetry
import com.example.canvas.ui.XRayPoint
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale

/**
 * Where the undo history lives right now, in plain terms: three tiers, the heap over the last minute and a few
 * numbers that matter. No gauges, no boxes inside boxes.
 */
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
    val modeName = when (mode) {
        HistoryMode.NAIVE -> "Naive"
        HistoryMode.DIRTY_RECT -> "Dirty rect"
        HistoryMode.TIERED -> "Tiered"
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CyberSurface,
        border = BorderStroke(1.dp, CyberCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bộ nhớ lịch sử", color = InkText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(modeName, color = MutedText, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onToggleMinimize),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Thu gọn", tint = MutedText, modifier = Modifier.size(22.dp))
                }
            }

            // the three tiers
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SplitBar(telemetry.hotMb, telemetry.warmMb, telemetry.coldMb)
                Row(modifier = Modifier.fillMaxWidth()) {
                    TierStat("Hot", "RAM", telemetry.hotMb, telemetry.hotSteps, CoralRose, Modifier.weight(1f))
                    TierStat("Warm", "RAM nén", telemetry.warmMb, telemetry.warmSteps, AmberGold, Modifier.weight(1f))
                    TierStat("Cold", "đĩa", telemetry.coldMb, telemetry.coldSteps, SkyBlue, Modifier.weight(1f))
                }
            }

            // heap, last minute
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Heap, 60 giây qua", color = MutedText, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        String.format(
                            Locale.US, "hiện %.0f MB · đỉnh %.0f MB",
                            telemetry.currentJavaHeapMb + telemetry.currentNativeHeapMb, telemetry.peakHeapMb
                        ),
                        color = MutedText, fontSize = 12.sp
                    )
                }
                Sparkline(points)
            }

            // numbers that matter: two columns, label above value
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure("Tiết kiệm so với Naive", String.format(Locale.US, "%.0f%%", telemetry.ramSavedVsNaivePercent), Modifier.weight(1f))
                    Figure(
                        "Tỷ lệ nén",
                        if (telemetry.compressionRatio > 1.05f) String.format(Locale.US, "%.1f×", telemetry.compressionRatio) else "—",
                        Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Figure("Nạp trước trúng", String.format(Locale.US, "%.0f%%", telemetry.prefetchHitRate), Modifier.weight(1f))
                    Figure(
                        "Trang trùng đã gộp",
                        if (telemetry.deduplicatedPatches > 0)
                            "${telemetry.deduplicatedPatches} · ${telemetry.oomCleanerReclaimedKb.toInt()} KB" else "—",
                        Modifier.weight(1f)
                    )
                }
            }

            Text(
                "Cấu hình các tầng",
                color = NeonCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSwamSheet)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun SplitBar(hot: Float, warm: Float, cold: Float) {
    val total = hot + warm + cold
    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(CyberCardBorder, size = size, cornerRadius = r)
        if (total <= 0f) return@Canvas
        var x = 0f
        val gap = 2.dp.toPx()
        val parts = listOf(hot to CoralRose, warm to AmberGold, cold to SkyBlue).filter { it.first > 0f }
        for ((i, part) in parts.withIndex()) {
            val w = (size.width * (part.first / total) - if (i < parts.size - 1) gap else 0f).coerceAtLeast(2f)
            drawRoundRect(part.second, Offset(x, 0f), Size(w, size.height), r)
            x += w + gap
        }
    }
}

@Composable
private fun TierStat(name: String, caption: String, mb: Float, steps: Int, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(name, color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(String.format(Locale.US, "%.2f MB", mb), color = InkText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("$steps bước · $caption", color = MutedText, fontSize = 11.5.sp)
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MutedText, fontSize = 12.sp)
        Text(value, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Sparkline(points: List<XRayPoint>) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        if (points.size < 2) {
            Text("Đang thu thập…", color = FaintText, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart))
            return@Box
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val t0 = points.first().timestampMs
            val span = (points.last().timestampMs - t0).coerceAtLeast(1L).toFloat()
            val lo = points.minOf { it.totalHeapMb }
            val hi = points.maxOf { it.totalHeapMb }
            val pad = maxOf(1f, (hi - lo) * 0.25f)
            val bottom = lo - pad
            val range = (hi + pad) - bottom
            fun x(p: XRayPoint) = size.width * ((p.timestampMs - t0) / span)
            fun y(p: XRayPoint) = size.height - size.height * ((p.totalHeapMb - bottom) / range)

            drawLine(CyberCardBorder, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
            val line = Path().apply {
                moveTo(x(points[0]), y(points[0]))
                for (i in 1 until points.size) lineTo(x(points[i]), y(points[i]))
            }
            drawPath(line, InkText.copy(alpha = 0.85f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = points.last()
            drawCircle(NeonCyan, radius = 3.dp.toPx(), center = Offset(x(last), y(last)))
        }
    }
}
