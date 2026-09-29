package com.example.pdflibrary.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a user's favourite book IDs.
 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val bookId: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Tracks a downloaded book's local file path and download state.
 */
@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val bookId: Int,
    val localPath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,
)

/**
 * Stores the last-read page for each book (for resume reading).
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val bookId: Int,
    val lastPage: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Row for Library / Continue Reading lists: a favourite or download joined with
 * the cached book metadata so the UI can show real titles instead of "Book #12".
 * Book columns are nullable because the join is a LEFT JOIN.
 */
data class LibraryEntry(
    val bookId: Int,
    val addedAt: Long,
    val title: String?,
    val author: String?,
    val fileSize: Long?,
    val localPath: String?,
    val lastPage: Int?,
)
