package com.example.pdflibrary.ui

import com.example.pdflibrary.ui.common.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test fun fileSize_handlesUnknownAndUnits() {
        assertEquals("PDF", Formatters.fileSize(null))
        assertEquals("PDF", Formatters.fileSize(0))
        assertEquals("512 B", Formatters.fileSize(512))
        assertEquals("2 KB", Formatters.fileSize(2048))
        assertEquals("1.5 MB", Formatters.fileSize(1_572_864))
    }

    @Test fun countdown_formatsMinutesAndSeconds() {
        assertEquals("20:00", Formatters.countdown(1200))
        assertEquals("01:05", Formatters.countdown(65))
        assertEquals("00:00", Formatters.countdown(-3))
    }

    @Test fun initials_areStable() {
        assertEquals("HP", Formatters.initials("Harry Potter and the Stone"))
        assertEquals("PH", Formatters.initials("physics"))
        assertEquals("SM", Formatters.initials("spider_man"))
        assertEquals("?", Formatters.initials("  "))
    }

    @Test fun dates_emptyForMissingValues() {
        assertEquals("", Formatters.dateFromEpochSeconds(0))
        assertEquals("", Formatters.dateFromEpochMillis(-1))
    }
}
