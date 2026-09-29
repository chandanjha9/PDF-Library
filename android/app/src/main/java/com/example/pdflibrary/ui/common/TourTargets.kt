package com.example.pdflibrary.ui.common

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class TourTarget { Search, Categories, Passes, Request, BottomNav }

/**
 * Records where tour targets actually are on screen.
 * The old tour used hard-coded screen fractions (e.g. "search bar is at 7% of
 * height"), so the spotlight missed its target on any other screen size,
 * font scale or inset configuration.
 */
@Stable
class TourTargetRegistry {
    val bounds = mutableStateMapOf<TourTarget, Rect>()
}

val LocalTourTargets = staticCompositionLocalOf { TourTargetRegistry() }

fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val registry = LocalTourTargets.current
    // Forget the bounds when the target leaves composition (e.g. scrolled out of a LazyColumn).
    DisposableEffect(registry, target) { onDispose { registry.bounds.remove(target) } }
    onGloballyPositioned { coords ->
        if (coords.isAttached) registry.bounds[target] = coords.boundsInRoot()
    }
}
