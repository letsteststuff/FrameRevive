package com.framerevive.app

import java.time.LocalTime
import java.time.ZonedDateTime

object FrameSchedule {
    fun nextOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val next = now.with(time).withSecond(0).withNano(0)
        return if (next.isAfter(now)) next else now.plusDays(1).with(time).withSecond(0).withNano(0)
    }
    fun awakeBrightness(percent: Int): Float = percent.coerceIn(5, 100) / 100f
    fun isSleeping(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start == end) false
        else if (start < end) now >= start && now < end
        else now >= start || now < end
}
