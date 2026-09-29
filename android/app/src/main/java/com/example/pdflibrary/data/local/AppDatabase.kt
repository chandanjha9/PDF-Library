package com.example.pdflibrary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.Download
import com.example.pdflibrary.data.model.Favorite
import com.example.pdflibrary.data.model.ReadingProgress

@Database(
    entities = [Book::class, Favorite::class, Download::class, ReadingProgress::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun booksDao(): BooksDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_library.db",
                ).build().also { INSTANCE = it }
            }
    }
}
