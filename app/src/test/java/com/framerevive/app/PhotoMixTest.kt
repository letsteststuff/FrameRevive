package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class PhotoMixTest {
    private fun p(id:String,date:String)=FramePhoto(id,"",LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    @Test fun sliderEndpointsSelectOnlyTheRequestedPool() {
        val photos=listOf(p("recent","2026-09-01"),p("memory","2010-10-20"))
        assertEquals(listOf("recent"),PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,14,100).map { it.id })
        assertEquals(listOf("memory"),PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,14,0).map { it.id })
    }
    @Test fun exactDateWindowAndEmptyMemoryFallbackWork() {
        val photos=listOf(p("recent","2026-09-01"),p("sameDay","2010-10-20"),p("otherDay","2010-10-21"))
        assertEquals(listOf("sameDay"),PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,0,0).map { it.id })
        assertEquals(listOf("recent"),PhotoSelection.playlist(photos,LocalDate.parse("2026-03-01"),Random(1),1,0,0).map { it.id })
    }
    @Test fun weightedMixKeepsTheRequestedBalanceAndVisitsRecentPool() {
        val recent=(1..10).map { p("r$it","2026-09-01") }
        val memories=(1..3).map { p("m$it","2010-10-20") }
        val result=PhotoSelection.playlist(recent+memories,LocalDate.parse("2026-10-20"),Random(1),10,14,25)
        assertEquals(40,result.size)
        assertEquals(10,result.count { it.id.startsWith("r") })
        assertEquals(30,result.count { it.id.startsWith("m") })
        assertEquals(10,result.filter { it.id.startsWith("r") }.distinctBy { it.id }.size)
    }
}
