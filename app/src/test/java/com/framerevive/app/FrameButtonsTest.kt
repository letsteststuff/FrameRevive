package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameButtonsTest {
    @Test fun shortPressesAndRepeatsProduceOneAction() {
        val b = FrameButtons()
        assertEquals(FrameButtons.Action.NEXT,b.down(24,100))
        assertEquals(FrameButtons.Action.NONE,b.down(24,200))
        assertEquals(FrameButtons.Action.NONE,b.up(24,900))
        assertEquals(FrameButtons.Action.NONE,b.up(24,1000))
        assertEquals(FrameButtons.Action.PREVIOUS,b.down(25,1100))
        assertEquals(FrameButtons.Action.NONE,b.up(25,1200))
        b.down(295,2000)
        assertEquals(FrameButtons.Action.TOGGLE_PAUSE,b.up(295,3600))
    }
    @Test fun holdOpensControlsWithoutTogglingOnRelease() {
        val b = FrameButtons(); b.down(295,100)
        assertEquals(FrameButtons.Action.NONE,b.hold(2599))
        assertEquals(FrameButtons.Action.CONTROLS,b.hold(2600))
        assertEquals(FrameButtons.Action.NONE,b.hold(4000))
        assertEquals(FrameButtons.Action.NONE,b.up(295,5100))
    }
    @Test fun delayedHoldAndCanceledLifecycleAreHandled() {
        val b = FrameButtons(); b.down(295,0)
        assertEquals(FrameButtons.Action.CONTROLS,b.up(295,5000))
        b.down(295,6000); b.cancel()
        assertEquals(FrameButtons.Action.NONE,b.up(295,6500))
        b.down(24,7000)
        assertEquals(FrameButtons.Action.NONE,b.up(24,7100,true))
    }
}
