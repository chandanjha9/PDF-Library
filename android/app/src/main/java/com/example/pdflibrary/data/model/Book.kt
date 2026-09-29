package com.example.pdflibrary.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Canonical Book model — mirrors the backend's JSON response AND serves
 * as the Room entity for the local cache.
 */
@Entity(tableName = "books_cache")
data class Book(
    @PrimaryKey
    val id: Int,
    val title: String,
    val description: String?,
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("date_added") val dateAdded: Long,
    @SerializedName("cover_url") val coverUrl: String?,
    val author: String?,
)

/** Files above this size are Premium (unlock with ₹10 UPI or a rewarded ad). Mirrors the backend. */
const val PREMIUM_MIN_BYTES: Long = 20L * 1024 * 1024

val Book.isPremium: Boolean get() = fileSize > PREMIUM_MIN_BYTES

/** GET /books/{id}/unlock */
data class UnlockStatus(
    val premium: Boolean = false,
    val unlocked: Boolean = false,
    val available: Boolean = true,
    /** "download" = app downloads after unlock; "manual" = admin sends the book on WhatsApp/Telegram. */
    val delivery: String? = null,
    /** Manual orders: "pending" | "sent". */
    val status: String? = null,
    val contact: String? = null,
) {
    val isManual: Boolean get() = delivery == "manual"
}

/** POST /books/{id}/unlock */
data class UnlockPayload(
    @SerializedName("device_id") val deviceId: String,
    val method: String, // "upi" | "ad"
    val ref: String?,
    /** WhatsApp number / Telegram username for manual delivery. */
    val contact: String?,
    /** true when re-registering an existing unlock after a server restart (no new admin alert). */
    val restore: Boolean = false,
)

/** Response envelope for search and list endpoints */
data class BooksResponse(
    val books: List<Book>,
    val count: Int?,
    val total: Int?,
    val query: String?,
)

/** Download URL response from GET /books/{id}/download */
data class DownloadResponse(
    @SerializedName("book_id") val bookId: Int?,
    @SerializedName("session_id") val sessionId: String?,
    val title: String?,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("expires_at") val expiresAt: Long?,
    @SerializedName("ttl_seconds") val ttlSeconds: Long?,
)

/** Payload sent when user requests a book */
data class BookRequestPayload(
    @SerializedName("book_name") val bookName: String,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("device_id") val deviceId: String,
)

/** Response from POST /books/request */
data class BookRequestResponse(
    val found: Boolean = false,
    @SerializedName("session_id") val sessionId: String? = null,
    val book: Book? = null,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("ttl_seconds") val ttlSeconds: Long? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("related_books") val relatedBooks: List<Book>? = null,
    val message: String? = null,
    val error: String? = null,
)

/** Model for active 20-minute sessions (wire format). */
data class ActiveSession(
    @SerializedName("session_id") val sessionId: String,
    val book: Book,
    @SerializedName("expires_at") val expiresAt: Long,
    @SerializedName("ttl_seconds") val ttlSeconds: Long,
    @SerializedName("download_url") val downloadUrl: String,
)

data class ActiveSessionsResponse(
    @SerializedName("active_sessions") val activeSessions: List<ActiveSession>?,
    val count: Int?,
)

/**
 * UI model for a 20-minute pass. Expiry is computed on the *device* clock from
 * ttl_seconds at fetch time, so a phone whose clock is off by minutes still
 * shows the correct countdown (the server's expires_at uses the server clock).
 */
data class PassSession(
    val sessionId: String,
    val book: Book,
    val expiresAtLocalMs: Long,
) {
    fun remainingSeconds(nowMs: Long): Long = ((expiresAtLocalMs - nowMs) / 1000).coerceAtLeast(0)
}

/** Generic error envelope returned by the backend (`{ error, message, related_books }`). */
data class ApiError(
    val error: String? = null,
    val message: String? = null,
    @SerializedName("related_books") val relatedBooks: List<Book>? = null,
)
