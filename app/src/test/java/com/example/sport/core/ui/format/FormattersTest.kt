package com.example.sport.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

class FormattersTest {

    @Test
    fun numbers_useItalianSeparators() {
        assertEquals("42,5", Formatters.number(42.5))
        assertEquals("80", Formatters.number(80.0))
        assertEquals("1.234,5", Formatters.number(1234.5))
        assertEquals("12.340", Formatters.integer(12_340.4))
        assertEquals("42,5 kg", Formatters.weightKg(42.5))
        assertEquals("—", Formatters.weightKg(null))
    }

    @Test
    fun repRanges() {
        assertEquals("8–12", Formatters.repRange(8, 12))
        assertEquals("10", Formatters.repRange(10, 10))
        assertEquals("6", Formatters.repRange(null, 6))
        assertEquals("—", Formatters.repRange(null, null))
    }

    @Test
    fun durations() {
        assertEquals("1:30", Formatters.duration(90))
        assertEquals("0:05", Formatters.duration(5))
        assertEquals("1:02:05", Formatters.duration(3725))
        assertEquals("45 min", Formatters.durationHuman(Duration.ofMinutes(45)))
        assertEquals("1 h 5 min", Formatters.durationHuman(Duration.ofMinutes(65)))
    }

    @Test
    fun dates_areItalianAndCapitalized() {
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals("Lunedì 9 novembre", Formatters.longDate(monday))
        assertEquals("Mercoledì", Formatters.dayOfWeek(DayOfWeek.WEDNESDAY))
        assertEquals(listOf("L", "M", "M", "G", "V", "S", "D"), DayOfWeek.entries.map(Formatters::dayOfWeekInitial))
    }
}
