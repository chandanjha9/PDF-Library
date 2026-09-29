package com.example.pdflibrary.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.ui.common.appViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ViewerUiState(
    val isOpening: Boolean = true,
    val errorMessage: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val bitmap: Bitmap? = null,
    val renderedPage: Int = -1,
)

/**
 * Owns the PdfRenderer (previously owned by the Composable).
 *
 * Fixes:
 *  - Crash on fast swiping: PdfRenderer allows one open page at a time and is
 *    not thread-safe; two overlapping LaunchedEffects rendered concurrently and
 *    threw "Current page not closed". All renderer access is now serialised by
 *    [rendererLock], and superseded renders are cancelled.
 *  - Resume race: the file was opened before the saved page finished loading,
 *    so books always reopened on page 1. The page is now loaded first.
 *  - Corrupt/partial files crashed the app; they now show an error state.
 *  - Huge pages (posters/A3 scans) could OOM at a fixed 2x scale; render width
 *    now follows the screen width with a hard cap.
 *  - Renderer survives rotation (lives in the ViewModel).
 */
class PdfViewerViewModel(
    private val repo: BookRepository,
    private val bookId: Int,
    private val localPath: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val rendererLock = Mutex()
    private var renderer: PdfRenderer? = null
    private var renderJob: Job? = null
    private var targetWidthPx = 1080

    init {
        open()
    }

    private fun open() = viewModelScope.launch {
        val savedPage = repo.getProgress(bookId)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(localPath)
                require(file.exists()) { "This file is no longer on your device. Download it again from the book page." }
                rendererLock.withLock {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = try {
                        PdfRenderer(pfd) // takes ownership of pfd on success
                    } catch (e: Exception) {
                        runCatching { pfd.close() }
                        throw e
                    }
                    renderer = r
                    r.pageCount
                }
            }
        }
        result.fold(
            onSuccess = { count ->
                if (count <= 0) {
                    _uiState.update { it.copy(isOpening = false, errorMessage = "This PDF has no pages.") }
                } else {
                    _uiState.update { it.copy(isOpening = false, pageCount = count) }
                    goTo(savedPage.coerceIn(0, count - 1))
                }
            },
            onFailure = { e ->
                Log.w(TAG, "open failed", e)
                val msg = if (e is IllegalArgumentException) e.message
                          else "This PDF could not be opened. It may be damaged or password-protected."
                _uiState.update { it.copy(isOpening = false, errorMessage = msg) }
            },
        )
    }

    /** Called by the UI once the viewport is measured. */
    fun setViewportWidth(px: Int) {
        val clamped = px.coerceIn(480, MAX_RENDER_WIDTH)
        if (clamped == targetWidthPx) return
        targetWidthPx = clamped
        val s = _uiState.value
        if (s.pageCount > 0) goTo(s.currentPage, force = true)
    }

    fun next() = goTo(_uiState.value.currentPage + 1)
    fun previous() = goTo(_uiState.value.currentPage - 1)

    fun goTo(page: Int, force: Boolean = false) {
        val s = _uiState.value
        if (s.pageCount == 0) return
        val target = page.coerceIn(0, s.pageCount - 1)
        if (!force && target == s.renderedPage && s.bitmap != null) {
            if (target != s.currentPage) _uiState.update { it.copy(currentPage = target) }
            return
        }
        _uiState.update { it.copy(currentPage = target) }

        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                rendererLock.withLock { renderPage(target) }
            } ?: return@launch
            _uiState.update { it.copy(bitmap = bmp, renderedPage = target) }
            repo.saveProgress(bookId, target)
        }
    }

    private fun renderPage(index: Int): Bitmap? {
        val r = renderer ?: return null
        return try {
            val page = r.openPage(index)
            try {
                // Fit to screen width, but never exceed the height cap (keeps aspect ratio).
                val scale = minOf(
                    targetWidthPx.toFloat() / page.width,
                    MAX_RENDER_HEIGHT.toFloat() / page.height,
                )
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "render page $index failed", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM rendering page $index", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Close only after any in-flight render releases the lock.
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            rendererLock.withLock {
                runCatching { renderer?.close() }
                renderer = null
            }
        }
    }

    companion object {
        private const val TAG = "PdfViewer"
        private const val MAX_RENDER_WIDTH = 1800
        private const val MAX_RENDER_HEIGHT = 4096

        fun factory(bookId: Int, localPath: String) =
            appViewModelFactory { PdfViewerViewModel(it.repository, bookId, localPath) }
    }
}
