package com.framerevive.app

/** One disk-cache download at a time; replacement plans discard obsolete queued work. */
class PhotoPreloader<T>(private val key: (T)->String, private val ready: (T)->Boolean,
    private val load: (T,()->Boolean)->Unit, private val changed:()->Unit = {}) {
    enum class Status { QUEUED, LOADING, READY, FAILED }
    data class Entry<T>(val item:T,val status:Status)
    private val worker = LatestPhotoExecutor()
    private val lock = Any()
    @Volatile private var epoch = 0L
    @Volatile private var closed = false
    private var entries = emptyList<Entry<T>>()
    fun plan(items:List<T>) {
        val selected = items.distinctBy(key).take(15)
        val ticket:Long
        synchronized(lock) {
            if(closed) return
            if(entries.map { key(it.item) } == selected.map(key)) return
            ticket=++epoch
            entries=selected.map { Entry(it,if(ready(it)) Status.READY else Status.QUEUED) }
        }
        worker.execute {
            for(item in selected) {
                if(closed || ticket != epoch) break
                if(ready(item)) { update(item,Status.READY,ticket); continue }
                update(item,Status.LOADING,ticket)
                try { load(item) { !closed && ticket == epoch }; update(item,if(ready(item)) Status.READY else Status.QUEUED,ticket) }
                catch(_:Exception) { update(item,Status.FAILED,ticket) }
            }
        }
    }
    private fun update(item:T,status:Status,ticket:Long) {
        val modified=synchronized(lock) {
            if(ticket == epoch && !closed) {
                entries=entries.map { if(key(it.item)==key(item)) Entry(it.item,status) else it }; true
            } else false
        }
        if(modified) changed()
    }
    fun snapshot():List<Entry<T>> = synchronized(lock) { entries.map { if(ready(it.item)) Entry(it.item,Status.READY) else it } }
    fun clear() = synchronized(lock) { ++epoch; entries=emptyList() }
    fun close() { synchronized(lock) { closed=true; ++epoch; entries=emptyList() }; worker.shutdownNow() }
}
