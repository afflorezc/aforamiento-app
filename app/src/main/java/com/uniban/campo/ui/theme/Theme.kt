package com.uniban.campo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val UnibanColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = SurfaceCard,
    primaryContainer = SuccessBg,
    onPrimaryContainer = GreenPrimary,
    secondary = GreenAccent,
    onSecondary = SurfaceCard,
    secondaryContainer = SuccessBg,
    onSecondaryContainer = GreenAccent,
    tertiary = Warning,
    onTertiary = SurfaceCard,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = AppBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = ErrorRed,
    onError = SurfaceCard,
    errorContainer = ErrorBg,
    onErrorContainer = ErrorRed
)

private val UnibanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun UnibanCampoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UnibanColorScheme,
        typography = AppTypography,
        shapes = UnibanShapes,
        content = content
    )
}
