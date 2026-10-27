package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class FrameGesturesTest {
    @Test fun originalLenovoMappingWorksInAllOrientations() {
        val left = listOf(290,291,289,292)
        val right = listOf(289,292,290,291)
        for (rotation in 0..3) {
            assertEquals(1,FrameGestures.direction(left[rotation],rotation))
            assertEquals(-1,FrameGestures.direction(right[rotation],rotation))
            for (key in 289..294) if(key != left[rotation] && key != right[rotation])
                assertEquals(0,FrameGestures.direction(key,rotation))
        }
        assertEquals(0,FrameGestures.direction(24,1))
        assertEquals(0,FrameGestures.direction(291,4))
    }
    @Test fun cooldownFiltersDoubleWavesButDoesNotConsumeIgnoredKeys() {
        val g=FrameGestures()
        assertEquals(1,g.accept(291,1,1))
        assertEquals(0,g.accept(292,1,800))
        assertEquals(0,g.accept(289,1,801))
        assertEquals(-1,g.accept(292,1,801))
        assertEquals(1,g.accept(291,1,1601))
    }
    @Test fun previousAndForwardReturnActuallyDisplayedPhotos() {
        val h=PhotoHistory<String> {it}
        assertNull(h.previous());assertNull(h.next())
        h.record("A");assertNull(h.previous())
        h.record("B");h.record("C")
        assertEquals("B",h.previous());assertEquals("B",h.previous()) // Peeking never commits a pending image.
        h.record("B",true);assertEquals("A",h.previous());assertEquals("C",h.next())
        h.record("A",true);assertNull(h.previous());assertEquals("B",h.next())
        h.record("B",true);h.record("C",true);assertNull(h.next())
    }
    @Test fun playlistRepeatsAreNewVisitsAndHistoryIsBounded() {
        val h=PhotoHistory<String>(3) {it}
        h.record("A");h.record("B");h.record("A")
        assertEquals("B",h.previous());assertNull(h.next())
        h.record("C");h.record("A",true);h.record("B",true)
        assertNull(h.previous());assertEquals("A",h.next())
    }
    @Test fun failedImagesCanBeForgottenAndAlbumSwitchClearsHistory() {
        val h=PhotoHistory<String> {it}
        h.record("A");h.record("B");h.record("C");h.record("B",true)
        h.forget("A");assertNull(h.previous());assertEquals("C",h.next())
        h.forget("C");assertNull(h.next())
        h.record("D");assertEquals("B",h.previous())
        h.clear();assertNull(h.previous());assertNull(h.next())
        h.record("E");assertNull(h.previous())
    }
}
