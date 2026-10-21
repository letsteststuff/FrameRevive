package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class WakeScheduleTest {
    @Test fun afterWakeTimeSchedulesTomorrowInsteadOfDroppingAlarm() {
        val now=ZonedDateTime.parse("2026-10-21T07:00:00-04:00[America/New_York]")
        assertEquals("2026-10-22T07:00-04:00[America/New_York]",FrameSchedule.nextOccurrence(now,LocalTime.of(7,0)).toString())
    }
    @Test fun nightSchedulesNextMorning() {
        val now=ZonedDateTime.parse("2026-10-21T23:00:00-04:00[America/New_York]")
        assertEquals(22,FrameSchedule.nextOccurrence(now,LocalTime.of(7,0)).dayOfMonth)
    }
    @Test fun daylightSavingTransitionKeepsLocalWakeTime() {
        val now=ZonedDateTime.parse("2026-10-31T23:00:00-04:00[America/New_York]")
        val next=FrameSchedule.nextOccurrence(now,LocalTime.of(7,0))
        assertEquals(LocalTime.of(7,0),next.toLocalTime())
        assertEquals(-5*3600,next.offset.totalSeconds)
    }
    @Test fun daytimeBrightnessDoesNotFallBackToBrokenSystemAutoBrightness() {
        assertEquals(0.55f,FrameSchedule.awakeBrightness(55),0.001f)
        assertEquals(0.05f,FrameSchedule.awakeBrightness(0),0.001f)
        assertEquals(1f,FrameSchedule.awakeBrightness(200),0.001f)
    }
}
