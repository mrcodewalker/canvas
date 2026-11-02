package com.example.canvas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CyberObsidian = Color(0xFF060A12)
val CyberSurface = Color(0xFF0E1525)
val CyberSurfaceVariant = Color(0xFF172238)
val CyberCardBorder = Color(0xFF263654)

val NeonCyan = Color(0xFF00E5FF)
val SkyBlue = Color(0xFF38BDF8)
val ElectricIndigo = Color(0xFF818CF8)
val EmeraldGreen = Color(0xFF10B981)
val RadiantMint = Color(0xFF00E676)
val AmberGold = Color(0xFFF59E0B)
val CoralRose = Color(0xFFF43F5E)

private val ChronosDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF002633),
    primaryContainer = Color(0xFF0A2E46),
    onPrimaryContainer = Color(0xFFB8EAFF),
    secondary = EmeraldGreen,
    onSecondary = Color(0xFF00291B),
    secondaryContainer = Color(0xFF0B3B2D),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberGold,
    onTertiary = Color(0xFF331E00),
    tertiaryContainer = Color(0xFF422C08),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CoralRose,
    onError = Color.White,
    errorContainer = Color(0xFF4C1122),
    onErrorContainer = Color(0xFFFFCDD2),
    background = CyberObsidian,
    onBackground = Color(0xFFF1F5F9),
    surface = CyberSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = CyberCardBorder
)

private val ChronosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ChronosTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun ChronosCanvasTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChronosDarkColorScheme,
        shapes = ChronosShapes,
        typography = ChronosTypography,
        content = content
    )
}

