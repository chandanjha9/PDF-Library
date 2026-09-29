package com.example.pdflibrary.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.theme.Success
import com.example.pdflibrary.theme.SuccessDeep
import com.example.pdflibrary.ui.common.Formatters
import com.example.pdflibrary.ui.components.BookCover
import com.example.pdflibrary.ui.components.appTextFieldColors

/**
 * Request-a-book dialog: form → (found | not found with suggestions).
 * Now reachable from every tab (hosted by MainScreen).
 */
@Composable
fun RequestBookDialog(
    state: RequestUiState,
    onDismiss: () -> Unit,
    onSubmit: (title: String, author: String?) -> Unit,
    onBackToForm: () -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = !state.isRequesting, usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                val result = state.result
                when {
                    result != null && result.found && result.book != null ->
                        FoundContent(result.book, result.message, onOpenBook, onDismiss)
                    result != null ->
                        NotFoundContent(result.message, result.relatedBooks.orEmpty(), onOpenBook, onBackToForm)
                    else ->
                        FormContent(state, onSubmit, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(icon: ImageVector, title: String, subtitle: String, success: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (success) SuccessDeep else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (success) Success else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColumnScope.FormContent(
    state: RequestUiState,
    onSubmit: (String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var showTitleError by rememberSaveable { mutableStateOf(false) }

    val submit = {
        if (title.isBlank()) showTitleError = true else onSubmit(title, author.ifBlank { null })
    }

    DialogHeader(Icons.Outlined.LibraryAdd, "Request a book", "Get a 20-minute reading pass")
    Spacer(Modifier.height(16.dp))
    Text(
        "Search the full catalogue for a book, comic, study guide or worksheet. If it's found you get instant access for 20 minutes.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = title,
        onValueChange = { title = it; if (it.isNotBlank()) showTitleError = false },
        label = { Text("Book or comic name") },
        placeholder = { Text("e.g. Spider-Man, Physics Class 11") },
        isError = showTitleError,
        supportingText = if (showTitleError) { { Text("Please enter a name") } } else null,
        singleLine = true,
        enabled = !state.isRequesting,
        shape = MaterialTheme.shapes.medium,
        colors = appTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = author,
        onValueChange = { author = it },
        label = { Text("Author or publisher (optional)") },
        placeholder = { Text("e.g. Marvel, NCERT") },
        singleLine = true,
        enabled = !state.isRequesting,
        shape = MaterialTheme.shapes.medium,
        colors = appTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.errorMessage != null) {
        Spacer(Modifier.height(10.dp))
        Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDismiss, enabled = !state.isRequesting) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = submit, enabled = !state.isRequesting) {
            if (state.isRequesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text("Searching…")
            } else {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Find book")
            }
        }
    }
}

@Composable
private fun ColumnScope.FoundContent(book: Book, message: String?, onOpenBook: (Int) -> Unit, onDismiss: () -> Unit) {
    DialogHeader(Icons.Outlined.CheckCircle, "Access granted", "Valid for 20 minutes", success = true)
    Spacer(Modifier.height(16.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.id, book.title, Modifier.size(width = 48.dp, height = 66.dp), coverUrl = book.coverUrl, cornerRadius = 6.dp, showTitle = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(book.author, Formatters.fileSize(book.fileSize)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (!message.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = { onOpenBook(book.id) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open book")
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Close") }
}

@Composable
private fun ColumnScope.NotFoundContent(message: String?, related: List<Book>, onOpenBook: (Int) -> Unit, onBack: () -> Unit) {
    DialogHeader(Icons.Outlined.SearchOff, "No exact match", "Try another name or pick a suggestion")
    Spacer(Modifier.height(14.dp))
    Text(
        message ?: "We couldn't find that book.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (related.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Available now", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(related.distinctBy { it.id }, key = { it.id }) { book ->
                Column(Modifier.width(84.dp)) {
                    Surface(onClick = { onOpenBook(book.id) }, shape = RoundedCornerShape(8.dp)) {
                        BookCover(book.id, book.title, Modifier.size(width = 84.dp, height = 116.dp), coverUrl = book.coverUrl, cornerRadius = 8.dp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search again")
    }
}
