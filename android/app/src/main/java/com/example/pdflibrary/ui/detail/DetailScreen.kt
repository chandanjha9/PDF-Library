package com.example.pdflibrary.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.theme.Amber
import com.example.pdflibrary.theme.coverGradientFor
import com.example.pdflibrary.ui.common.Formatters
import com.example.pdflibrary.ui.components.BookCover
import com.example.pdflibrary.ui.components.EmptyState
import com.example.pdflibrary.ui.components.MetaPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bookId: Int,
    onBack: () -> Unit,
    onOpenPdf: (localPath: String) -> Unit,
) {
    // Keyed per book: combined with the nav-entry ViewModelStore this guarantees
    // each book gets its own ViewModel (previously the first book's ViewModel was
    // reused for every book opened afterwards).
    val viewModel: DetailViewModel = viewModel(
        key = "detail_$bookId",
        factory = DetailViewModel.factory(bookId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.book != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (state.isFavorite) "Remove from saved" else "Save to library",
                                tint = if (state.isFavorite) Amber else LocalContentColor.current,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        val book = state.book
        when {
            state.isLoading && book == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            book == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't load this book",
                    message = state.errorMessage ?: "Please try again.",
                    actionLabel = "Try again",
                    onAction = viewModel::loadBook,
                )
            }
            else -> DetailContent(
                book = book,
                state = state,
                topPadding = padding.calculateTopPadding(),
                onDownload = viewModel::download,
                onCancel = viewModel::cancelDownload,
                onOpenPdf = onOpenPdf,
            )
        }
    }
}

@Composable
private fun DetailContent(
    book: Book,
    state: DetailUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val (tint, _) = coverGradientFor(book.id)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero: tinted backdrop fading into the page, cover centred.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), MaterialTheme.colorScheme.background)))
                .padding(top = topPadding + 8.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            BookCover(
                bookId = book.id,
                title = book.title,
                coverUrl = book.coverUrl,
                cornerRadius = 12.dp,
                modifier = Modifier.size(width = 150.dp, height = 210.dp),
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!book.author.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "by ${book.author}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MetaPill(Formatters.fileSize(book.fileSize), icon = Icons.Outlined.Description)
                val date = Formatters.dateFromEpochSeconds(book.dateAdded)
                if (date.isNotBlank()) MetaPill(date, icon = Icons.Outlined.CalendarToday)
            }

            Spacer(Modifier.height(24.dp))
            if (book.fileSize > BookRepository.MAX_DOWNLOAD_BYTES && state.downloadState !is DownloadState.Done) {
                // Known in advance: don't offer a button that is guaranteed to fail.
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(BookRepository.TOO_LARGE_MESSAGE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            } else {
                DownloadSection(state.downloadState, onDownload, onCancel, onOpenPdf)
            }

            if (!book.description.isNullOrBlank()) {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text("About this book", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(book.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DownloadSection(
    dl: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
    when (dl) {
        DownloadState.Idle -> Button(onClick = onDownload, modifier = buttonModifier) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download & read")
        }
        is DownloadState.InProgress -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dl.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.height(6.dp))
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val progress = dl.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, trackColor = trackColor, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(trackColor = trackColor, modifier = Modifier.fillMaxWidth())
            }
        }
        is DownloadState.Done -> Button(onClick = { onOpenPdf(dl.localPath) }, modifier = buttonModifier) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Read now")
        }
        is DownloadState.Failed -> Column(Modifier.fillMaxWidth()) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(dl.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDownload, modifier = buttonModifier) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
        }
    }
}
