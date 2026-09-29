package com.example.pdflibrary.ui

import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.theme.coverGradientFor
import com.example.pdflibrary.ui.home.BookCategory
import com.example.pdflibrary.ui.home.filterBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCategoryTest {

    private fun book(id: Int, title: String, desc: String? = null) =
        Book(id = id, title = title, description = desc, fileId = "f$id", fileSize = 0, dateAdded = 0, coverUrl = null, author = null)

    private val books = listOf(
        book(1, "Amazing Spider-Man #1"),
        book(2, "Organic Chemistry Notes"),
        book(3, "Grade 5 Math Worksheet"),
        book(4, "Random Title"),
    )

    @Test fun all_returnsEverything() {
        assertEquals(books, books.filterBy(BookCategory.All))
    }

    @Test fun categories_matchKeywords() {
        assertEquals(listOf(1), books.filterBy(BookCategory.Comics).map { it.id })
        assertEquals(listOf(2), books.filterBy(BookCategory.Science).map { it.id })
        assertEquals(listOf(3), books.filterBy(BookCategory.Worksheets).map { it.id })
    }

    @Test fun emptyCategory_isEmpty_notAllBooks() {
        // Regression: the old filter silently fell back to showing every book.
        assertTrue(listOf(book(9, "Random")).filterBy(BookCategory.Novels).isEmpty())
    }

    @Test fun coverGradient_neverCrashesOnNegativeIds() {
        coverGradientFor(-7)
        coverGradientFor(Int.MIN_VALUE)
    }
}
