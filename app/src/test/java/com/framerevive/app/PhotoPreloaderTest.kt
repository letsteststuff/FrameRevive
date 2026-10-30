package com.framerevive.app

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PhotoPreloaderTest {
    private fun await(condition:()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
        while(!condition() && System.nanoTime()<end) Thread.sleep(10)
        assertTrue(condition())
    }
    @Test fun snapshotShowsLoadingQueuedAndReady() {
        val cache=Collections.synchronizedSet(HashSet<String>())
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val q=PhotoPreloader<String>({it},{it in cache},{item,current ->
            if(item=="a") { entered.countDown(); release.await(4,TimeUnit.SECONDS) }
            if(current()) cache.add(item)
        })
        try {
            q.plan(listOf("a","b","c"));assertTrue(entered.await(4,TimeUnit.SECONDS))
            assertEquals(listOf(PhotoPreloader.Status.LOADING,PhotoPreloader.Status.QUEUED,PhotoPreloader.Status.QUEUED),q.snapshot().map { it.status })
            release.countDown();await { q.snapshot().all { it.status==PhotoPreloader.Status.READY } }
            assertEquals(setOf("a","b","c"),cache)
        } finally { release.countDown();q.close() }
    }
    @Test fun replacementPlanDiscardsObsoleteDownloads() {
        val cache=Collections.synchronizedSet(HashSet<String>())
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val q=PhotoPreloader<String>({it},{it in cache},{item,current ->
            if(item=="a") { entered.countDown();release.await(4,TimeUnit.SECONDS) }
            if(current()) cache.add(item)
        })
        try {
            q.plan(listOf("a","b"));assertTrue(entered.await(4,TimeUnit.SECONDS))
            q.plan(listOf("c","d"));release.countDown()
            await { q.snapshot().all { it.status==PhotoPreloader.Status.READY } }
            assertEquals(setOf("c","d"),cache)
            q.clear();assertTrue(q.snapshot().isEmpty())
        } finally { release.countDown();q.close() }
    }
    @Test fun aFailedDownloadDoesNotBlockTheRestOfThePlan() {
        val cache=Collections.synchronizedSet(HashSet<String>())
        val q=PhotoPreloader<String>({it},{it in cache},{item,_ ->if(item=="bad") error("test failure") else cache.add(item)})
        try {
            q.plan(listOf("bad","ok"));await { q.snapshot().last().status==PhotoPreloader.Status.READY }
            assertEquals(PhotoPreloader.Status.FAILED,q.snapshot().first().status)
        } finally { q.close() }
    }
}
