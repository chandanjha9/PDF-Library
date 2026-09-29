package com.example.pdflibrary.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.data.model.PREMIUM_MIN_BYTES
import com.example.pdflibrary.ui.common.Formatters
import com.example.pdflibrary.ui.components.AppCard
import com.example.pdflibrary.ui.components.BookCover
import com.example.pdflibrary.ui.components.EmptyState

private enum class LibraryTab(val label: String) { Saved("Saved"), Downloads("Downloads") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Int) -> Unit,
    onOpenPdf: (bookId: Int, localPath: String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Saved) }
    var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("My Library", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Saved books and PDFs available offline",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LibraryTab.entries.forEachIndexed { i, t ->
                val count = if (t == LibraryTab.Saved) state.favorites.size else state.downloads.size
                SegmentedButton(
                    selected = tab == t,
                    onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(i, LibraryTab.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Text(if (count > 0) "${t.label} ($count)" else t.label)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val rows = if (tab == LibraryTab.Saved) state.favorites else state.downloads
        when {
            !state.isLoaded -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            rows.isEmpty() && tab == LibraryTab.Saved -> EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = "No saved books yet",
                message = "Tap the bookmark on any book page to keep it here.",
                actionLabel = "Browse books",
                onAction = onBrowse,
            )
            rows.isEmpty() -> EmptyState(
                icon = Icons.Outlined.CloudDownload,
                title = "No downloads yet",
                message = "Downloaded PDFs appear here and can be read without internet.",
                actionLabel = "Browse books",
                onAction = onBrowse,
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows, key = { "${tab.name}_${it.entry.bookId}" }) { item ->
                    LibraryRow(
                        item = item,
                        isDownloadsTab = tab == LibraryTab.Downloads,
                        onOpen = {
                            val path = item.entry.localPath
                            if (item.fileAvailable && path != null) onOpenPdf(item.entry.bookId, path)
                            else onOpenBook(item.entry.bookId)
                        },
                        onDetails = { onOpenBook(item.entry.bookId) },
                        onRemove = {
                            if (tab == LibraryTab.Saved) viewModel.removeFavorite(item.entry.bookId)
                            else pendingDelete = item
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = { Text("\"${item.entry.title ?: "This book"}\" will be removed from your device. You can download it again later.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteDownload(item); pendingDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItem,
    isDownloadsTab: Boolean,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onRemove: () -> Unit,
) {
    val e = item.entry
    val title = e.title ?: "Book #${e.bookId}"   // only if metadata was never cached
    val missing = isDownloadsTab && !item.fileAvailable
    val subtitle = when {
        missing -> "File missing — tap to download again"
        isDownloadsTab -> listOfNotNull(
            Formatters.fileSize(e.fileSize),
            e.lastPage?.let { "Page ${it + 1}" },
        ).joinToString(" · ")
        else -> listOfNotNull(e.author, "Saved ${Formatters.dateFromEpochMillis(e.addedAt)}").joinToString(" · ")
    }

    AppCard(onClick = if (missing) onDetails else onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(
                e.bookId, title, Modifier.size(width = 46.dp, height = 64.dp),
                coverUrl = e.coverUrl, cornerRadius = 6.dp, showTitle = false,
                premium = (e.fileSize ?: 0L) > PREMIUM_MIN_BYTES,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (missing) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!missing) {
                FilledTonalButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Text(if (item.fileAvailable) "Read" else "View")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = if (isDownloadsTab) "Delete download" else "Remove from saved",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
