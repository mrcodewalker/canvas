package com.example.canvas.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.bench.ExperimentSuites
import com.example.canvas.bench.Improvement
import com.example.canvas.bench.ImprovementClaim
import com.example.canvas.bench.Improvements
import com.example.canvas.bench.VariantResult
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
import kotlin.math.abs

private val UndoColor = Color(0xFF5BC0BE)
private val gateSymbols = listOf("⓪", "①", "②", "③", "④")
private val gateShort = listOf("Ô nét", "Nén thích nghi", "Nén sâu chọn lọc", "Nạp trước thích nghi", "Ngân sách")
// two short lines under the arrows of the diagram, where there is little width
private val gateTwoLines = listOf("Ô\nnét", "Nén\nthích nghi", "Nén sâu\nchọn lọc", "Nạp trước\nthích nghi", "Ngân\nsách")

/**
 * The three tiers with the gates between them, then one card per improvement: what is usually done, what the team
 * did instead, and the RAM and CPU change measured on this device. Badges show "chưa đo" until the matching
 * experiment suite was run; nothing here is a stored number.
 */
@Composable
fun ImprovementMap(
    results: Map<String, List<VariantResult>>,
    isRunning: Boolean,
    onVerify: (suiteId: String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TierGatesDiagram()
        Improvements.all.forEach { imp ->
            ImprovementCard(imp, results, isRunning, onVerify)
        }
    }
}

@Composable
private fun TierGatesDiagram() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CyberSurface)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Cải tiến nằm ở các cổng giữa các tầng", color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Các tầng Hot, Warm, Cold chỉ là kho chứa. Phần nhóm làm khác nằm ở lúc dữ liệu đi qua từng cổng.",
            color = MutedText, fontSize = 12.sp, lineHeight = 16.sp
        )

        GateBar(4, SkyBlue.copy(alpha = 0.0f), "toàn hệ thống")

        // Both directions live in the same gap between two tiers. Each arrow carries its own words on top, in its own
        // colour, with the gate number next to them: red = drawing (down), teal = undo (back up).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 330.dp
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                TierNode("Hot", if (compact) "bitmap\nRAM" else "bitmap\ntrong RAM", CoralRose, Modifier.weight(0.9f), compact)
                GatePair("① Nén\nthích nghi", "③ Giải nén\nsẵn", compact, Modifier.weight(1.15f))
                TierNode("Warm", if (compact) "nén\nRAM" else "nén\ntrong RAM", AmberGold, Modifier.weight(0.9f), compact)
                GatePair("② Nén sâu\nchọn lọc", "③ Đọc đĩa\nsớm", compact, Modifier.weight(1.15f))
                TierNode("Cold", if (compact) "file\nđĩa" else "file\ntrên đĩa", SkyBlue, Modifier.weight(0.9f), compact)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Vẽ: nét đi xuống →", color = Color(0xFFE2553A), fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            Text("← Undo: nét đi lên", color = UndoColor, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
        }
        GateBar(3, NeonCyan, "Cold → Warm → Hot")
        GateBar(0, EmeraldGreen, "trước khi vào Hot")
    }
}

@Composable
private fun GateBar(gate: Int, color: Color, where: String) {
    if (color.alpha == 0f) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CyberSurfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(gateSymbols[gate], color = InkText, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text("${gateShort[gate]}: chia Hot/Warm theo thói quen vẽ ($where)", color = MutedText, fontSize = 12.sp)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(gateSymbols[gate], color = InkText, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text("${gateShort[gate]} ($where)", color = InkText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TierNode(name: String, caption: String, color: Color, modifier: Modifier, compact: Boolean = false) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.5.dp, color, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Text(name, color = InkText, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(caption, color = MutedText, fontSize = if (compact) 9.5.sp else 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}

/** The gap between two tiers: red words over the arrow down (drawing), teal words over the arrow back up (undo). */
@Composable
private fun GatePair(down: String, up: String, compact: Boolean, modifier: Modifier) {
    val size = if (compact) 9.sp else 10.sp
    val line = if (compact) 11.sp else 12.sp
    Column(modifier.fillMaxHeight().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        Text(down, color = Color(0xFFE2553A), fontSize = size, lineHeight = line, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) { drawGateArrow(Color(0xFFE2553A), toRight = true) }
        Spacer(Modifier.height(6.dp))
        Text(up, color = UndoColor, fontSize = size, lineHeight = line, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) { drawGateArrow(UndoColor, toRight = false) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGateArrow(c: Color, toRight: Boolean) {
    val y = size.height / 2
    val pad = 4f
    val head = 18f
    val tail = if (toRight) pad else size.width - pad
    val tip = if (toRight) size.width - pad else pad
    val back = if (toRight) tip - head else tip + head
    drawLine(c, Offset(tail, y), Offset(tip, y), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(c, Offset(tip, y), Offset(back, y - 9f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(c, Offset(tip, y), Offset(back, y + 9f), strokeWidth = 4f, cap = StrokeCap.Round)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ImprovementCard(
    imp: Improvement,
    results: Map<String, List<VariantResult>>,
    isRunning: Boolean,
    onVerify: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(gateSymbols[imp.gate], color = InkText, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(imp.title, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Top) {
            Text("Cách thường", color = FaintText, fontSize = 11.sp, modifier = Modifier.width(78.dp))
            Text(imp.plain, color = MutedText, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Top) {
            Text("Của nhóm", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(78.dp))
            Text(imp.ours, color = InkText, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        }
        imp.note?.let { Text(it, color = FaintText, fontSize = 10.5.sp, lineHeight = 14.sp) }
        // One badge sits beside the button; several badges get the full width (they wrap) and the button goes under.
        val verify: @Composable () -> Unit = {
            Text(
                if (isRunning) "Đang chạy…" else "Kiểm chứng",
                color = if (isRunning) FaintText else NeonCyan,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isRunning) { onVerify(imp.suiteId) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (imp.claims.size <= 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { imp.claims.forEach { ClaimBadge(it, results) } }
                verify()
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                imp.claims.forEach { ClaimBadge(it, results) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { verify() }
        }
    }
}

/** "CPU −50%" in green when the improvement is better than the plain way on this device, amber when it is not. */
@Composable
private fun ClaimBadge(claim: ImprovementClaim, results: Map<String, List<VariantResult>>) {
    val delta = claimDelta(claim, results)
    val measured = results[claim.suiteId] != null
    val unproven = claimOverlaps(claim, results)
    val text: String
    val color: Color
    when {
        !measured -> { text = "${claim.kind} · chưa đo"; color = FaintText }
        unproven -> { text = "${claim.kind} · chưa phân biệt"; color = MutedText }
        delta == null -> { text = "${claim.kind} · ≈ ngang"; color = MutedText }
        abs(delta) < 1.0 -> { text = "${claim.kind} · ≈ ngang"; color = MutedText }
        else -> {
            // every claim is "lower is better"
            val better = delta < 0
            text = String.format(Locale.US, "%s %s%.0f%%", claim.kind, if (delta >= 0) "+" else "−", abs(delta))
            color = if (better) EmeraldGreen else AmberGold
        }
    }
    Text(
        text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun claimOverlaps(c: ImprovementClaim, results: Map<String, List<VariantResult>>): Boolean {
    val suite = ExperimentSuites.byId(c.suiteId) ?: return false
    val rs = results[c.suiteId] ?: return false
    val metric = suite.metrics.firstOrNull { it.label == c.metricLabel } ?: return false
    val base = rs.getOrNull(c.baselineIndex) ?: return false
    val hero = rs.getOrNull(suite.heroIndex) ?: return false
    return ExperimentSuites.rangesOverlap(metric, base, hero)
}

private fun claimDelta(c: ImprovementClaim, results: Map<String, List<VariantResult>>): Double? {
    val suite = ExperimentSuites.byId(c.suiteId) ?: return null
    val rs = results[c.suiteId] ?: return null
    val metric = suite.metrics.firstOrNull { it.label == c.metricLabel } ?: return null
    val base = rs.getOrNull(c.baselineIndex) ?: return null
    val hero = rs.getOrNull(suite.heroIndex) ?: return null
    return ExperimentSuites.deltaPercent(metric, base, hero)
}
