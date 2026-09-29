package com.example.pdflibrary.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdflibrary.PdfLibraryApp
import com.example.pdflibrary.R
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.LibraryEntry
import com.example.pdflibrary.data.model.PassSession
import com.example.pdflibrary.theme.Amber
import com.example.pdflibrary.theme.LogoSky
import com.example.pdflibrary.ui.common.Formatters
import com.example.pdflibrary.ui.common.TourTarget
import com.example.pdflibrary.ui.common.tourTarget
import com.example.pdflibrary.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBookClick: (Int) -> Unit,
    onOpenPdf: (bookId: Int, localPath: String) -> Unit,
    onProfileClick: () -> Unit,
    onRequestClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    val recentlyRead by viewModel.recentlyRead.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as PdfLibraryApp
    val profile by app.prefs.profile.collectAsStateWithLifecycle()
    val visibleBooks = state.visibleBooks
    val isSearching = state.query.isNotBlank()

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.books.isNotEmpty(),
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item(key = "header") {
                HomeHeader(name = profile.name, avatar = profile.avatar, onProfileClick = onProfileClick)
            }

            item(key = "search") {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .tourTarget(TourTarget.Search),
                )
            }

            val errorText = state.errorMessage
            val noticeText = errorText ?: if (state.fromCache) "You're offline — showing saved results." else null
            if (noticeText != null) {
                item(key = "notice") {
                    NoticeBanner(
                        message = noticeText,
                        isError = errorText != null,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item(key = "categories") {
                CategoryChips(
                    selected = state.category,
                    onSelect = viewModel::onCategorySelected,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .tourTarget(TourTarget.Categories),
                )
            }

            item(key = "for_you_header") {
                SectionHeader(
                    title = if (isSearching) "Results" else "For you",
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                    trailing = {
                        if (!state.isLoading && visibleBooks.isNotEmpty()) {
                            Text(
                                "${visibleBooks.size} books",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }

            item(key = "carousel") {
                when {
                    state.isLoading && state.books.isEmpty() -> CarouselSkeleton()
                    visibleBooks.isNotEmpty() -> LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(visibleBooks, key = { it.id }) { book ->
                            CarouselBook(book = book, onClick = { onBookClick(book.id) })
                        }
                    }
                    state.errorMessage != null -> Unit // banner above explains it
                    else -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = if (isSearching) "No matches" else "Nothing in ${state.category.label}",
                        message = if (isSearching) "Try a different spelling, or request it from the full catalogue."
                                  else "Pick another category or request a specific book.",
                        actionLabel = "Request a book",
                        onAction = onRequestClick,
                    )
                }
            }

            // ── Active 20-minute passes ─────────────────────────────────────
            if (passes.isNotEmpty()) {
                item(key = "passes_header") {
                    SectionHeader(
                        title = "Active passes",
                        modifier = Modifier
                            .padding(top = 28.dp, bottom = 12.dp)
                            .tourTarget(TourTarget.Passes),
                        trailing = {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "${passes.size} active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        },
                    )
                }
                items(passes, key = { "pass_${it.sessionId}" }) { pass ->
                    PassCard(pass = pass, onOpen = { onBookClick(pass.book.id) }, modifier = Modifier.padding(bottom = 10.dp))
                }
            }

            // ── Continue reading (real history) or recently added ───────────
            if (recentlyRead.isNotEmpty()) {
                item(key = "continue_header") {
                    SectionHeader(
                        "Continue reading",
                        Modifier
                            .padding(top = 28.dp, bottom = 12.dp)
                            .then(if (passes.isEmpty()) Modifier.tourTarget(TourTarget.Passes) else Modifier),
                    )
                }
                items(recentlyRead, key = { "recent_${it.bookId}" }) { entry ->
                    ContinueReadingRow(
                        entry = entry,
                        onClick = {
                            val path = entry.localPath
                            if (path != null) onOpenPdf(entry.bookId, path) else onBookClick(entry.bookId)
                        },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            } else if (!isSearching && visibleBooks.isNotEmpty()) {
                item(key = "recent_header") {
                    SectionHeader(
                        "Recently added",
                        Modifier
                            .padding(top = 28.dp, bottom = 12.dp)
                            .then(if (passes.isEmpty()) Modifier.tourTarget(TourTarget.Passes) else Modifier),
                    )
                }
                items(visibleBooks.take(4), key = { "added_${it.id}" }) { book ->
                    BookRow(book = book, onClick = { onBookClick(book.id) }, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(name: String, avatar: String, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, LogoSky, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Welcome back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = onProfileClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(avatar, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search books, comics, notes…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = appTextFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChips(selected: BookCategory, onSelect: (BookCategory) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        items(BookCategory.entries, key = { it.name }) { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                label = { Text(cat.label) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = cat == selected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun CarouselBook(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        BookCover(
            bookId = book.id,
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.author ?: Formatters.fileSize(book.fileSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CarouselSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(3) {
            Column(Modifier.width(118.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
    }
}

/** Ticks once per second while composed; only the card that reads it recomposes. */
@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun PassCard(pass: PassSession, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNowMillis()
    val remaining = pass.remainingSeconds(now)
    val fraction = (remaining / 1200f).coerceIn(0f, 1f)

    AppCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(pass.book.id, pass.book.title, Modifier.size(width = 48.dp, height = 66.dp), coverUrl = pass.book.coverUrl, cornerRadius = 6.dp, showTitle = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pass.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (remaining > 0) "${Formatters.countdown(remaining)} left" else "Expired",
                        style = MaterialTheme.typography.labelMedium,
                        color = Amber,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    color = Amber,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Open") }
        }
    }
}

@Composable
private fun ContinueReadingRow(entry: LibraryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = entry.title ?: "Untitled book"
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(entry.bookId, title, Modifier.size(width = 48.dp, height = 66.dp), cornerRadius = 6.dp, showTitle = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.author ?: "Downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                MetaPill("Page ${(entry.lastPage ?: 0) + 1}", icon = Icons.AutoMirrored.Outlined.MenuBook)
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Resume") }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.id, book.title, Modifier.size(width = 48.dp, height = 66.dp), coverUrl = book.coverUrl, cornerRadius = 6.dp, showTitle = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(book.author, Formatters.fileSize(book.fileSize), Formatters.dateFromEpochSeconds(book.dateAdded).ifBlank { null })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
