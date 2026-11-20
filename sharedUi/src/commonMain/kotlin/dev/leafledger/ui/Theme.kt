package dev.leafledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF303030), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAEA), onPrimaryContainer = Color(0xFF202020),
    secondary = Color(0xFF5C5C5C), secondaryContainer = Color(0xFFEAEAEA), onSecondaryContainer = Color(0xFF202020),
    background = Color(0xFFF5F5F5), onBackground = Color(0xFF191919), surface = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE8E8E8), surfaceVariant = Color(0xFFF0F0F0),
    onSurface = Color(0xFF191919), onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFF858585), outlineVariant = Color(0xFFE8E8E8),
    inverseSurface = Color(0xFF303030), inverseOnSurface = Color(0xFFF5F5F5), inversePrimary = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6E6E6), onPrimary = Color(0xFF191919),
    primaryContainer = Color(0xFF363636), onPrimaryContainer = Color(0xFFF3F3F3),
    secondary = Color(0xFFBEBEBE), secondaryContainer = Color(0xFF383838), onSecondaryContainer = Color(0xFFEAEAEA),
    background = Color(0xFF151515), onBackground = Color(0xFFECECEC), surface = Color(0xFF1E1E1E),
    surfaceContainerLowest = Color(0xFF151515), surfaceContainerLow = Color(0xFF202020),
    surfaceContainer = Color(0xFF262626), surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF383838), surfaceVariant = Color(0xFF2C2C2C),
    onSurface = Color(0xFFECECEC), onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF363636),
    inverseSurface = Color(0xFFE0E0E0), inverseOnSurface = Color(0xFF222222), inversePrimary = Color(0xFF222222)
)
@Composable fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18), large = androidx.compose.foundation.shape.RoundedCornerShape(24)),
        content = content)
}
