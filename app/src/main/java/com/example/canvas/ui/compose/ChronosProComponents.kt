package com.example.canvas.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvas.history.HistoryMode
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.ElectricIndigo
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.RadiantMint

/**
 * Clean Material 3 Studio surface container with a 1dp hairline border.
 */
@Composable
fun AuroraGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    accentColors: List<Color> = listOf(NeonCyan, ElectricIndigo, RadiantMint),
    containerColor: Color = Color(0xF21F1D1B),
    glowAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = Color(0xFF3A3733)

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

/**
 * Clean Material 3 Circular Ring Gauge for Memory Profiler telemetry.
 */
@Composable
fun NeonArcGauge(
    title: String,
    valueText: String,
    subText: String,
    fraction: Float,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 58.dp
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "arcGaugeFraction"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(sizeDp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 5.dp.toPx()
                val arcDiameter = size.minDimension - strokeW * 1.4f
                val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
                val arcSize = Size(arcDiameter, arcDiameter)

                // Track arc (240 degrees)
                drawArc(
                    color = Color(0xFF34312D),
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Active value arc
                if (animatedFraction > 0.002f) {
                    val activeSweep = (240f * animatedFraction).coerceAtLeast(4f)
                    drawArc(
                        color = primaryColor,
                        startAngle = 150f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    color = Color(0xFFF2EEE7),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = subText,
                    color = Color(0xFF9A948A),
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = Color(0xFFCFC9BF),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Clean Material 3 Segmented Mode Selector.
 */
@Composable
fun M3ExpressiveModeSelector(
    selectedMode: HistoryMode,
    onSelectMode: (HistoryMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1B1A18),
        border = BorderStroke(1.dp, Color(0xFF3A3733))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ExpressiveModeTab(
                title = "Naive",
                subtitle = "16 MB / bước",
                isSelected = selectedMode == HistoryMode.NAIVE,
                activeColor = CoralRose,
                onClick = { onSelectMode(HistoryMode.NAIVE) },
                modifier = Modifier.weight(1f)
            )
            ExpressiveModeTab(
                title = "Dirty Rect",
                subtitle = "Cắt khung bao",
                isSelected = selectedMode == HistoryMode.DIRTY_RECT,
                activeColor = AmberGold,
                onClick = { onSelectMode(HistoryMode.DIRTY_RECT) },
                modifier = Modifier.weight(1f)
            )
            ExpressiveModeTab(
                title = "Tiered (3 tầng)",
                subtitle = "Hot · Warm · Cold",
                isSelected = selectedMode == HistoryMode.TIERED,
                activeColor = EmeraldGreen,
                onClick = { onSelectMode(HistoryMode.TIERED) },
                modifier = Modifier.weight(1.1f)
            )
        }
    }
}

@Composable
private fun ExpressiveModeTab(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF222731) else Color.Transparent,
        animationSpec = tween(160),
        label = "modeTabBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.65f) else Color.Transparent,
        animationSpec = tween(160),
        label = "modeTabBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) activeColor else Color(0xFF5C574F))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = title,
                    color = if (isSelected) Color(0xFFF2EEE7) else Color(0xFF9A948A),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = if (isSelected) Color(0xFFCFC9BF) else Color(0xFF7A746B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean Material 3 Action Button for running or stopping the 3-mode comparison.
 */
@Composable
fun PulsingAuroraButton(
    text: String,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val bgColor = if (isRunning) Color(0xFFCF4F3A) else Color(0xFFD9482D)
    val borderColor = if (isRunning) Color(0xFFEE7A60).copy(alpha = 0.4f) else Color(0xFFEE7A60).copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
