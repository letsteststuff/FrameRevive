package com.framerevive.app

/** Receives only events from the frame's dedicated as9061d_key device. */
class FrameButtons {
    enum class Action { NONE, NEXT, PREVIOUS, TOGGLE_PAUSE, CONTROLS }
    private var key = 0
    private var started = 0L
    private var held = false
    fun down(code: Int, now: Long): Action {
        if (key != 0) return Action.NONE
        key = code; started = now; held = false
        return when(code) { PLUS -> Action.NEXT; MINUS -> Action.PREVIOUS; else -> Action.NONE }
    }
    fun hold(now: Long): Action {
        if (key != STAR || held || now - started < HOLD_MS) return Action.NONE
        held = true
        return Action.CONTROLS
    }
    fun up(code: Int, now: Long, canceled: Boolean = false): Action {
        if (code != key) return Action.NONE
        val result = when {
            canceled || held -> Action.NONE
            code == STAR && now - started >= HOLD_MS -> Action.CONTROLS
            code == STAR -> Action.TOGGLE_PAUSE
            else -> Action.NONE
        }
        cancel()
        return result
    }
    fun cancel() { key = 0; held = false }
    companion object {
        const val PLUS = 24
        const val MINUS = 25
        const val STAR = 295
        const val HOLD_MS = 2500L
        fun supports(code: Int) = code == PLUS || code == MINUS || code == STAR
    }
}
