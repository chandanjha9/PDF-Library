package com.example.pdflibrary.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.model.LibraryEntry
import com.example.pdflibrary.ui.common.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** A library row plus whether its PDF is still on disk (checked off the main thread). */
data class LibraryItem(val entry: LibraryEntry, val fileAvailable: Boolean)

data class LibraryUiState(
    val favorites: List<LibraryItem> = emptyList(),
    val downloads: List<LibraryItem> = emptyList(),
    val isLoaded: Boolean = false,
)

class LibraryViewModel(private val repo: BookRepository) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        combine(repo.observeFavorites().withFileCheck(), repo.observeDownloads().withFileCheck()) { favs, dls ->
            LibraryUiState(favorites = favs, downloads = dls, isLoaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun deleteDownload(item: LibraryItem) = viewModelScope.launch {
        repo.deleteDownload(item.entry.bookId, item.entry.localPath)
    }

    fun removeFavorite(bookId: Int) = viewModelScope.launch {
        repo.removeFavorite(bookId)
    }

    private fun Flow<List<LibraryEntry>>.withFileCheck(): Flow<List<LibraryItem>> =
        map { list ->
            list.map { e -> LibraryItem(e, e.localPath?.let { File(it).exists() } == true) }
        }.flowOn(Dispatchers.IO)

    companion object {
        val Factory = appViewModelFactory { LibraryViewModel(it.repository) }
    }
}
