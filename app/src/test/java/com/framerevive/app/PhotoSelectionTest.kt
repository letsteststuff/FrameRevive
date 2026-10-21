package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random

class PhotoSelectionTest {
    private fun photo(id: String, date: String) = FramePhoto(id, "", LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    @Test fun calendarWrapsAtNewYearAndHandlesLeapDay() {
        assertEquals(1, PhotoSelection.distance(LocalDate.parse("2020-12-31"), LocalDate.parse("2026-01-01")))
        assertEquals(1, PhotoSelection.distance(LocalDate.parse("2020-02-29"), LocalDate.parse("2026-03-01")))
    }
    @Test fun emptyAlbumIsSafe() { assertTrue(PhotoSelection.playlist(emptyList(), LocalDate.now()).isEmpty()) }
    @Test fun newlyAddedOlderPhotoJoinsRecentPool() {
        val existing = (0..299).map { photo("existing$it", "2025-01-01") }
        val addition = photo("newlyAdded", "2008-01-01").copy(discoveredMs = photo("now", "2026-10-20").takenMs)
        assertTrue(PhotoSelection.playlist(existing + addition, LocalDate.parse("2026-10-20")).any { it.id == "newlyAdded" })
    }
    @Test fun oldAlbumStillDisplaysNewestAvailable() {
        val photos = listOf(photo("old", "2019-04-01"), photo("new", "2020-05-01"))
        assertEquals(setOf("old", "new"), PhotoSelection.playlist(photos, LocalDate.parse("2026-10-20")).map { it.id }.toSet())
    }
    @Test fun mixesNewestWithSeasonalMemories() {
        val latest = (0..299).map { photo("new$it", "2026-08-01") }
        val result = PhotoSelection.playlist(latest + photo("memory", "2010-10-21") + photo("other", "2010-03-01"), LocalDate.parse("2026-10-20"), Random(42))
        assertEquals(600, result.size)
        assertTrue(result.filterIndexed { i, _ -> i % 2 == 1 }.all { it.id == "memory" })
        assertEquals(300, result.filterIndexed { i, _ -> i % 2 == 0 }.map { it.id }.toSet().size)
        assertFalse(result.any { it.id == "other" })
    }
    @Test fun seasonalPoolExcludesCurrentYear() {
        val photos = (0..300).map { photo("p$it", "2026-10-20") }
        assertEquals(300, PhotoSelection.playlist(photos, LocalDate.parse("2026-10-20")).size)
    }
    @Test fun overnightScheduleHasCorrectBoundaries() {
        fun sleeping(time: String) = FrameSchedule.isSleeping(LocalTime.parse(time), LocalTime.parse("22:00"), LocalTime.parse("07:00"))
        assertTrue(sleeping("22:00")); assertTrue(sleeping("00:00")); assertTrue(sleeping("06:59"))
        assertFalse(sleeping("07:00")); assertFalse(sleeping("21:59"))
    }
    @Test fun daytimeScheduleAndEqualTimes() {
        assertTrue(FrameSchedule.isSleeping(LocalTime.NOON, LocalTime.of(10,0), LocalTime.of(14,0)))
        assertFalse(FrameSchedule.isSleeping(LocalTime.of(14,0), LocalTime.of(10,0), LocalTime.of(14,0)))
        assertFalse(FrameSchedule.isSleeping(LocalTime.NOON, LocalTime.NOON, LocalTime.NOON))
    }
}
