package com.example.pdflibrary.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AppColorScheme = darkColorScheme(
    primary                 = Periwinkle,
    onPrimary               = OnPeriwinkle,
    primaryContainer        = PeriwinkleDeep,
    onPrimaryContainer      = OnPeriwinkleDeep,
    inversePrimary          = PeriwinkleDeep,

    secondary               = Periwinkle,
    onSecondary             = OnPeriwinkle,
    secondaryContainer      = PeriwinkleDeep,
    onSecondaryContainer    = OnPeriwinkleDeep,

    tertiary                = Amber,
    onTertiary              = OnAmber,
    tertiaryContainer       = AmberDeep,
    onTertiaryContainer     = Amber,

    background              = Ink950,
    onBackground            = TextHigh,
    surface                 = Ink950,
    onSurface               = TextHigh,
    surfaceVariant          = Ink800,
    onSurfaceVariant        = TextMedium,
    surfaceTint             = Periwinkle,

    surfaceDim              = Ink950,
    surfaceBright           = Ink800,
    surfaceContainerLowest  = Ink950,
    surfaceContainerLow     = Ink900,
    surfaceContainer        = Ink900,
    surfaceContainerHigh    = Ink850,
    surfaceContainerHighest = Ink800,

    inverseSurface          = TextHigh,
    inverseOnSurface        = Ink950,

    outline                 = Ink700,
    outlineVariant          = Ink750,

    error                   = Danger,
    onError                 = OnDanger,
    errorContainer          = DangerDeep,
    onErrorContainer        = Danger,

    scrim                   = Scrim,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The app is intentionally dark-only (reading app, logo is designed for dark).
 * Every screen should read colours from [MaterialTheme.colorScheme] so the
 * palette can be changed in one place.
 */
@Composable
fun PDFLibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content,
    )
}
