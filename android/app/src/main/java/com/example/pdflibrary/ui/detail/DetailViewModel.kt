package com.example.pdflibrary.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.Result
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.isPremium
import com.example.pdflibrary.premium.UpiPayment
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

sealed interface PremiumState {
    data object NotPremium : PremiumState
    data object Checking : PremiumState
    /** [manual] = after unlocking, the admin sends the book on WhatsApp/Telegram. */
    data class Locked(val manual: Boolean) : PremiumState
    /** Unlocked and downloadable in the app. */
    data object Unlocked : PremiumState
    /** Paid/ad watched; waiting for (or received) manual delivery. */
    data class Ordered(val contact: String?, val sent: Boolean) : PremiumState
    data class Error(val message: String) : PremiumState
}

data class DetailUiState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val errorMessage: String? = null,
    val premium: PremiumState = PremiumState.NotPremium,
    val isUnlocking: Boolean = false,
    /** UPI app returned no clear result → ask the user. */
    val askPaymentConfirmation: Boolean = false,
    /** One-off message (snackbar). */
    val message: String? = null,
    /** WhatsApp number / Telegram username for manual delivery. */
    val contact: String = "",
)

class DetailViewModel(
    private val repo: BookRepository,
    private val bookId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(contact = repo.lastContact))
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
                is Result.Success -> {
                    _uiState.update { it.copy(book = result.data, isLoading = false) }
                    if (result.data.isPremium && _uiState.value.downloadState !is DownloadState.Done) checkPremium()
                }
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

    // ── Premium ──────────────────────────────────────────────────────────────

    private var pendingTxnRef: String? = null

    fun checkPremium() {
        viewModelScope.launch {
            _uiState.update { it.copy(premium = PremiumState.Checking) }
            val next = when (val r = repo.getUnlockStatus(bookId)) {
                is Result.Success -> r.data.toPremiumState()
                is Result.Error -> PremiumState.Error(r.message)
                Result.Loading -> PremiumState.Checking
            }
            _uiState.update { it.copy(premium = next) }
        }
    }

    private fun com.example.pdflibrary.data.model.UnlockStatus.toPremiumState(): PremiumState = when {
        !premium -> PremiumState.NotPremium
        !unlocked -> PremiumState.Locked(manual = isManual)
        isManual -> PremiumState.Ordered(contact, sent = status == "sent")
        else -> PremiumState.Unlocked
    }

    fun onContactChange(value: String) = _uiState.update { it.copy(contact = value.take(60)) }

    /** Manual delivery needs somewhere to send the book before we take payment. */
    fun contactValidOrWarn(): Boolean {
        val p = _uiState.value.premium
        if (p is PremiumState.Locked && p.manual && !isValidContact(_uiState.value.contact)) {
            showMessage("Enter your WhatsApp number or Telegram username first.")
            return false
        }
        return true
    }

    private fun isValidContact(c: String): Boolean {
        val t = c.trim()
        val digits = t.count { it.isDigit() }
        return digits >= 10 || (t.startsWith("@") && t.length >= 4) || (t.contains("@") && t.contains("."))
    }

    fun onUpiStarted(txnRef: String) {
        pendingTxnRef = txnRef
    }

    fun onUpiResult(response: String?) {
        when (UpiPayment.parse(response)) {
            UpiPayment.Outcome.Success -> completeUnlock("upi", response ?: pendingTxnRef)
            UpiPayment.Outcome.Failed -> showMessage("Payment failed or was cancelled.")
            UpiPayment.Outcome.Unknown -> _uiState.update { it.copy(askPaymentConfirmation = true) }
        }
    }

    /** User's answer when the UPI app didn't report a clear result. */
    fun confirmPayment(paid: Boolean) {
        _uiState.update { it.copy(askPaymentConfirmation = false) }
        if (paid) completeUnlock("upi", "unconfirmed:${pendingTxnRef.orEmpty()}")
    }

    fun onAdRewarded() = completeUnlock("ad", "ad:${System.currentTimeMillis()}")

    fun showMessage(text: String) = _uiState.update { it.copy(message = text) }
    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun completeUnlock(method: String, ref: String?) {
        val contact = _uiState.value.contact.trim().ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isUnlocking = true) }
            when (val r = repo.unlock(bookId, method, ref, contact)) {
                is Result.Success -> {
                    val next = r.data.toPremiumState()
                    _uiState.update {
                        it.copy(
                            isUnlocking = false,
                            premium = next,
                            message = if (next is PremiumState.Ordered) "Order received! The book will be sent to ${next.contact ?: "you"}."
                                      else "Unlocked! Starting download…",
                        )
                    }
                    if (next == PremiumState.Unlocked) download()
                }
                is Result.Error -> {
                    // The unlock is saved on the device and re-sent automatically next time.
                    _uiState.update { it.copy(isUnlocking = false, message = "Saved on your phone, but the server didn't respond: ${r.message}") }
                    checkPremium()
                }
                Result.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(bookId: Int) = appViewModelFactory { DetailViewModel(it.repository, bookId) }
    }
}
