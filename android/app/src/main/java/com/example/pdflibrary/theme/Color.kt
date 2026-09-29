package com.example.pdflibrary.theme

import androidx.compose.ui.graphics.Color

/*
 * ── "Midnight Library" design tokens ──────────────────────────────────────────
 *
 * Derived from the app logo (periwinkle sky #8BA6D3 over ink silhouettes).
 * One accent family (periwinkle) drives every interactive element; amber is
 * reserved for time-sensitive info (20-min passes, bookmarks); red is reserved
 * for errors and destructive actions only.
 *
 * All text/background pairs below meet WCAG AA (≥ 4.5:1) — see docs/AUDIT.md.
 * Screens must use these tokens (or MaterialTheme.colorScheme), never raw hex.
 */

// ── Neutrals (ink navy, elevation increases lightness) ───────────────────────
val Ink950   = Color(0xFF0E1320) // app background
val Ink900   = Color(0xFF161C2B) // top bars, bottom nav, dialogs
val Ink850   = Color(0xFF1C2436) // cards
val Ink800   = Color(0xFF252F45) // inputs, chips, pressed surfaces
val Ink700   = Color(0xFF2E3850) // borders / outlines
val Ink750   = Color(0xFF252E44) // hairline dividers

// ── Text ─────────────────────────────────────────────────────────────────────
val TextHigh   = Color(0xFFE7EBF3) // titles, primary text          (15.5:1 on bg)
val TextMedium = Color(0xFFA6B0C3) // secondary text, captions       (8.5:1 on bg)
val TextLow    = Color(0xFF8C98AF) // placeholders, disabled, hints  (≥4.5:1 on cards)

// ── Brand accent: periwinkle (from logo) ─────────────────────────────────────
val Periwinkle         = Color(0xFF9DB6E6) // primary buttons, active states
val OnPeriwinkle       = Color(0xFF0D1A33) // text/icons on primary (8.5:1)
val PeriwinkleDeep     = Color(0xFF2B3B5F) // primary container (selected chips, tonal)
val OnPeriwinkleDeep   = Color(0xFFDCE6FA)
val LogoSky            = Color(0xFF8BA6D3) // exact logo sky — launcher icon / logo ring

// ── Functional ───────────────────────────────────────────────────────────────
val Amber        = Color(0xFFF2B872) // timers, bookmarks
val OnAmber      = Color(0xFF2B1A00)
val AmberDeep    = Color(0xFF4A3718) // amber container
val Success      = Color(0xFF6FD39B)
val SuccessDeep  = Color(0xFF1D3F2F)
val Danger       = Color(0xFFF4868A)
val OnDanger     = Color(0xFF3D0B0E)
val DangerDeep   = Color(0xFF4A1F24)

/** Scrim used behind modal overlays (tour, dialogs). */
val Scrim = Color(0xE60A0E18)

/**
 * Curated, muted book-cover gradients. Each pair keeps white text ≥ 5.7:1,
 * and all hues sit at similar saturation so a shelf of covers looks cohesive.
 */
val CoverGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFF3B5A8C) to Color(0xFF26406B), // slate blue
    Color(0xFF2F6F73) to Color(0xFF1F4F52), // teal
    Color(0xFF6B4E8A) to Color(0xFF4A3566), // plum
    Color(0xFF8A5A3C) to Color(0xFF63402A), // leather
    Color(0xFF4B6B4A) to Color(0xFF334D33), // sage
    Color(0xFF7A4058) to Color(0xFF582C3F), // mulberry
    Color(0xFF4A5670) to Color(0xFF333C52), // graphite
)

fun coverGradientFor(id: Int): Pair<Color, Color> =
    CoverGradients[Math.floorMod(id, CoverGradients.size)]
