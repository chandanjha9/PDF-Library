package com.example.pdflibrary.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdflibrary.theme.Scrim

private data class TourStep(
    val target: TourTarget,
    val tag: String,
    val title: String,
    val body: String,
)

private val steps = listOf(
    TourStep(TourTarget.Search, "Search", "Find any book or comic",
        "Type a title, author or subject — results update as you type."),
    TourStep(TourTarget.Categories, "Browse", "Filter by category",
        "Jump between Comics, Stories, Science, Novels and Worksheets with one tap."),
    TourStep(TourTarget.Passes, "Your reading", "Passes & progress",
        "Books you request appear here with a live 20-minute countdown. Downloaded books show the page you stopped on."),
    TourStep(TourTarget.Request, "Request", "Can't find it? Request it",
        "Search the full catalogue. If the book exists you get instant access for 20 minutes."),
    TourStep(TourTarget.BottomNav, "Navigation", "Home, Library, Request, Profile",
        "Always visible at the bottom. Library keeps your saved books and offline downloads."),
)

/**
 * Coach-mark tour. Spotlight positions come from the real on-screen bounds of
 * each target (see [tourTarget]); if a target isn't on screen, the card is shown
 * without a cutout instead of highlighting empty space. The overlay also
 * consumes touches so taps don't leak to the UI underneath.
 */
@Composable
fun SpotlightTour(onDismiss: () -> Unit) {
    val registry = LocalTourTargets.current
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val target: Rect? = registry.bounds[step.target]?.takeIf { r ->
        r.width > 0f && r.height > 0f && r.bottom > 0f && r.top < overlaySize.height && overlaySize != IntSize.Zero
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 2f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseWidth",
    )
    val ringColor = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .pointerInput(Unit) { detectTapGestures { /* swallow */ } },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Scrim)
            if (target != null) {
                val pad = 6.dp.toPx()
                val r = CornerRadius(16.dp.toPx())
                val topLeft = Offset(target.left - pad, target.top - pad)
                val size = Size(target.width + pad * 2, target.height + pad * 2)
                drawRoundRect(Color.Transparent, topLeft, size, r, blendMode = BlendMode.Clear)
                drawRoundRect(ringColor, topLeft, size, r, style = Stroke(width = pulse.dp.toPx()))
            }
        }

        // Place the card on whichever side of the target has more room.
        // Target bounds are window coordinates (the app is edge-to-edge), so
        // they already account for the status / navigation bars.
        val gap = 16.dp
        val targetInLowerHalf = target != null && target.center.y > overlaySize.height / 2f
        val alignment = when {
            target == null -> Alignment.Center
            targetInLowerHalf -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        val topPad = if (target != null && !targetInLowerHalf) with(density) { target.bottom.toDp() } + gap else 0.dp
        val bottomPad = if (target != null && targetInLowerHalf) with(density) { (overlaySize.height - target.top).toDp() } + gap else 0.dp

        Box(
            Modifier
                .fillMaxSize()
                .then(if (target == null) Modifier.systemBarsPadding() else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
            contentAlignment = alignment,
        ) {
            TourCard(
                step = step,
                index = index,
                onSkip = onDismiss,
                onNext = { if (index < steps.lastIndex) index++ else onDismiss() },
            )
        }
    }
}

@Composable
private fun TourCard(step: TourStep, index: Int, onSkip: () -> Unit, onNext: () -> Unit) {
    val last = index == steps.lastIndex
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        step.tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == index) 16.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(step.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1} of ${steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!last) TextButton(onClick = onSkip) { Text("Skip") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onNext) {
                    Text(if (last) "Start reading" else "Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
