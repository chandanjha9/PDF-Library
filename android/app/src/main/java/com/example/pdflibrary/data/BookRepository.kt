package com.example.pdflibrary.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.pdflibrary.data.local.AppDatabase
import com.example.pdflibrary.data.model.ApiError
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.BookRequestPayload
import com.example.pdflibrary.data.model.BookRequestResponse
import com.example.pdflibrary.data.model.Download
import com.example.pdflibrary.data.model.Favorite
import com.example.pdflibrary.data.model.LibraryEntry
import com.example.pdflibrary.data.model.PassSession
import com.example.pdflibrary.data.model.ReadingProgress
import com.example.pdflibrary.data.network.ApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

/** Books plus whether they came from the offline cache. */
data class BookPage(val books: List<Book>, val fromCache: Boolean)

class BookRepository(context: Context, private val prefs: AppPrefs) {

    private val appContext = context.applicationContext
    private val api = ApiClient.bookApiService
    private val db  = AppDatabase.getInstance(appContext)
    private val gson = Gson()

    private val booksDao    = db.booksDao()
    private val favsDao     = db.favoritesDao()
    private val dlDao       = db.downloadsDao()
    private val progressDao = db.readingProgressDao()

    private val pdfDir: File get() = File(appContext.filesDir, "pdfs").also { it.mkdirs() }

    // ── Search / Browse ─────────────────────────────────────────────────────

    suspend fun searchBooks(query: String): Result<BookPage> = withContext(Dispatchers.IO) {
        try {
            val response = if (query.isBlank()) api.listBooks() else api.searchBooks(query.trim())
            if (response.isSuccessful) {
                val books = response.body()?.books.orEmpty().distinctBy { it.id }
                if (books.isNotEmpty()) booksDao.insertAll(books)
                Result.Success(BookPage(books, fromCache = false))
            } else {
                cachedFallback(query, "Server error ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cachedFallback(query, friendlyMessage(e))
        }
    }

    private suspend fun cachedFallback(query: String, reason: String): Result<BookPage> {
        val q = query.trim().lowercase()
        val cached = booksDao.getAllBooks().filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.author?.lowercase()?.contains(q) == true
        }
        return if (cached.isNotEmpty()) Result.Success(BookPage(cached, fromCache = true))
        else Result.Error(reason)
    }

    // ── Single Book ──────────────────────────────────────────────────────────

    suspend fun getBook(id: Int): Result<Book> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBook(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                booksDao.insertAll(listOf(body)) // keeps Library titles resolvable offline
                Result.Success(body)
            } else {
                booksDao.getBook(id)?.let { Result.Success(it) } ?: Result.Error("Book not found")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            booksDao.getBook(id)?.let { Result.Success(it) } ?: Result.Error(friendlyMessage(e))
        }
    }

    // ── Download ─────────────────────────────────────────────────────────────

    /**
     * Downloads the PDF to app storage.
     * - Streams through OkHttp (timeouts + host failover) instead of java.net.URL.
     * - Writes to a .part file and renames only after the bytes are verified as a
     *   PDF, so a dropped connection or an HTML error page never becomes a
     *   "downloaded" book that later crashes the viewer.
     */
    suspend fun downloadBook(
        bookId: Int,
        onProgress: (Float?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val part = File(pdfDir, "book_$bookId.pdf.part")
        val knownSize = booksDao.getBook(bookId)?.fileSize ?: 0L
        if (knownSize > MAX_DOWNLOAD_BYTES) return@withContext Result.Error(TOO_LARGE_MESSAGE)
        try {
            val urlResponse = api.getDownloadUrl(bookId, prefs.deviceId)
            val fileUrl = urlResponse.body()?.fileUrl
            if (!urlResponse.isSuccessful || fileUrl.isNullOrBlank()) {
                return@withContext Result.Error(errorMessage(urlResponse, "Could not get a download link"))
            }

            val request = Request.Builder().url(ApiClient.resolve(fileUrl)).build()
            ApiClient.httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.Error(
                        when (resp.code) {
                            410 -> "Your 20-minute pass expired. Request the book again."
                            413 -> TOO_LARGE_MESSAGE
                            else -> "Download failed (HTTP ${resp.code})"
                        }
                    )
                }
                val body = resp.body ?: return@withContext Result.Error("Empty response from server")
                val total = body.contentLength().takeIf { it > 0 }
                var lastEmit = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmit > 120) {
                                lastEmit = now
                                onProgress(total?.let { (read.toFloat() / it).coerceIn(0f, 1f) })
                            }
                        }
                    }
                }
            }

            if (!looksLikePdf(part)) {
                part.delete()
                return@withContext Result.Error("The server did not return a valid PDF file")
            }
            val out = File(pdfDir, "book_$bookId.pdf")
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) {
                part.delete()
                return@withContext Result.Error("Could not save the file")
            }
            val size = out.length()
            dlDao.saveDownload(Download(bookId = bookId, localPath = out.absolutePath, fileSize = size))
            onProgress(1f)
            Result.Success(out)
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            Log.w(TAG, "download failed", e)
            Result.Error(friendlyMessage(e))
        }
    }

    private fun looksLikePdf(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val head = ByteArray(1024)
            val n = s.read(head)
            n > 4 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF")
        }
    }.getOrDefault(false)

    // ── Favorites ────────────────────────────────────────────────────────────

    fun observeFavorites(): Flow<List<LibraryEntry>> = favsDao.observeFavorites()
    fun isFavorite(bookId: Int): Flow<Boolean> = favsDao.isFavorite(bookId)

    suspend fun setFavorite(book: Book, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (favorite) {
            booksDao.insertAll(listOf(book))
            favsDao.addFavorite(Favorite(bookId = book.id))
        } else {
            favsDao.removeFavorite(book.id)
        }
    }

    suspend fun removeFavorite(bookId: Int) = withContext(Dispatchers.IO) {
        favsDao.removeFavorite(bookId)
    }

    // ── Downloads ────────────────────────────────────────────────────────────

    fun observeDownloads(): Flow<List<LibraryEntry>> = dlDao.observeDownloads()
    fun observeRecentlyRead(limit: Int = 5): Flow<List<LibraryEntry>> = dlDao.observeRecentlyRead(limit)

    suspend fun getDownload(bookId: Int): Download? = withContext(Dispatchers.IO) {
        dlDao.getDownload(bookId)
    }

    /** Removes the DB row, the file on disk and the saved page. */
    suspend fun deleteDownload(bookId: Int, localPath: String?) = withContext(Dispatchers.IO) {
        localPath?.let { File(it).delete() }
        dlDao.removeDownload(bookId)
        progressDao.clearProgress(bookId)
    }

    // ── Reading Progress ─────────────────────────────────────────────────────

    suspend fun getProgress(bookId: Int): Int = withContext(Dispatchers.IO) {
        progressDao.getProgress(bookId)?.lastPage ?: 0
    }

    suspend fun saveProgress(bookId: Int, page: Int) = withContext(Dispatchers.IO) {
        progressDao.saveProgress(ReadingProgress(bookId = bookId, lastPage = page))
    }

    // ── On-Demand Request & 20-Min Session Access ───────────────────────────

    /**
     * Returns Success for both "found" and "not found" (the latter carries the
     * server's message and suggested books). Error is reserved for real failures.
     * Previously a 404 surfaced the raw JSON body as the error text.
     */
    suspend fun requestBook(bookName: String, authorName: String?): Result<BookRequestResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.requestBook(
                    BookRequestPayload(bookName, authorName?.takeIf { it.isNotBlank() }, prefs.deviceId)
                )
                val body = response.body()
                when {
                    response.isSuccessful && body != null -> {
                        body.book?.let { runCatching { booksDao.insertAll(listOf(it)) } }
                        body.relatedBooks?.takeIf { it.isNotEmpty() }?.let { runCatching { booksDao.insertAll(it) } }
                        Result.Success(body)
                    }
                    response.code() == 404 -> {
                        val err = parseError(response)
                        Result.Success(
                            BookRequestResponse(
                                found = false,
                                message = err?.message ?: "No match found for \"$bookName\".",
                                relatedBooks = err?.relatedBooks,
                            )
                        )
                    }
                    else -> Result.Error(errorMessage(response, "Request failed"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(friendlyMessage(e))
            }
        }

    suspend fun getActiveSessions(): Result<List<PassSession>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getActiveSessions(prefs.deviceId)
            val now = System.currentTimeMillis()
            if (response.isSuccessful) {
                val list = response.body()?.activeSessions.orEmpty()
                    .filter { it.ttlSeconds > 0 }
                    .map { PassSession(it.sessionId, it.book, now + it.ttlSeconds * 1000) }
                Result.Success(list)
            } else {
                Result.Error(errorMessage(response, "Could not load passes"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(friendlyMessage(e))
        }
    }

    suspend fun isServerOnline(): Boolean = withContext(Dispatchers.IO) {
        try { api.health().isSuccessful } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseError(response: Response<*>): ApiError? = runCatching {
        response.errorBody()?.charStream()?.use { gson.fromJson(it, ApiError::class.java) }
    }.getOrNull()

    private fun errorMessage(response: Response<*>, fallback: String): String {
        val err = parseError(response)
        return err?.message ?: err?.error ?: "$fallback (HTTP ${response.code()})"
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is com.example.pdflibrary.data.network.ServerUnreachableException ->
            "Can't reach the library server. Make sure the backend is running, then check " +
                "Profile → Server address. Tried: ${e.triedHosts.joinToString()}"
        is UnknownHostException, is java.net.ConnectException -> "Can't reach the library server. Check your internet connection."
        is SocketTimeoutException -> "The server took too long to respond. Please try again."
        is IOException -> e.message ?: "Network error"
        else -> e.message ?: "Something went wrong"
    }

    companion object {
        private const val TAG = "BookRepository"

        /** Telegram's standard Bot API only serves files up to 20 MB. */
        const val MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024
        const val TOO_LARGE_MESSAGE =
            "This book is larger than 20 MB. Downloads of large files aren't enabled on the server yet."
    }
}
