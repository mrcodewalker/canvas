package com.example.canvas.ui.compose

import android.graphics.Color as AndroidColor
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
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PanoramaFishEye
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.ShapeDrawMode
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

// Pigments + vibrant graphic studio swatches
val PigmentPalette: List<Int> = listOf(
    0xFF1C1B1A.toInt(), // ink
    0xFF4A4743.toInt(), // charcoal
    0xFFA8A39A.toInt(), // ash
    0xFFFFFFFF.toInt(), // pure white
    0xFFD9482B.toInt(), // vermilion
    0xFFE91E63.toInt(), // magenta
    0xFFD9A13B.toInt(), // ochre
    0xFFFFC107.toInt(), // vivid gold
    0xFF6E8B3D.toInt(), // moss
    0xFF00C853.toInt(), // emerald
    0xFF2F7D6B.toInt(), // viridian
    0xFF00BCD4.toInt(), // cyan
    0xFF25507A.toInt(), // prussian
    0xFF2979FF.toInt(), // cobalt
    0xFF3D4FA3.toInt(), // ultramarine
    0xFF7C4DFF.toInt(), // violet
    0xFF7A3E6B.toInt(), // plum
    0xFF8A5A3C.toInt()  // sienna
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
    savedCount: Int,
    hasUnsavedChanges: Boolean,
    zoomScale: Float,
    trackerOpen: Boolean,
    onOpenGallery: () -> Unit,
    onSaveArtwork: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectMode: (HistoryMode) -> Unit,
    onResetZoom: () -> Unit,
    onToggleTracker: () -> Unit,
    onClearCanvas: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val narrow = maxWidth < 390.dp
        val button = if (narrow) 38.dp else 42.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Studio actions on left, Tracker + Clear + Settings on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BarIcon(
                        icon = Icons.Rounded.PhotoLibrary,
                        description = "Bộ sưu tập tranh ($savedCount)",
                        size = button,
                        badge = savedCount.takeIf { it > 0 },
                        tint = SkyBlue,
                        onClick = onOpenGallery
                    )
                    BarIcon(
                        icon = Icons.Rounded.Save,
                        description = "Lưu bản tranh vẽ",
                        size = button,
                        tint = if (hasUnsavedChanges) EmeraldGreen else InkText,
                        background = if (hasUnsavedChanges) EmeraldGreen.copy(alpha = 0.16f) else Color.Transparent,
                        showUnsavedDot = hasUnsavedChanges,
                        onClick = onSaveArtwork
                    )
                    BarIcon(
                        Icons.AutoMirrored.Rounded.Undo,
                        "Hoàn tác ($undoSteps bước)",
                        enabled = canUndo,
                        size = button,
                        badge = undoSteps,
                        onClick = onUndo
                    )
                    BarIcon(
                        Icons.AutoMirrored.Rounded.Redo,
                        "Làm lại ($redoSteps bước)",
                        enabled = canRedo,
                        size = button,
                        badge = redoSteps,
                        onClick = onRedo
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    BarIcon(
                        Icons.Rounded.Settings, "Cài đặt & Thử nghiệm", size = button,
                        tint = InkText, background = CyberSurfaceVariant, onClick = onOpenSettings
                    )
                }
            }

            // Row 2: Mode selector + Zoom indicator (when zoomed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeSwitch(mode = mode, compact = narrow, onSelect = onSelectMode)
                Spacer(Modifier.weight(1f))
                if (kotlin.math.abs(zoomScale - 1f) > 0.02f) {
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        color = MutedText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onResetZoom)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    size: Dp = 42.dp,
    tint: Color? = null,
    background: Color = Color.Transparent,
    badge: Int? = null,
    showUnsavedDot: Boolean = false,
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
            Icon(
                icon,
                contentDescription = description,
                tint = color,
                modifier = Modifier.size(if (badge != null) 18.dp else 20.dp)
            )
            if (badge != null) {
                Text(
                    text = if (badge > 999) "999+" else badge.toString(),
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 9.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        if (showUnsavedDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen)
            )
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
            .clip(RoundedCornerShape(11.dp))
            .background(CyberSurface)
            .border(BorderStroke(1.dp, CyberCardBorder), RoundedCornerShape(11.dp))
            .padding(2.5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((m, label) in items) {
            val selected = m == mode
            val bg by animateColorAsState(if (selected) CyberSurfaceVariant else Color.Transparent, label = "seg")
            val fg by animateColorAsState(if (selected) InkText else MutedText, label = "segText")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(m) }
                    .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = if (compact) 11.sp else 12.sp,
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
    artworkTitle: String?,
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
            if (telemetry.hotBudgetMb > 0f) {
                TierMeters(telemetry)
            } else {
                TierBar(telemetry.hotMb, telemetry.warmMb, telemetry.coldMb)
            }
            Column {
                if (!artworkTitle.isNullOrBlank()) {
                    Text(
                        text = artworkTitle,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${telemetry.totalSteps} bước",
                        color = InkText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.0f MB", heap),
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = if (expanded) "Thu gọn" else "Chi tiết",
                color = FaintText,
                fontSize = 11.sp
            )
        }
    }
}

private fun mbText(mb: Float): String =
    if (mb >= 1024f) String.format(java.util.Locale.US, "%.1f GB", mb / 1024f) else String.format(java.util.Locale.US, "%.1f MB", mb)

@Composable
private fun TierMeters(t: XRayCurrentTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TierMeterRow("Hot", t.hotMb, t.hotBudgetMb, CoralRose, watermark = t.demotionWatermark)
        TierMeterRow("Warm", t.warmMb, t.warmBudgetMb, AmberGold, watermark = t.demotionWatermark)
        TierMeterRow("Cold", t.coldMb, t.coldBudgetMb, SkyBlue, showLimit = true, fullMeansLimit = false, watermark = null)
    }
}

@Composable
private fun TierMeterRow(
    name: String,
    used: Float,
    budget: Float,
    color: Color,
    showLimit: Boolean = true,
    fullMeansLimit: Boolean = true,
    watermark: Float? = null
) {
    val frac = if (budget > 0f) (used / budget).coerceIn(0f, 1f) else 0f
    val limitLine = watermark ?: 0.97f
    val nearWatermark = fullMeansLimit && frac >= (limitLine - 0.03f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, color = MutedText, fontSize = 10.5.sp, modifier = Modifier.width(30.dp), maxLines = 1)
        Box(Modifier.width(60.dp).height(5.dp).background(CyberCardBorder)) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.03f)).height(5.dp).background(color))
        }
        Text(
            text = if (!showLimit || budget <= 0f) mbText(used)
            else if (budget >= 1024f || used >= 1024f) mbText(used) + " / " + mbText(budget)
            else String.format(java.util.Locale.US, "%.1f / %.1f MB", used, budget),
            color = if (nearWatermark) color else InkText,
            fontSize = 10.sp,
            fontWeight = if (nearWatermark) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

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
// Graphic Studio Tool Dock
// ---------------------------------------------------------------------------------------------

private enum class DockPanel { SHAPES, SIZE, COLOR }

@Composable
fun ToolDock(
    activeTool: StudioBrushTool,
    shapeMode: ShapeDrawMode,
    color: Int,
    width: Float,
    opacity: Float,
    onSelectTool: (StudioBrushTool) -> Unit,
    onSelectShapeMode: (ShapeDrawMode) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onSelectOpacity: (Float) -> Unit,
    onFillCanvas: () -> Unit,
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
                    DockPanel.SHAPES -> ShapesAndFillPanel(
                        currentShape = shapeMode,
                        onSelectShape = {
                            onSelectShapeMode(it)
                            panel = null
                        },
                        onFill = {
                            onFillCanvas()
                            panel = null
                        }
                    )
                    DockPanel.SIZE -> SizeAndOpacityPanel(
                        width = width,
                        opacity = opacity,
                        ink = Color(color),
                        onWidthChange = onSelectWidth,
                        onOpacityChange = onSelectOpacity
                    )
                    DockPanel.COLOR -> ColorStudioPanel(
                        selected = color,
                        onPick = { picked ->
                            onSelectColor(picked)
                        },
                        onDone = { panel = null }
                    )
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
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DockTool(selected = activeTool == StudioBrushTool.INK_PEN, label = "Bút mực", onClick = {
                    onSelectTool(StudioBrushTool.INK_PEN); panel = null
                }) { tint -> Icon(Icons.Rounded.Edit, null, tint = tint, modifier = Modifier.size(20.dp)) }

                DockTool(selected = activeTool == StudioBrushTool.MARKER, label = "Bút dạ", onClick = {
                    onSelectTool(StudioBrushTool.MARKER); panel = null
                }) { tint -> Icon(Icons.Rounded.Brush, null, tint = tint, modifier = Modifier.size(20.dp)) }

                DockTool(selected = activeTool == StudioBrushTool.SOFT_BRUSH, label = "Cọ mềm", onClick = {
                    onSelectTool(StudioBrushTool.SOFT_BRUSH); panel = null
                }) { tint -> Icon(Icons.Rounded.BlurOn, null, tint = tint, modifier = Modifier.size(21.dp)) }

                DockTool(selected = activeTool == StudioBrushTool.ERASER, label = "Tẩy", onClick = {
                    onSelectTool(StudioBrushTool.ERASER); panel = null
                }) { tint -> EraserGlyph(tint) }

                DockTool(
                    selected = panel == DockPanel.SHAPES || shapeMode != ShapeDrawMode.FREEHAND,
                    label = "Hình học & Đổ nền",
                    showDot = shapeMode != ShapeDrawMode.FREEHAND,
                    onClick = { panel = if (panel == DockPanel.SHAPES) null else DockPanel.SHAPES }
                ) { tint ->
                    val shapeIcon = when (shapeMode) {
                        ShapeDrawMode.FREEHAND -> Icons.Rounded.Category
                        ShapeDrawMode.LINE -> Icons.Rounded.HorizontalRule
                        ShapeDrawMode.RECTANGLE -> Icons.Rounded.CropSquare
                        ShapeDrawMode.OVAL -> Icons.Rounded.PanoramaFishEye
                    }
                    Icon(shapeIcon, null, tint = if (shapeMode != ShapeDrawMode.FREEHAND) NeonCyan else tint, modifier = Modifier.size(20.dp))
                }

                Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(CyberCardBorder))

                DockTool(selected = panel == DockPanel.SIZE, label = "Cỡ nét & Độ đậm", showDot = false, onClick = {
                    panel = if (panel == DockPanel.SIZE) null else DockPanel.SIZE
                }) { tint ->
                    val d = (4f + (width - 2f) / (160f - 2f) * 18f).coerceIn(4f, 22f)
                    Box(Modifier.size(d.dp).clip(CircleShape).background(tint.copy(alpha = opacity.coerceIn(0.25f, 1f))))
                }

                DockTool(selected = panel == DockPanel.COLOR, label = "Bảng màu", showDot = false, onClick = {
                    panel = if (panel == DockPanel.COLOR) null else DockPanel.COLOR
                }) { _ ->
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(InkText),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(19.dp).clip(CircleShape).background(Color(color)))
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
            .size(44.dp)
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
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
            )
        }
    }
}

@Composable
private fun ShapesAndFillPanel(
    currentShape: ShapeDrawMode,
    onSelectShape: (ShapeDrawMode) -> Unit,
    onFill: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .width(292.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Chế độ nét & Hình học đồ họa", color = InkText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val entries = listOf(
                Triple(ShapeDrawMode.FREEHAND, "Tự do", Icons.Rounded.Gesture),
                Triple(ShapeDrawMode.LINE, "Đoạn thẳng", Icons.Rounded.HorizontalRule),
                Triple(ShapeDrawMode.RECTANGLE, "Chữ nhật", Icons.Rounded.CropSquare),
                Triple(ShapeDrawMode.OVAL, "Elip", Icons.Rounded.PanoramaFishEye)
            )
            for ((mode, label, icon) in entries) {
                val sel = currentShape == mode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) NeonCyan.copy(alpha = 0.16f) else CyberSurfaceVariant)
                        .border(1.dp, if (sel) NeonCyan else CyberCardBorder, RoundedCornerShape(12.dp))
                        .clickable { onSelectShape(mode) }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, null, tint = if (sel) NeonCyan else InkText, modifier = Modifier.size(19.dp))
                    Text(label, color = if (sel) NeonCyan else MutedText, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CyberSurfaceVariant,
            border = BorderStroke(1.dp, CyberCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onFill)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.FormatColorFill, null, tint = AmberGold, modifier = Modifier.size(20.dp))
                Column {
                    Text("Đổ màu toàn trang vẽ", color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Tô kín canvas 2048×2048 bằng màu đang chọn", color = MutedText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EraserGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        rotate(-38f) {
            val w = size.width * 0.62f
            val h = size.height * 0.40f
            val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            drawRoundRect(tint, topLeft, Size(w, h), corner, style = Stroke(width = 1.8.dp.toPx()))
            drawRoundRect(tint, topLeft, Size(w * 0.42f, h), corner)
        }
        drawLine(
            tint, Offset(size.width * 0.18f, size.height * 0.88f), Offset(size.width * 0.82f, size.height * 0.88f),
            strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SizeAndOpacityPanel(
    width: Float,
    opacity: Float,
    ink: Color,
    onWidthChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp).width(284.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cỡ cọ", color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${width.roundToInt()} px", color = MutedText, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 2.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InkText)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                val stroke = (width / 4f).coerceIn(1.5f, 34f).dp.toPx()
                drawLine(
                    color = ink.copy(alpha = opacity.coerceIn(0.1f, 1f)),
                    start = Offset(24.dp.toPx(), size.height / 2),
                    end = Offset(size.width - 24.dp.toPx(), size.height / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        Slider(
            value = width,
            onValueChange = onWidthChange,
            valueRange = 2f..160f,
            colors = SliderDefaults.colors(
                thumbColor = InkText,
                activeTrackColor = InkText,
                inactiveTrackColor = CyberCardBorder
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Độ đậm (Opacity)", color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${(opacity * 100).roundToInt()}%", color = MutedText, fontSize = 13.sp)
        }
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.10f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = CyberCardBorder
            )
        )
    }
}

@Composable
private fun ColorStudioPanel(
    selected: Int,
    onPick: (Int) -> Unit,
    onDone: () -> Unit
) {
    val hsv = remember(selected) {
        FloatArray(3).also { AndroidColor.colorToHSV(selected, it) }
    }
    var hue by remember(selected) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(selected) { mutableFloatStateOf(hsv[1]) }
    var bri by remember(selected) { mutableFloatStateOf(hsv[2]) }

    Column(
        modifier = Modifier
            .padding(14.dp)
            .width(292.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(selected))
                    .border(1.dp, InkText.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and selected),
                color = InkText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Xong",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        for (row in PigmentPalette.chunked(6)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (c in row) {
                    val isSel = (c and 0xFFFFFF) == (selected and 0xFFFFFF)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                BorderStroke(if (isSel) 2.2.dp else 1.dp, if (isSel) NeonCyan else Color.White.copy(alpha = 0.16f)),
                                CircleShape
                            )
                            .clickable {
                                onPick(c)
                                onDone()
                            }
                    )
                }
            }
        }

        // Custom HSV fine-tuning sliders for full graphic color mixing
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Pha màu tùy chỉnh (HSV)", color = MutedText, fontSize = 11.5.sp)
            HsvSliderRow("Màu", hue, 0f..360f, NeonCyan) { h ->
                hue = h
                onPick(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
            }
            HsvSliderRow("Tươi", sat, 0f..1f, CoralRose) { s ->
                sat = s
                onPick(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
            }
            HsvSliderRow("Sáng", bri, 0f..1f, AmberGold) { v ->
                bri = v
                onPick(AndroidColor.HSVToColor(floatArrayOf(hue, sat, bri)))
            }
        }
    }
}

@Composable
private fun HsvSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, accent: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
        Text(label, color = FaintText, fontSize = 11.sp, modifier = Modifier.width(34.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = InkText,
                activeTrackColor = accent,
                inactiveTrackColor = CyberCardBorder
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Status strip
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
// Lab & Studio Actions Sheet
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
    onOpenGallery: () -> Unit,
    onSaveArtwork: () -> Unit,
    onExportPng: () -> Unit,
    onNewCanvas: () -> Unit,
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
                "Cài đặt, Benchmark & Thử nghiệm",
                color = InkText,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                "Đo kiểm hiệu năng E1–E6, cấu hình tầng bộ nhớ và quản lý tranh vẽ.",
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)
            )

            LabSection("So sánh & Benchmark")
            LabRow("So sánh 3 chế độ", "Cùng một kịch bản trên Naive, Dirty rect và Tiered", enabled = !isDemoRunning) {
                onStartComparison(); onDismiss()
            }
            if (hasReport) LabRow("Báo cáo gần nhất", null) { onOpenReport(); onDismiss() }
            LabRow("Benchmark trên thiết bị", "Chạy các thí nghiệm E1–E6") { onOpenBenchmark(); onDismiss() }
            LabRow("Cấu hình các tầng (Settings)", "Ngưỡng đẩy 80%, tự nguội theo thời gian, mức nén, ngân sách RAM") { onOpenConfig(); onDismiss() }

            LabSection("Thử tải")
            LabRow("Vẽ thêm 50 nét", null) { onBurstDraw(50); onDismiss() }
            LabRow("Vẽ thêm 200 nét", null) { onBurstDraw(200); onDismiss() }
            LabRow("Hoàn tác 50 bước liên tiếp", null) { onBurstUndo(50); onDismiss() }
            LabSwitchRow("Nét thử tải cỡ lớn", "Tắt: nét nhỏ, ít vùng bị đổi", burstLarge, onToggleBurstSize)
            LabRow("Thu hồi bộ nhớ đệm Hot", "Mô phỏng hệ điều hành xin lại bộ nhớ") { onReleaseHot(); onDismiss() }
            LabRow("Gộp trang trùng lặp", "Chỉ áp dụng cho Tiered") { onMergeDuplicates(); onDismiss() }

            LabSection("Hiển thị")
            LabSwitchRow("Vùng đã lưu (Sub-boxes)", "Hiển thị các ô nhỏ thực sự được chép cho nét to & nét chéo", showSavedRegions, onToggleSavedRegions)
            LabSwitchRow("Chi tiết bộ nhớ", "Biểu đồ Hot, Warm, Cold theo thời gian", showMemoryDetail, onToggleMemoryDetail)
            LabSwitchRow("Lưới căn chỉnh trên giấy", null, showGrid, onToggleGrid)

            LabSection("Tác phẩm & Tệp")
            LabRow("Lưu bản tranh vẽ", "Chụp canvas 2048×2048 xuống đĩa & xóa sạch lịch sử Undo/Redo") {
                onDismiss(); onSaveArtwork()
            }
            LabRow("Bộ sưu tập các bản đã vẽ", "Mở lại, đổi tên, nhân bản hoặc tạo tranh mới") {
                onDismiss(); onOpenGallery()
            }
            LabRow("Xuất ảnh PNG ra Thư viện máy", "Lưu ảnh gốc không nén vào Pictures/ChronosCanvas") {
                onDismiss(); onExportPng()
            }
            LabRow("Tạo trang vẽ trắng mới", "Xóa lịch sử hiện tại và mở tờ giấy mới") {
                onDismiss(); onNewCanvas()
            }
            LabRow("Đổ màu toàn canvas", "Một bước hoàn tác cho cả 16 MB") {
                onDismiss(); onFill()
            }
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

fun modeAccent(mode: HistoryMode): Color = when (mode) {
    HistoryMode.NAIVE -> CoralRose
    HistoryMode.DIRTY_RECT -> AmberGold
    HistoryMode.TIERED -> EmeraldGreen
}
