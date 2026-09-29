package com.example.pdflibrary.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.Result
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.BookRequestResponse
import com.example.pdflibrary.data.model.LibraryEntry
import com.example.pdflibrary.data.model.PassSession
import com.example.pdflibrary.ui.common.appViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val fromCache: Boolean = false,
    val errorMessage: String? = null,
    val category: BookCategory = BookCategory.All,
) {
    val visibleBooks: List<Book> get() = books.filterBy(category)
}

/** State of the "Request a book" sheet, which is shown from any tab. */
data class RequestUiState(
    val visible: Boolean = false,
    val isRequesting: Boolean = false,
    val result: BookRequestResponse? = null,
    val errorMessage: String? = null,
)

class HomeViewModel(private val repo: BookRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Kept separate from [uiState]: the per-second countdown is rendered inside
     * the pass cards only, instead of the old design which rebuilt the whole
     * HomeUiState every second and recomposed the entire Home screen.
     */
    private val _passes = MutableStateFlow<List<PassSession>>(emptyList())
    val passes: StateFlow<List<PassSession>> = _passes.asStateFlow()

    private val _request = MutableStateFlow(RequestUiState())
    val request: StateFlow<RequestUiState> = _request.asStateFlow()

    /** Real reading history (downloaded + opened), replacing the old fake progress numbers. */
    val recentlyRead: StateFlow<List<LibraryEntry>> = repo.observeRecentlyRead(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    init {
        refresh()
        startPassPruner()
    }

    // ── Search ──────────────────────────────────────────────────────────────

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (q.isNotBlank()) delay(350) // debounce typing
            runSearch(q)
        }
    }

    fun onCategorySelected(category: BookCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(_uiState.value.query) }
        refreshPasses()
    }

    private suspend fun runSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repo.searchBooks(query)) {
            is Result.Success -> _uiState.update {
                it.copy(isLoading = false, books = result.data.books, fromCache = result.data.fromCache, errorMessage = null)
            }
            is Result.Error -> _uiState.update {
                it.copy(isLoading = false, books = emptyList(), fromCache = false, errorMessage = result.message)
            }
            Result.Loading -> Unit
        }
    }

    // ── 20-minute passes ────────────────────────────────────────────────────

    fun refreshPasses() {
        viewModelScope.launch {
            val result = repo.getActiveSessions()
            if (result is Result.Success) _passes.value = result.data
        }
    }

    private fun startPassPruner() {
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val now = System.currentTimeMillis()
                val current = _passes.value
                if (current.any { it.expiresAtLocalMs <= now }) {
                    _passes.value = current.filter { it.expiresAtLocalMs > now }
                }
            }
        }
    }

    // ── Request dialog ──────────────────────────────────────────────────────

    fun openRequest() {
        _request.value = RequestUiState(visible = true)
    }

    fun dismissRequest() {
        if (_request.value.isRequesting) return
        _request.value = RequestUiState(visible = false)
    }

    /** Back from the "not found / suggestions" view to the form. */
    fun resetRequestForm() {
        _request.update { it.copy(result = null, errorMessage = null) }
    }

    fun requestBook(bookName: String, authorName: String?) {
        if (bookName.isBlank() || _request.value.isRequesting) return
        viewModelScope.launch {
            _request.update { it.copy(isRequesting = true, errorMessage = null, result = null) }
            when (val res = repo.requestBook(bookName.trim(), authorName?.trim())) {
                is Result.Success -> {
                    _request.update { it.copy(isRequesting = false, result = res.data) }
                    if (res.data.found) refreshPasses()
                }
                is Result.Error -> _request.update { it.copy(isRequesting = false, errorMessage = res.message) }
                Result.Loading -> Unit
            }
        }
    }

    companion object {
        val Factory = appViewModelFactory { HomeViewModel(it.repository) }
    }
}
