package app.smartremote.android.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape

private val SmartRemoteColors = darkColorScheme(
    primary = Color(0xFF7170FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF29284D),
    onPrimaryContainer = Color(0xFFE4E3FF),
    secondary = Color(0xFFD0D6E0),
    onSecondary = Color(0xFF17191D),
    secondaryContainer = Color(0xFF22252B),
    onSecondaryContainer = Color(0xFFE7EAF0),
    background = Color(0xFF08090A),
    onBackground = Color(0xFFF7F8F8),
    surface = Color(0xFF0F1011),
    onSurface = Color(0xFFF7F8F8),
    surfaceVariant = Color(0xFF191A1B),
    onSurfaceVariant = Color(0xFFD0D6E0),
    outline = Color(0xFF303238),
    outlineVariant = Color(0xFF24262A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val SmartRemoteTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val SmartRemoteShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun SmartRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartRemoteColors,
        typography = SmartRemoteTypography,
        shapes = SmartRemoteShapes,
        content = content,
    )
}
