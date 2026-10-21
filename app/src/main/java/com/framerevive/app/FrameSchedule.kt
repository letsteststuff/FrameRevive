package com.framerevive.app

import java.time.LocalTime

object FrameSchedule {
    fun isSleeping(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start == end) false
        else if (start < end) now >= start && now < end
        else now >= start || now < end
}
