package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HolidayThemeTest {
    private fun automatic(date: String) = HolidayTheme.resolve("auto", LocalDate.parse(date))

    @Test fun seasonalWindowsAndHolidayOverrides() {
        assertEquals("winter", automatic("2026-01-01"))
        assertEquals("winter", automatic("2026-02-06"))
        assertEquals("valentine", automatic("2026-02-07"))
        assertEquals("valentine", automatic("2026-02-14"))
        assertEquals("winter", automatic("2026-02-15"))
        assertEquals("spring", automatic("2026-03-28"))
        assertEquals("easter", automatic("2026-03-29"))
        assertEquals("easter", automatic("2026-04-05"))
        assertEquals("spring", automatic("2026-04-06"))
        assertEquals("easter", automatic("2027-03-28"))
        assertEquals("summer", automatic("2026-08-31"))
        assertEquals("autumn", automatic("2026-09-01"))
        assertEquals("autumn", automatic("2026-10-19"))
        assertEquals("halloween", automatic("2026-10-20"))
        assertEquals("halloween", automatic("2026-10-31"))
        assertEquals("autumn", automatic("2026-11-01"))
        assertEquals("christmas", automatic("2026-12-01"))
        assertEquals("christmas", automatic("2026-12-31"))
    }

    @Test fun manualChoicesOverrideCalendarAndUnknownValuesAreOff() {
        val date = LocalDate.of(2026, 11, 11)
        for (choice in HolidayTheme.labels.keys.filter { it != "auto" }) {
            assertEquals(choice, HolidayTheme.resolve(choice, date))
        }
        assertEquals("off", HolidayTheme.resolve("unknown", date))
    }
}
