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

// ---------------------------------------------------------------------------------------------
// Palette: warm graphite, like the paper-and-pencil tools this app imitates, and ONE accent
// (vermilion). Memory tiers keep their own muted pigments so the telemetry stays readable.
// ---------------------------------------------------------------------------------------------

// Surfaces, darkest to lightest (names kept from earlier versions so every screen follows the new look)
val CyberObsidian = Color(0xFF171614)        // window background
val CyberSurface = Color(0xFF1F1D1B)         // bars, sheets
val CyberSurfaceVariant = Color(0xFF2A2825)  // controls, selected segments
val CyberCardBorder = Color(0xFF3A3733)      // hairlines

val NeonCyan = Color(0xFFE2553A)       // the accent: vermilion
val SkyBlue = Color(0xFF7FA6C9)        // Cold tier: slate blue
val ElectricIndigo = Color(0xFF9A93B0) // secondary: dusty violet-grey
val EmeraldGreen = Color(0xFF7FA35E)   // success / Tiered: moss
val RadiantMint = Color(0xFF4FA38C)    // viridian
val AmberGold = Color(0xFFD9A13B)      // Warm tier: ochre
val CoralRose = Color(0xFFCF4F3A)      // Hot tier / alert: brick

val InkText = Color(0xFFF2EEE7)        // primary text
val MutedText = Color(0xFF9A948A)      // secondary text
val FaintText = Color(0xFF7A746B)      // tertiary text

private val ChronosDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF2A130C),
    primaryContainer = Color(0xFF3A2620),
    onPrimaryContainer = Color(0xFFF5C9BC),
    secondary = EmeraldGreen,
    onSecondary = Color(0xFF17241A),
    secondaryContainer = Color(0xFF26362E),
    onSecondaryContainer = Color(0xFFC9E0B8),
    tertiary = AmberGold,
    onTertiary = Color(0xFF2B1F08),
    tertiaryContainer = Color(0xFF3A2F18),
    onTertiaryContainer = Color(0xFFF0D9A0),
    error = CoralRose,
    onError = Color.White,
    errorContainer = Color(0xFF3F2019),
    onErrorContainer = Color(0xFFF5C9BC),
    background = CyberObsidian,
    onBackground = InkText,
    surface = CyberSurface,
    onSurface = InkText,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = Color(0xFFCFC9BF),
    outline = CyberCardBorder
)

private val ChronosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// One typeface (the system sans) used with discipline: size and weight carry the hierarchy.
// Tabular figures for numbers so live values do not jitter.
private val ChronosTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum"
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
