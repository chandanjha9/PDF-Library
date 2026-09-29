package com.example.pdflibrary.data.local

import androidx.room.*
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.Download
import com.example.pdflibrary.data.model.Favorite
import com.example.pdflibrary.data.model.LibraryEntry
import com.example.pdflibrary.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

// ── Books Cache DAO ──────────────────────────────────────────────────────────

@Dao
interface BooksDao {
    @Query("SELECT * FROM books_cache ORDER BY dateAdded DESC")
    suspend fun getAllBooks(): List<Book>

    @Query("SELECT * FROM books_cache WHERE id = :id")
    suspend fun getBook(id: Int): Book?

    /**
     * Upsert only. The cache is intentionally never cleared: Library rows join
     * against it to display titles, and clearing it on every refresh made saved
     * books lose their names.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)
}

// ── Favorites DAO ────────────────────────────────────────────────────────────

@Dao
interface FavoritesDao {
    @Query(
        """
        SELECT f.bookId AS bookId, f.addedAt AS addedAt,
               b.title AS title, b.author AS author, b.fileSize AS fileSize,
               d.localPath AS localPath, p.lastPage AS lastPage, b.coverUrl AS coverUrl
        FROM favorites f
        LEFT JOIN books_cache b      ON b.id = f.bookId
        LEFT JOIN downloads d        ON d.bookId = f.bookId
        LEFT JOIN reading_progress p ON p.bookId = f.bookId
        ORDER BY f.addedAt DESC
        """
    )
    fun observeFavorites(): Flow<List<LibraryEntry>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE bookId = :bookId)")
    fun isFavorite(bookId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE bookId = :bookId")
    suspend fun removeFavorite(bookId: Int)
}

// ── Downloads DAO ────────────────────────────────────────────────────────────

@Dao
interface DownloadsDao {
    @Query(
        """
        SELECT d.bookId AS bookId, d.downloadedAt AS addedAt,
               b.title AS title, b.author AS author,
               COALESCE(b.fileSize, d.fileSize) AS fileSize,
               d.localPath AS localPath, p.lastPage AS lastPage, b.coverUrl AS coverUrl
        FROM downloads d
        LEFT JOIN books_cache b      ON b.id = d.bookId
        LEFT JOIN reading_progress p ON p.bookId = d.bookId
        ORDER BY d.downloadedAt DESC
        """
    )
    fun observeDownloads(): Flow<List<LibraryEntry>>

    /** Books the user actually opened, most recent first — real data for "Continue reading". */
    @Query(
        """
        SELECT d.bookId AS bookId, p.updatedAt AS addedAt,
               b.title AS title, b.author AS author,
               COALESCE(b.fileSize, d.fileSize) AS fileSize,
               d.localPath AS localPath, p.lastPage AS lastPage, b.coverUrl AS coverUrl
        FROM reading_progress p
        INNER JOIN downloads d  ON d.bookId = p.bookId
        LEFT JOIN books_cache b ON b.id = p.bookId
        ORDER BY p.updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyRead(limit: Int): Flow<List<LibraryEntry>>

    @Query("SELECT * FROM downloads WHERE bookId = :bookId")
    suspend fun getDownload(bookId: Int): Download?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(download: Download)

    @Query("DELETE FROM downloads WHERE bookId = :bookId")
    suspend fun removeDownload(bookId: Int)
}

// ── Reading Progress DAO ─────────────────────────────────────────────────────

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: Int): ReadingProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun clearProgress(bookId: Int)
}
