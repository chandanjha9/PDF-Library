package com.example.pdflibrary.ui.viewer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.ui.components.EmptyState
import kotlin.math.roundToInt

private const val ZOOM = 2f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    bookId: Int,
    localPath: String,
    onBack: () -> Unit,
) {
    val viewModel: PdfViewerViewModel = viewModel(
        key = "viewer_${bookId}_$localPath",
        factory = PdfViewerViewModel.factory(bookId, localPath),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reading", style = MaterialTheme.typography.titleMedium)
                        if (state.pageCount > 0) {
                            Text(
                                "Page ${state.currentPage + 1} of ${state.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (state.pageCount > 1) {
                PageControls(
                    current = state.currentPage,
                    count = state.pageCount,
                    onPrev = viewModel::previous,
                    onNext = viewModel::next,
                    onJump = viewModel::goTo,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.errorMessage != null -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Can't open this PDF",
                    message = state.errorMessage.orEmpty(),
                    actionLabel = "Go back",
                    onAction = onBack,
                )
                state.isOpening -> CircularProgressIndicator()
                else -> PageView(
                    state = state,
                    onViewport = viewModel::setViewportWidth,
                    onNext = viewModel::next,
                    onPrev = viewModel::previous,
                )
            }
        }
    }
}

@Composable
private fun PageView(
    state: ViewerUiState,
    onViewport: (Int) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomed by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when the page changes.
    LaunchedEffect(state.renderedPage) {
        zoomed = false
        pan = Offset.Zero
    }

    fun clampPan(p: Offset): Offset {
        val maxX = boxSize.width * (ZOOM - 1) / 2f
        val maxY = boxSize.height * (ZOOM - 1) / 2f
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .onSizeChanged {
                boxSize = it
                onViewport(it.width)
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    zoomed = !zoomed
                    pan = Offset.Zero
                })
            }
            .pointerInput(zoomed) {
                if (zoomed) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pan = clampPan(pan + drag)
                    }
                } else {
                    val threshold = 56.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -threshold) onNext() else if (total > threshold) onPrev()
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            total += dx
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = state.bitmap, label = "page") { bmp ->
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Page ${state.renderedPage + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = if (zoomed) ZOOM else 1f
                            scaleX = s
                            scaleY = s
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
            }
        }
    }
}

@Composable
private fun PageControls(
    current: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: current

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = current > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
            }
            Slider(
                value = dragging ?: current.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onJump(it.roundToInt()) }
                    dragging = null
                },
                valueRange = 0f..(count - 1).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${shown + 1}/$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            IconButton(onClick = onNext, enabled = current < count - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
            }
        }
    }
}
