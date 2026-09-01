package dev.csdesktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

val CsPurple = Color(0xFFBB86FC)
val CsPurpleBright = Color(0xFFD0BCFF)
val CsPurpleDim = Color(0xFF7C4DFF)
val CsBackground = Color(0xFF09090B)
val CsSurface = Color(0xFF121214)
val CsSurfaceHigh = Color(0xFF1C1C1F)
val CsOnBg = Color(0xFFF2F2F3)
val CsMuted = Color(0xFFA1A1AA)
val CsLive = Color(0xFFE11D48)
val CsOk = Color(0xFF4ADE80)

private val DarkColors = darkColorScheme(
    primary = CsPurple,
    onPrimary = Color(0xFF1A0B2E),
    primaryContainer = Color(0xFF3B2066),
    onPrimaryContainer = CsPurpleBright,
    secondary = CsPurpleDim,
    onSecondary = Color.White,
    background = CsBackground,
    onBackground = CsOnBg,
    surface = CsSurface,
    onSurface = CsOnBg,
    surfaceVariant = CsSurfaceHigh,
    onSurfaceVariant = CsMuted,
    outline = Color(0xFF3F3F46),
    error = CsLive,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B21A8),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F5),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
)

val CsTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun CsTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = CsTypography,
        content = content,
    )
}

val ColorScheme.live: Color get() = CsLive
