package com.framerevive.app

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class FrameLoadingTest {
    @Test fun repeatedNavigationKeepsOnlyTheLatestPendingRequest() {
        val worker = LatestPhotoExecutor()
        val started = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val ran = Collections.synchronizedList(mutableListOf<Int>())
        try {
            worker.execute { started.countDown(); release.await(5,TimeUnit.SECONDS); ran.add(0) }
            assertTrue(started.await(5,TimeUnit.SECONDS))
            for (i in 1..30) worker.execute { ran.add(i); if(i == 30) done.countDown() }
            assertEquals(1,worker.queue.size)
            release.countDown()
            assertTrue(done.await(5,TimeUnit.SECONDS))
            assertEquals(listOf(0,30),ran.toList())
        } finally { release.countDown(); worker.shutdownNow() }
    }
    @Test fun refreshUpdatesUrlsWithoutLosingBackAndForwardHistory() {
        val h = PhotoHistory<Pair<String,String>> { it.first }
        for (id in listOf("a","b","c")) h.record(id to "old")
        h.reconcile(listOf("a" to "new","b" to "new","c" to "new"))
        assertEquals("b" to "new",h.previous())
        h.record(h.previous()!!,true)
        assertEquals("a" to "new",h.previous())
        assertEquals("c" to "new",h.next())
        h.reconcile(listOf("a" to "newer","c" to "newer"))
        assertEquals("c" to "newer",h.next())
    }
    @Test fun removingAllHistoryDuringRefreshLeavesNoNavigation() {
        val h = PhotoHistory<String> { it }; h.record("a"); h.record("b")
        h.reconcile(emptyList())
        assertNull(h.previous()); assertNull(h.next())
    }
}
