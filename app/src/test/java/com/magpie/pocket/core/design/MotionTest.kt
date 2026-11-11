package com.magpie.pocket.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every duration on the page passes through one switch, so a reader can turn all of it off. */
class MotionTest {

    @Test
    fun aSwitchedOffAnimatorLeavesNoDuration() {
        assertEquals(0, motionDuration(baseMillis = 760, animationsEnabled = false))
        assertEquals(0, motionDuration(baseMillis = 40, animationsEnabled = false))
    }

    @Test
    fun aSwitchedOnAnimatorKeepsTheBaseDuration() {
        assertEquals(760, motionDuration(baseMillis = 760, animationsEnabled = true))
        assertEquals(40, motionDuration(baseMillis = 40, animationsEnabled = true))
    }
}
