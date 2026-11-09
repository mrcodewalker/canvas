package com.example.canvas.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale
import kotlin.math.max

/**
 * Everything the demo stage shows. [phase] is 1..3 while a mode is being measured, 0 before the first one and 4
 * when everything is done. The two series hold one number per drawn step for each of the three modes and stay on
 * screen until the demo ends, so the modes can be compared on the same axes instead of each one starting from zero.
 */
data class DemoStageState(
    val visible: Boolean = false,
    val phase: Int = 0,
    val title: String = "",
    val explain: String = "",
    val action: String = "",
    val hotMb: Double = 0.0,
    /** Bitmaps decompressed ahead of time for the next undos: the step up from Warm towards Hot. */
    val readyMb: Double = 0.0,
    /** Size of the Hot and Warm budgets, MB (0 = none, e.g. in the modes that have no tiers). */
    val hotBudgetMb: Double = 0.0,
    val warmBudgetMb: Double = 0.0,
    val warmMb: Double = 0.0,
    val coldMb: Double = 0.0,
    val steps: Int = 0,
    val meterCaption: String = "",
    /** RAM used for the history after each step, MB, per mode (Naive, Dirty rect, Tiered). */
    val ram: List<List<Float>> = listOf(emptyList(), emptyList(), emptyList()),
    /** Time the step took (drawing + saving it for undo), ms, per mode. */
    val ms: List<List<Float>> = listOf(emptyList(), emptyList(), emptyList()),
    val progress: Float = 0f
)

private val modeNames = listOf("Naive", "Dirty rect", "Tiered")
val DemoModeColors = listOf(Color(0xFFB8B1A7), AmberGold, EmeraldGreen)

/** Top strip: where we are in the demo and what is going on, in plain words. Short, so the canvas stays visible. */
@Composable
fun DemoStageHeader(
    state: DemoStageState,
    onStop: () -> Unit,
    onOpenReport: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CyberSurface.copy(alpha = 0.96f))
            .border(1.dp, CyberCardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) {
                val done = state.phase > i + 1
                val active = state.phase == i + 1
                val bg by animateColorAsState(
                    when {
                        active -> DemoModeColors[i].copy(alpha = 0.22f)
                        done -> CyberSurfaceVariant
                        else -> Color.Transparent
                    }, tween(300), label = "step"
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, if (active) DemoModeColors[i] else CyberCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(DemoModeColors[i]))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        (if (done) "✓ " else "") + modeNames[i],
                        color = if (active || done) InkText else FaintText,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.phase >= 4 && onOpenReport != null) {
                Text(
                    "Báo cáo", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenReport).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                if (state.phase >= 4) "Đóng" else "Dừng",
                color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onStop).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Text(state.title, color = InkText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
        if (state.explain.isNotBlank()) {
            Text(state.explain, color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (state.action.isNotBlank()) {
            Text(
                state.action,
                color = if (state.phase in 1..3) DemoModeColors[state.phase - 1] else EmeraldGreen,
                fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp
            )
        }
    }
}

/** Bottom panel: the two charts that tell the story, and the tiers of the mode that is running now. */
@Composable
fun DemoCharts(state: DemoStageState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CyberSurface.copy(alpha = 0.96f))
            .border(1.dp, CyberCardBorder, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineChart("RAM cho lịch sử", "MB", state.ram, state.phase, 2f, Modifier.weight(1f))
            LineChart("Thời gian mỗi nét", "ms", state.ms, state.phase, 20f, Modifier.weight(1f))
        }
        TierMeter(state)
    }
}

/** One line per mode against the number of strokes; the mode that is running is drawn thicker. */
@Composable
private fun LineChart(title: String, unit: String, series: List<List<Float>>, phase: Int, minTop: Float, modifier: Modifier) {
    val longest = max(10, series.maxOfOrNull { it.size } ?: 0)
    // Time per stroke has rare spikes (a disk write, a compression): the scale follows the usual values so the
    // lines stay readable, and a spike is simply drawn up to the top edge
    val sorted = series.flatten().sorted()
    val peak = if (sorted.isEmpty()) 0f else if (unit == "ms") sorted[((sorted.size - 1) * 0.97f).toInt()] * 1.25f else sorted.last() * 1.08f
    val top by animateFloatAsState(max(minTop, peak), tween(400), label = "top")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = InkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(String.format(Locale.US, "%.0f %s", top, unit), color = FaintText, fontSize = 10.5.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CyberSurfaceVariant)
        ) {
            val w = size.width
            val h = size.height
            for (g in 1..3) {
                val y = h * g / 4f
                drawLine(CyberCardBorder, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            series.forEachIndexed { i, s ->
                if (s.isEmpty()) return@forEachIndexed
                val color = DemoModeColors[i]
                val active = phase == i + 1
                fun px(k: Int) = if (longest <= 1) 0f else w * 0.02f + (w * 0.96f) * k / (longest - 1).toFloat()
                fun py(v: Float) = h - (v / top).coerceIn(0f, 1f) * (h - 6f) - 3f
                val line = Path()
                s.forEachIndexed { k, v -> if (k == 0) line.moveTo(px(k), py(v)) else line.lineTo(px(k), py(v)) }
                if (active || phase >= 4) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(px(s.lastIndex), h); lineTo(px(0), h); close()
                    }
                    drawPath(area, color.copy(alpha = 0.12f), style = Fill)
                }
                drawPath(
                    line, color.copy(alpha = if (active || phase >= 4) 1f else 0.75f),
                    style = Stroke(width = if (active) 6f else 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawCircle(color, radius = if (active) 8f else 6f, center = Offset(px(s.lastIndex), py(s.last())))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("nét đầu", color = FaintText, fontSize = 9.5.sp)
            Text("$longest nét", color = FaintText, fontSize = 9.5.sp)
        }
    }
}

/**
 * One row per tier, each against its own budget: Hot and Warm fill up and then stay full while the overflow goes down
 * to Cold, which has no RAM limit. Held at the last value between two modes instead of snapping to zero.
 */
@Composable
private fun TierMeter(state: DemoStageState) {
    val hot by animateFloatAsState(state.hotMb.toFloat(), tween(350), label = "hot")
    val ready by animateFloatAsState(state.readyMb.toFloat(), tween(350), label = "ready")
    val warm by animateFloatAsState(state.warmMb.toFloat(), tween(350), label = "warm")
    val cold by animateFloatAsState(state.coldMb.toFloat(), tween(350), label = "cold")
    val tiered = state.hotBudgetMb > 0.0
    val coldScale = max(1f, cold * 1.1f)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (tiered) {
            TierRow("Hot", hot, ready, state.hotBudgetMb.toFloat(), CoralRose, "bitmap trong RAM")
            TierRow("Warm", warm, 0f, state.warmBudgetMb.toFloat(), AmberGold, "nén trong RAM")
            TierRow("Cold", cold, 0f, coldScale, SkyBlue, "file trên đĩa, không tính vào RAM")
        } else {
            // Naive and Dirty rect have a single place: everything stays in RAM as bitmaps
            val scale = max(16f, hot * 1.1f)
            TierRow("Hot", hot, 0f, scale, CoralRose, "bitmap trong RAM, không có Warm/Cold")
        }
        Text(
            if (state.steps > 0) "${state.steps} bước trong lịch sử" else "chưa có bước nào",
            color = MutedText, fontSize = 11.sp
        )
        if (state.meterCaption.isNotBlank()) {
            Text(state.meterCaption, color = FaintText, fontSize = 10.5.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun TierRow(name: String, used: Float, ready: Float, scale: Float, color: Color, caption: String) {
    val full = scale > 0f && (used + ready) >= scale * 0.97f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(name, color = InkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(caption, color = FaintText, fontSize = 10.5.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                String.format(Locale.US, if (name == "Cold") "%.1f MB" else "%.1f / %.1f MB", used + ready, scale),
                color = if (full) color else MutedText, fontSize = 11.5.sp,
                fontWeight = if (full) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(CyberSurfaceVariant)) {
            if (used > 0.001f) Box(Modifier.weight((used / max(scale, 0.01f)).coerceIn(0.001f, 1f)).fillMaxSize().background(color))
            // decompressed ahead of time for the next undos: Hot in form, drawn lighter
            if (ready > 0.001f) Box(Modifier.weight((ready / max(scale, 0.01f)).coerceIn(0.001f, 1f)).fillMaxSize().background(color.copy(alpha = 0.5f)))
            val rest = (1f - (used + ready) / max(scale, 0.01f)).coerceAtLeast(0.0001f)
            Box(Modifier.weight(rest))
        }
    }
}
