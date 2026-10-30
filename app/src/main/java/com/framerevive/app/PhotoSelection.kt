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
    fun playlist(photos: List<FramePhoto>, today: LocalDate, random: Random = Random.Default, recentCount: Int = 300, memoryDays: Int = 14, recentPercent: Int = 50): List<FramePhoto> {
        require(recentCount in 1..2000 && memoryDays in 0..60 && recentPercent in 0..100)
        if (photos.isEmpty()) return emptyList()
        val latest = photos.sortedByDescending { it.takenMs }.take(recentCount).shuffled(random)
        val latestIds = latest.map { it.id }.toHashSet()
        val memories = photos.filter { it.date().year < today.year && distance(it.date(), today) <= memoryDays }
            .shuffled(random).filter { p -> recentPercent == 0 || p.id !in latestIds }
        if (memories.isEmpty() || recentPercent == 100) return latest
        if (recentPercent == 0) return memories
        // Ensure each pool has a full turn at ordinary ratios; cap very large cycles.
        val turns = maxOf(kotlin.math.ceil(latest.size * 100.0 / recentPercent).toInt(),
            kotlin.math.ceil(memories.size * 100.0 / (100-recentPercent)).toInt()).coerceAtMost(20000)
        val result = ArrayList<FramePhoto>(turns)
        var balance = 100-recentPercent
        var recentIndex = 0; var memoryIndex = 0
        repeat(turns) {
            balance += recentPercent
            if (balance >= 100) { balance -= 100; result.add(latest[recentIndex++ % latest.size]) }
            else result.add(memories[memoryIndex++ % memories.size])
        }
        return result
    }
}
