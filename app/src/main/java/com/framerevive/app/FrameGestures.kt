package com.framerevive.app

/** Lenovo's gesture key mapping, rotated exactly as its original photo app did. */
class FrameGestures {
    private var lastAccepted: Long? = null
    fun accept(keyCode: Int, rotation: Int, now: Long): Int {
        val action = direction(keyCode, rotation)
        if (action == 0 || lastAccepted?.let { now - it < 800 } == true) return 0
        lastAccepted = now
        return action
    }
    companion object {
        /** 1 = next (left wave), -1 = previous (right wave), 0 = ignore. */
        fun direction(keyCode: Int, rotation: Int): Int = when (rotation) {
            0 -> when (keyCode) { 289 -> -1; 290 -> 1; else -> 0 }
            1 -> when (keyCode) { 291 -> 1; 292 -> -1; else -> 0 }
            2 -> when (keyCode) { 289 -> 1; 290 -> -1; else -> 0 }
            3 -> when (keyCode) { 291 -> -1; 292 -> 1; else -> 0 }
            else -> 0
        }
    }
}

/** History contains only photos actually displayed, never pending downloads or bitmaps. */
class PhotoHistory<T>(private val capacity: Int = 60, private val key: (T) -> String) {
    init { require(capacity >= 2) }
    private val items = ArrayList<T>()
    private var cursor = -1
    fun previous(): T? = items.getOrNull(cursor - 1)
    fun next(): T? = items.getOrNull(cursor + 1)
    fun clear() { items.clear(); cursor = -1 }
    /** Keep displayed-photo history across same-album refreshes, replacing temporary URLs. */
    fun reconcile(fresh: Collection<T>) {
        val valid = fresh.associateBy(key)
        val current = items.getOrNull(cursor)?.let(key)
        val before = items.take(cursor + 1).count { key(it) in valid }
        val kept = items.mapNotNull { valid[key(it)] }
        items.clear(); items.addAll(kept)
        cursor = if (current != null && current in valid) items.indexOfFirst { key(it) == current }
            else (before - 1).coerceIn(-1, items.lastIndex.coerceAtLeast(-1))
    }

    fun record(item: T, fromHistory: Boolean = false) {
        val id = key(item)
        if (items.getOrNull(cursor)?.let(key) == id) return
        if (fromHistory && previous()?.let(key) == id) { cursor--; return }
        if (fromHistory && next()?.let(key) == id) { cursor++; return }
        while (items.size > cursor + 1) items.removeAt(items.lastIndex)
        items.add(item)
        if (items.size > capacity) items.removeAt(0)
        cursor = items.lastIndex
    }
    fun forget(item: T) {
        val id = key(item)
        for (i in items.lastIndex downTo 0) {
            if (key(items[i]) == id) { items.removeAt(i); if (i <= cursor) cursor-- }
        }
    }
}
