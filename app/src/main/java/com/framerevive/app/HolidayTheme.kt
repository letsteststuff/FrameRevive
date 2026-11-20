package com.framerevive.app

import java.time.LocalDate

object HolidayTheme {
    val labels = linkedMapOf("off" to "Off", "auto" to "Automatic", "christmas" to "Christmas lights",
        "winter" to "Winter snowflakes", "valentine" to "Valentine hearts", "spring" to "Spring flowers",
        "easter" to "Easter eggs", "summer" to "Summer stars", "autumn" to "Autumn leaves", "halloween" to "Halloween")

    fun resolve(choice: String, date: LocalDate): String {
        if (choice != "auto") return choice.takeIf { it in labels } ?: "off"
        val easter = easterSunday(date.year)
        return when {
            date.monthValue == 12 -> "christmas"
            date.monthValue == 2 && date.dayOfMonth in 7..14 -> "valentine"
            !date.isBefore(easter.minusDays(7)) && !date.isAfter(easter) -> "easter"
            date.monthValue == 10 && date.dayOfMonth >= 20 -> "halloween"
            date.monthValue in 1..2 -> "winter"
            date.monthValue in 3..5 -> "spring"
            date.monthValue in 6..8 -> "summer"
            else -> "autumn"
        }
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = (19 * a + b - b / 4 - (b - (b + 8) / 25 + 1) / 3 + 15) % 30
        val e = (32 + 2 * (b % 4) + 2 * (c / 4) - d - c % 4) % 7
        val f = (a + 11 * d + 22 * e) / 451
        val value = d + e - 7 * f + 114
        return LocalDate.of(year, value / 31, value % 31 + 1)
    }
}
