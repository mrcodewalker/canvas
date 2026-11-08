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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.TierCell
import com.example.canvas.history.TierEvent
import com.example.canvas.history.TierEventKind
import com.example.canvas.ui.XRayCurrentTelemetry
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.SkyBlue
import java.util.Locale

/**
 * Live view of what happens to the undo history while drawing: a map with one column per stroke coloured by the
 * tier it lives in, and a feed of every transition (compress, write to disk, prefetch, decompress ahead, undo,
 * drop...). Events are real: they come from the code that moves the data.
 */
@Composable
fun TierTrackerPanel(
    mode: HistoryMode,
    telemetry: XRayCurrentTelemetry,
    cells: List<TierCell>,
    events: List<TierEvent>,
    startMs: Long,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberSurface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, CyberCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(EmeraldGreen))
                Spacer(Modifier.width(8.dp))
                Text("Theo dõi các tầng", color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "Xóa nhật ký",
                    color = MutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "Đóng", tint = MutedText, modifier = Modifier.size(18.dp)) }
            }

            if (mode != HistoryMode.TIERED) {
                Text(
                    "Chế độ này chỉ giữ ảnh gốc trong RAM nên không có Warm và Cold. Chuyển sang Tiered để xem các nét di chuyển giữa các tầng.",
                    color = MutedText, fontSize = 13.sp, lineHeight = 19.sp
                )
                return@Column
            }

            // the three tiers, right now
            Row(modifier = Modifier.fillMaxWidth()) {
                Legend("Hot", telemetry.hotMb, cells.count { it.tier == 0 }, CoralRose, Modifier.weight(1f))
                Legend("Warm", telemetry.warmMb, cells.count { it.tier == 1 }, AmberGold, Modifier.weight(1f))
                Legend("Cold", telemetry.coldMb, cells.count { it.tier == 2 }, SkyBlue, Modifier.weight(1f))
            }

            // one column per stroke, oldest on the left
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TierStrip(cells)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("nét cũ nhất", color = FaintText, fontSize = 10.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${cells.size} nét · mới nhất", color = FaintText, fontSize = 10.5.sp)
                }
            }

            // what just happened
            if (events.isEmpty()) {
                Text("Hãy vẽ một nét: bạn sẽ thấy nó đi qua từng tầng ở đây.", color = FaintText, fontSize = 12.5.sp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(88.dp)) {
                    items(events) { e -> EventRow(e, startMs) }
                }
            }
        }
    }
}

@Composable
private fun Legend(name: String, mb: Float, steps: Int, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(name, color = InkText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        }
        Text(String.format(Locale.US, "%d nét · %.2f MB", steps, mb), color = MutedText, fontSize = 11.5.sp)
    }
}

private fun tierColor(tier: Int): Color = when (tier) {
    0 -> CoralRose
    1 -> AmberGold
    else -> SkyBlue
}

@Composable
private fun TierStrip(cells: List<TierCell>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        if (cells.isEmpty()) {
            drawRoundRect(CyberCardBorder, size = size, cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
            return@Canvas
        }
        val gap = 1.dp.toPx()
        val minCol = 3.dp.toPx()
        val maxCol = 14.dp.toPx()
        val fit = (size.width / minCol).toInt().coerceAtLeast(1)
        if (cells.size <= fit) {
            // one column per stroke
            val col = (size.width / cells.size).coerceAtMost(maxCol)
            cells.forEachIndexed { i, c ->
                val x = i * col
                drawRoundRect(tierColor(c.tier), Offset(x, 0f), Size((col - gap).coerceAtLeast(1f), size.height), r)
                if (c.ready) {
                    drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Size((col - gap).coerceAtLeast(1f), 3.dp.toPx()), r)
                }
            }
        } else {
            // too many strokes for one column each: each column shows the mix of tiers it stands for
            val per = (cells.size + fit - 1) / fit
            val col = size.width / ((cells.size + per - 1) / per)
            var x = 0f
            var i = 0
            while (i < cells.size) {
                val chunk = cells.subList(i, minOf(i + per, cells.size))
                var y = 0f
                for (tier in 0..2) {
                    val share = chunk.count { it.tier == tier }.toFloat() / chunk.size
                    if (share > 0f) {
                        drawRect(tierColor(tier), Offset(x, y), Size((col - 0.5f).coerceAtLeast(1f), size.height * share))
                        y += size.height * share
                    }
                }
                x += col
                i += per
            }
        }
    }
}

private fun fmt(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun EventRow(e: TierEvent, startMs: Long) {
    val (chip, color) = when (e.kind) {
        TierEventKind.NEW -> "Mới" to InkText
        TierEventKind.HOT_TO_WARM -> "Hot → Warm" to AmberGold
        TierEventKind.WARM_TO_COLD -> "Warm → Cold" to SkyBlue
        TierEventKind.COLD_TO_WARM -> "Cold → Warm" to AmberGold
        TierEventKind.PREDECODE -> "Warm → ảnh" to CoralRose
        TierEventKind.SQUEEZE -> "Nén sâu" to SkyBlue
        TierEventKind.EVICT -> "Bỏ" to CoralRose
        TierEventKind.HELD_IN_WARM -> "Giữ ở Warm" to AmberGold
        TierEventKind.UNDO -> "Hoàn tác" to NeonCyan
        TierEventKind.REDO -> "Làm lại" to NeonCyan
    }
    val tiles = if (e.tiles > 1) " · ${e.tiles} ô" else ""
    val detail = when (e.kind) {
        TierEventKind.NEW -> "Nét #${e.stroke} ${e.note} · ${fmt(e.bytesAfter)}$tiles"
        TierEventKind.HOT_TO_WARM ->
            "Nét #${e.stroke} nén ${fmt(e.bytesBefore)} → ${fmt(e.bytesAfter)}" +
                (if (e.bytesAfter > 0) String.format(Locale.US, " (%.1f×)", e.bytesBefore.toDouble() / e.bytesAfter) else "") + tiles
        TierEventKind.WARM_TO_COLD -> "Nét #${e.stroke} ghi ${fmt(e.bytesBefore)} → ${fmt(e.bytesAfter)} · ${e.note}$tiles"
        TierEventKind.COLD_TO_WARM -> "Nét #${e.stroke} ${e.note} · ${fmt(e.bytesAfter)}$tiles"
        TierEventKind.PREDECODE -> "Nét #${e.stroke} ${e.note} · ${fmt(e.bytesAfter)}$tiles"
        TierEventKind.SQUEEZE -> "Nét #${e.stroke} ${fmt(e.bytesBefore)} → ${fmt(e.bytesAfter)}, ${e.note}"
        TierEventKind.EVICT -> "Nét #${e.stroke} (${fmt(e.bytesBefore)}), ${e.note}"
        TierEventKind.HELD_IN_WARM -> "Nét #${e.stroke}: ${e.note}"
        TierEventKind.UNDO -> "Nét #${e.stroke} ${e.note}"
        TierEventKind.REDO -> "Nét #${e.stroke} ${e.note}"
    }
    val sec = ((e.timeMs - startMs) / 1000).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format(Locale.US, "%d:%02d", sec / 60, sec % 60),
            color = FaintText, fontSize = 11.sp, modifier = Modifier.width(36.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(chip, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(detail, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
