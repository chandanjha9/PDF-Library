package com.example.pdflibrary.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure helpers (unit-tested in FormattersTest). */
object Formatters {

    fun fileSize(bytes: Long?): String = when {
        bytes == null || bytes <= 0 -> "PDF"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** Backend `date_added` is epoch *seconds*. */
    fun dateFromEpochSeconds(seconds: Long): String =
        if (seconds <= 0) "" else dateFromEpochMillis(seconds * 1000)

    fun dateFromEpochMillis(millis: Long): String =
        if (millis <= 0) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

    /** mm:ss countdown. */
    fun countdown(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    /** Up to two initials for a cover placeholder, e.g. "Harry Potter" -> "HP". */
    fun initials(title: String): String {
        val words = title.split(Regex("[\\s_\\-.]+")).filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}
