package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val OmarDarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = ObsidianBlack,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = SoftMint,
    secondary = CyberCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = WarningAmber,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = ErrorCoral,
    onError = ObsidianBlack,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorCoral
)

private val OmarShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OmarVpnTheme(
    isArabic: Boolean,
    content: @Composable () -> Unit
) {
    val direction = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = OmarDarkColorScheme,
            typography = createOmarTypography(isArabic),
            shapes = OmarShapes,
            content = content
        )
    }
}
