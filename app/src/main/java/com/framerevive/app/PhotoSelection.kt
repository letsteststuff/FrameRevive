package com.framerevive.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

data class FramePhoto(val id: String, val url: String, val takenMs: Long, val offsetMs: Long = 0, val discoveredMs: Long = 0) {
    fun date(): LocalDate = Instant.ofEpochMilli(takenMs + offsetMs).atZone(ZoneOffset.UTC).toLocalDate()
}

object PhotoSelection {
    // Newly discovered IDs get one turn before the usual mix, including old captures.
    fun prioritize(normal: List<FramePhoto>, photos: List<FramePhoto>, pending: Set<String>): List<FramePhoto> {
        val first = photos.filter { it.id in pending }.distinctBy { it.id }.sortedByDescending { it.discoveredMs }
        return first + normal.filterNot { it.id in pending }
    }
    fun distance(a: LocalDate, b: LocalDate): Int {
        val days = abs(a.withYear(2000).dayOfYear - b.withYear(2000).dayOfYear)
        return min(days, 366 - days)
    }

    // Equal turns for the newest 300 by capture time and a +/-14-day seasonal pool.
    // Each pool is shuffled without replacement; dates are recalculated each day.
    fun playlist(photos: List<FramePhoto>, today: LocalDate, random: Random = Random.Default): List<FramePhoto> {
        if (photos.isEmpty()) return emptyList()
        val latest = photos.sortedByDescending { it.takenMs }.take(300).shuffled(random)
        val memories = photos.filter { it.date().year < today.year && distance(it.date(), today) <= 14 }
            .shuffled(random).filter { p -> latest.none { it.id == p.id } }
        if (memories.isEmpty()) return latest
        val result = ArrayList<FramePhoto>()
        // Repeat smaller pool only after every photo in it has had a turn.
        for (i in 0 until maxOf(latest.size, memories.size)) {
            result.add(latest[i % latest.size])
            result.add(memories[i % memories.size])
        }
        return result
    }
}
