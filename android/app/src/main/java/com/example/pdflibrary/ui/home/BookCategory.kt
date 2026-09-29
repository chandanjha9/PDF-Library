package com.example.pdflibrary.ui.home

import com.example.pdflibrary.data.model.Book

/**
 * Client-side category filter. The backend has no category field yet, so books
 * are matched on title/description keywords.
 *
 * Previously an empty category silently fell back to showing *all* books,
 * which looked like the filter was broken. Now an empty category is empty and
 * the UI shows an explicit empty state.
 */
enum class BookCategory(val label: String, private val keywords: List<String>) {
    All("All", emptyList()),
    Comics("Comics", listOf("comic", "spider", "batman", "superman", "flash", "hulk", "marvel", "dc ", "avengers", "x-men", "cbz", "manga")),
    Stories("Stories", listOf("story", "stories", "tale", "fiction", "love", "life", "fairy")),
    Science("Science", listOf("science", "physics", "chemistry", "chem", "biology", "organic", "astronomy", "ncert")),
    Novels("Novels", listOf("novel", "leader", "witch", "stegano", "mystery", "thriller", "romance")),
    Worksheets("Worksheets", listOf("worksheet", "grade", "class ", "math", "algebra", "exercise", "practice"));

    fun matches(book: Book): Boolean {
        if (this == All) return true
        val text = " " + (book.title + " " + (book.description ?: "")).lowercase() + " "
        return keywords.any { text.contains(it) }
    }
}

fun List<Book>.filterBy(category: BookCategory): List<Book> =
    if (category == BookCategory.All) this else filter(category::matches)
