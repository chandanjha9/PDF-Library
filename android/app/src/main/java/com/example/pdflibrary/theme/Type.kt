package com.example.pdflibrary.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * IMPORTANT: styles here must NOT carry a colour.
 * A colour baked into a TextStyle overrides LocalContentColor, which is how
 * Buttons, Chips, TopAppBars and NavigationBars tell their text what colour
 * to be. Baked colours were the cause of unreadable text on buttons/chips.
 *
 * Serif is used for display/headline (editorial "library" feel);
 * sans-serif for everything functional.
 */
private val Serif = FontFamily.Serif
private val Sans  = FontFamily.Default

val AppTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall   = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge  = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 26.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall  = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,     fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium    = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium    = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)
