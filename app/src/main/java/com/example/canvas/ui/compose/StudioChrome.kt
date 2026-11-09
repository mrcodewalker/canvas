package com.example.canvas.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.StudioBrushTool
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
import com.example.canvas.ui.theme.SkyBlue
import kotlin.math.roundToInt

// Pigments rather than a rainbow: every colour is a muted, mixable paint
val PigmentPalette: List<Int> = listOf(
    0xFF1C1B1A.toInt(), // ink
    0xFF4A4743.toInt(), // charcoal
    0xFFA8A39A.toInt(), // ash
    0xFFD9482B.toInt(), // vermilion
    0xFFD9A13B.toInt(), // ochre
    0xFF6E8B3D.toInt(), // moss
    0xFF2F7D6B.toInt(), // viridian
    0xFF25507A.toInt(), // prussian
    0xFF3D4FA3.toInt(), // ultramarine
    0xFF7A3E6B.toInt(), // plum
    0xFF8A5A3C.toInt(), // sienna
    0xFFE9D8B4.toInt()  // parchment
)

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
fun StudioTopBar(
    mode: HistoryMode,
    canUndo: Boolean,
    canRedo: Boolean,
    undoSteps: Int,
    redoSteps: Int,
    zoomScale: Float,
    trackerOpen: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectMode: (HistoryMode) -> Unit,
    onResetZoom: () -> Unit,
    onToggleTracker: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(56.dp)) {
        // phones that are narrow get smaller touch targets so six controls still fit on one line
        val narrow = maxWidth < 400.dp
        val button = if (narrow) 38.dp else 44.dp
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (narrow) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(Icons.AutoMirrored.Rounded.Undo, "Hoàn tác ($undoSteps bước)", enabled = canUndo, size = button, badge = undoSteps, onClick = onUndo)
            BarIcon(Icons.AutoMirrored.Rounded.Redo, "Làm lại ($redoSteps bước)", enabled = canRedo, size = button, badge = redoSteps, onClick = onRedo)
            Spacer(Modifier.weight(1f))
            ModeSwitch(mode = mode, compact = narrow, onSelect = onSelectMode)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = kotlin.math.abs(zoomScale - 1f) > 0.02f && !narrow) {
                Text(
                    text = "${(zoomScale * 100).roundToInt()}%",
                    color = MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            BarIcon(
                Icons.Rounded.Layers, "Theo dõi các tầng", size = button,
                tint = if (trackerOpen) NeonCyan else null,
                background = if (trackerOpen) CyberSurfaceVariant else Color.Transparent,
                onClick = onToggleTracker
            )
            BarIcon(
                Icons.Rounded.DeleteOutline, "Xóa bản vẽ", size = button,
                tint = CoralRose, background = CoralRose.copy(alpha = 0.14f), onClick = onClearCanvas
            )
            BarIcon(Icons.Rounded.MoreHoriz, "Thêm", size = button, onClick = onOpenLab)
        }
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    tint: Color? = null,
    background: Color = Color.Transparent,
    badge: Int? = null,
    onClick: () -> Unit
) {
    val target = tint ?: if (enabled) InkText else FaintText.copy(alpha = 0.55f)
    val color by animateColorAsState(target, label = "barIcon")
    val bg by animateColorAsState(background, label = "barIconBg")
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(if (badge != null) 20.dp else 22.dp))
            if (badge != null) {
                // the step count sits under the icon, plain white, no background
                Text(
                    text = if (badge > 999) "999+" else badge.toString(),
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: HistoryMode, compact: Boolean, onSelect: (HistoryMode) -> Unit) {
    val items = listOf(
        HistoryMode.NAIVE to "Naive",
        HistoryMode.DIRTY_RECT to if (compact) "Dirty" else "Dirty rect",
        HistoryMode.TIERED to "Tiered"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CyberSurface)
            .border(BorderStroke(1.dp, CyberCardBorder), RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((m, label) in items) {
            val selected = m == mode
            val bg by animateColorAsState(if (selected) CyberSurfaceVariant else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (selected) InkText else MutedText, label = "segText")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(m) }
                    .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = if (compact) 12.sp else 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Memory pill: one quiet line that says where the history lives
// ---------------------------------------------------------------------------------------------

@Composable
fun MemoryPill(
    telemetry: XRayCurrentTelemetry,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heap = telemetry.currentJavaHeapMb + telemetry.currentNativeHeapMb
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, CyberCardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TierBar(telemetry.hotMb, telemetry.warmMb, telemetry.coldMb)
            Text(
                text = "${telemetry.totalSteps} bước",
                color = InkText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format(java.util.Locale.US, "%.0f MB", heap),
                color = MutedText,
                fontSize = 12.5.sp
            )
            Text(
                text = if (expanded) "Thu gọn" else "Chi tiết",
                color = FaintText,
                fontSize = 11.5.sp
            )
        }
    }
}

/** Where the steps are held right now: Hot (RAM bitmaps), Warm (RAM compressed), Cold (disk). */
@Composable
private fun TierBar(hot: Float, warm: Float, cold: Float) {
    val total = (hot + warm + cold).coerceAtLeast(0.0001f)
    Canvas(modifier = Modifier.size(width = 44.dp, height = 6.dp)) {
        val r = size.height / 2
        drawRoundRect(color = CyberCardBorder, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (hot + warm + cold <= 0f) return@Canvas
        var x = 0f
        for ((value, color) in listOf(hot to CoralRose, warm to AmberGold, cold to SkyBlue)) {
            val w = size.width * (value / total)
            if (w > 0.5f) {
                drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tool dock
// ---------------------------------------------------------------------------------------------

private enum class DockPanel { SIZE, COLOR }

@Composable
fun ToolDock(
    activeTool: StudioBrushTool,
    color: Int,
    width: Float,
    onSelectTool: (StudioBrushTool) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectWidth: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var panel by remember { mutableStateOf<DockPanel?>(null) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = panel != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CyberSurface,
                border = BorderStroke(1.dp, CyberCardBorder),
                shadowElevation = 10.dp,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                when (panel) {
                    DockPanel.SIZE -> SizePanel(width, Color(color), onSelectWidth)
                    DockPanel.COLOR -> ColorPanel(color) {
                        onSelectColor(it)
                        panel = null
                    }
                    null -> Spacer(Modifier.height(1.dp))
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = CyberSurface,
            border = BorderStroke(1.dp, CyberCardBorder),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DockTool(selected = activeTool == StudioBrushTool.INK_PEN, label = "Bút mực", onClick = {
                    onSelectTool(StudioBrushTool.INK_PEN); panel = null
                }) { tint -> Icon(Icons.Rounded.Edit, null, tint = tint, modifier = Modifier.size(22.dp)) }
                DockTool(selected = activeTool == StudioBrushTool.MARKER, label = "Bút dạ", onClick = {
                    onSelectTool(StudioBrushTool.MARKER); panel = null
                }) { tint -> Icon(Icons.Rounded.Brush, null, tint = tint, modifier = Modifier.size(22.dp)) }
                DockTool(selected = activeTool == StudioBrushTool.ERASER, label = "Tẩy", onClick = {
                    onSelectTool(StudioBrushTool.ERASER); panel = null
                }) { tint -> EraserGlyph(tint) }

                Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(CyberCardBorder))

                DockTool(selected = panel == DockPanel.SIZE, label = "Cỡ nét", showDot = false, onClick = {
                    panel = if (panel == DockPanel.SIZE) null else DockPanel.SIZE
                }) { tint ->
                    val d = (4f + (width - 3f) / (160f - 3f) * 18f).coerceIn(4f, 22f)
                    Box(Modifier.size(d.dp).clip(CircleShape).background(tint))
                }
                DockTool(selected = panel == DockPanel.COLOR, label = "Màu", showDot = false, onClick = {
                    panel = if (panel == DockPanel.COLOR) null else DockPanel.COLOR
                }) { _ ->
                    // a paper-coloured rim so even ink-black is visible on the dark dock
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(InkText),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)))
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTool(
    selected: Boolean,
    label: String,
    showDot: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit
) {
    val bg by animateColorAsState(if (selected) CyberSurfaceVariant else Color.Transparent, label = "toolBg")
    val tint by animateColorAsState(if (selected) InkText else MutedText, label = "toolTint")
    val scale by animateFloatAsState(
        if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toolScale"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        content(tint)
        if (selected && showDot) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
            )
        }
    }
}

/** A tilted eraser block, drawn rather than borrowed from an icon set. */
@Composable
private fun EraserGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        rotate(-38f) {
            val w = size.width * 0.62f
            val h = size.height * 0.40f
            val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            drawRoundRect(tint, topLeft, Size(w, h), corner, style = Stroke(width = 1.8.dp.toPx()))
            drawRoundRect(tint, topLeft, Size(w * 0.42f, h), corner)          // the worn end, filled
        }
        drawLine(
            tint, Offset(size.width * 0.18f, size.height * 0.88f), Offset(size.width * 0.82f, size.height * 0.88f),
            strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SizePanel(width: Float, ink: Color, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp).width(280.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cỡ nét", color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${width.roundToInt()} px", color = MutedText, fontSize = 13.sp)
        }
        // live preview of the stroke on a strip of paper, so ink-black is visible on the dark panel
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkText)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val stroke = (width / 4f).coerceIn(1.5f, 34f).dp.toPx()
                drawLine(
                    color = ink, start = Offset(24.dp.toPx(), size.height / 2), end = Offset(size.width - 24.dp.toPx(), size.height / 2),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
        }
        Slider(
            value = width,
            onValueChange = onChange,
            valueRange = 3f..160f,
            colors = SliderDefaults.colors(
                thumbColor = InkText,
                activeTrackColor = InkText,
                inactiveTrackColor = CyberCardBorder
            )
        )
    }
}

@Composable
private fun ColorPanel(selected: Int, onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in PigmentPalette.chunked(6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) {
                    val isSel = c == selected
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) InkText else Color.White.copy(alpha = 0.12f)),
                                CircleShape
                            )
                            .clickable { onPick(c) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Status strip: one calm line for everything that is running or went wrong
// ---------------------------------------------------------------------------------------------

@Composable
fun StatusStrip(
    title: String,
    detail: String?,
    accent: Color,
    progress: Float? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, CyberCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, color = MutedText, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (secondaryLabel != null && onSecondary != null) StripAction(secondaryLabel, MutedText, onSecondary)
                if (primaryLabel != null && onPrimary != null) StripAction(primaryLabel, accent, onPrimary)
                if (onClose != null) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, "Đóng", tint = MutedText, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = accent,
                    trackColor = CyberCardBorder
                )
            }
        }
    }
}

@Composable
private fun StripAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Lab sheet: everything that is for experiments, out of the way of drawing
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSheet(
    isDemoRunning: Boolean,
    hasReport: Boolean,
    burstLarge: Boolean,
    showSavedRegions: Boolean,
    showGrid: Boolean,
    showMemoryDetail: Boolean,
    onStartComparison: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenConfig: () -> Unit,
    onBurstDraw: (Int) -> Unit,
    onBurstUndo: (Int) -> Unit,
    onToggleBurstSize: () -> Unit,
    onReleaseHot: () -> Unit,
    onMergeDuplicates: () -> Unit,
    onToggleSavedRegions: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleMemoryDetail: () -> Unit,
    onFill: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberSurface,
        contentColor = InkText,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Thử nghiệm",
                color = InkText,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                "Công cụ để đo và so sánh ba cách lưu lịch sử hoàn tác.",
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)
            )

            LabSection("So sánh")
            LabRow("So sánh 3 chế độ", "Cùng một kịch bản trên Naive, Dirty rect và Tiered", enabled = !isDemoRunning) {
                onStartComparison(); onDismiss()
            }
            if (hasReport) LabRow("Báo cáo gần nhất", null) { onOpenReport(); onDismiss() }
            LabRow("Benchmark trên thiết bị", "Chạy các thí nghiệm E1–E6") { onOpenBenchmark(); onDismiss() }
            LabRow("Cấu hình các tầng", "Ngân sách Hot/Warm/Cold, mức nén, kiểu xóa khi đầy") { onOpenConfig(); onDismiss() }

            LabSection("Thử tải")
            LabRow("Vẽ thêm 50 nét", null) { onBurstDraw(50); onDismiss() }
            LabRow("Vẽ thêm 200 nét", null) { onBurstDraw(200); onDismiss() }
            LabRow("Hoàn tác 50 bước liên tiếp", null) { onBurstUndo(50); onDismiss() }
            LabSwitchRow("Nét thử tải cỡ lớn", "Tắt: nét nhỏ, ít vùng bị đổi", burstLarge, onToggleBurstSize)
            LabRow("Thu hồi bộ nhớ đệm Hot", "Mô phỏng hệ điều hành xin lại bộ nhớ") { onReleaseHot(); onDismiss() }
            LabRow("Gộp trang trùng lặp", "Chỉ áp dụng cho Tiered") { onMergeDuplicates(); onDismiss() }

            LabSection("Hiển thị")
            LabSwitchRow("Vùng đã lưu", "Các ô thực sự được chép lại cho mỗi nét", showSavedRegions, onToggleSavedRegions)
            LabSwitchRow("Chi tiết bộ nhớ", "Biểu đồ Hot, Warm, Cold theo thời gian", showMemoryDetail, onToggleMemoryDetail)
            LabSwitchRow("Lưới", null, showGrid, onToggleGrid)

            LabSection("Bản vẽ")
            LabRow("Đổ màu toàn canvas", "Một bước hoàn tác cho cả 16 MB") { onFill(); onDismiss() }
        }
    }
}

@Composable
internal fun LabSection(title: String) {
    Text(
        text = title,
        color = FaintText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
internal fun LabRow(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            title,
            color = when {
                !enabled -> FaintText
                destructive -> CoralRose
                else -> InkText
            },
            fontSize = 15.sp
        )
        if (subtitle != null) Text(subtitle, color = MutedText, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
internal fun LabSwitchRow(title: String, subtitle: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = InkText, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = MutedText, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = InkText,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = MutedText,
                uncheckedTrackColor = CyberSurfaceVariant,
                uncheckedBorderColor = CyberCardBorder
            )
        )
    }
}

/** Mode accent used by telemetry strips. */
fun modeAccent(mode: HistoryMode): Color = when (mode) {
    HistoryMode.NAIVE -> CoralRose
    HistoryMode.DIRTY_RECT -> AmberGold
    HistoryMode.TIERED -> EmeraldGreen
}

