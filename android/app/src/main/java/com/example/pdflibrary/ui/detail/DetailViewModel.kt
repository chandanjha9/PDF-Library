package com.example.pdflibrary.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.Result
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.ui.common.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState
    /** [progress] is null when the server did not send a Content-Length. */
    data class InProgress(val progress: Float?) : DownloadState
    data class Done(val localPath: String) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

data class DetailUiState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val errorMessage: String? = null,
)

class DetailViewModel(
    private val repo: BookRepository,
    private val bookId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        loadBook()
        observeFavorite()
        checkExistingDownload()
    }

    fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repo.getBook(bookId)) {
                is Result.Success -> _uiState.update { it.copy(book = result.data, isLoading = false) }
                is Result.Error   -> _uiState.update { it.copy(errorMessage = result.message, isLoading = false) }
                Result.Loading    -> Unit
            }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            repo.isFavorite(bookId).collect { fav -> _uiState.update { it.copy(isFavorite = fav) } }
        }
    }

    private fun checkExistingDownload() {
        viewModelScope.launch {
            val existing = repo.getDownload(bookId) ?: return@launch
            val exists = withContext(Dispatchers.IO) { File(existing.localPath).exists() }
            if (exists) _uiState.update { it.copy(downloadState = DownloadState.Done(existing.localPath)) }
        }
    }

    fun toggleFavorite() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch { repo.setFavorite(book, !_uiState.value.isFavorite) }
    }

    fun download() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _uiState.update { it.copy(downloadState = DownloadState.InProgress(null)) }
            val result = repo.downloadBook(bookId) { p ->
                _uiState.update { s ->
                    if (s.downloadState is DownloadState.InProgress) s.copy(downloadState = DownloadState.InProgress(p)) else s
                }
            }
            _uiState.update {
                it.copy(
                    downloadState = when (result) {
                        is Result.Success -> DownloadState.Done(result.data.absolutePath)
                        is Result.Error   -> DownloadState.Failed(result.message)
                        Result.Loading    -> DownloadState.Idle
                    }
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update { it.copy(downloadState = DownloadState.Idle) }
    }

    companion object {
        fun factory(bookId: Int) = appViewModelFactory { DetailViewModel(it.repository, bookId) }
    }
}
