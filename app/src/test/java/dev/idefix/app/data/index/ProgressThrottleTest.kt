package dev.idefix.app.data.index

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {

    private var now = 0L
    private val throttle = ProgressThrottle(intervalMs = 100, nowMs = { now })

    @Test
    fun firstCallEmits() {
        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun callsWithinTheIntervalAreSuppressed() {
        assertTrue(throttle.shouldEmit())

        now = 99

        assertFalse(throttle.shouldEmit())
    }

    @Test
    fun emitsAgainOnceTheIntervalHasElapsed() {
        assertTrue(throttle.shouldEmit())

        now = 100

        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun windowsStayMonotonicAcrossSeveralIntervals() {
        assertTrue(throttle.shouldEmit())

        now = 50
        assertFalse(throttle.shouldEmit())
        now = 100
        assertTrue(throttle.shouldEmit())

        now = 175
        assertFalse(throttle.shouldEmit())
        now = 199
        assertFalse(throttle.shouldEmit())
        now = 200
        assertTrue(throttle.shouldEmit())

        now = 250
        assertFalse(throttle.shouldEmit())
        now = 299
        assertFalse(throttle.shouldEmit())
        now = 300
        assertTrue(throttle.shouldEmit())
    }

    @Test
    fun aBackwardsClockNeitherEmitsNorRewindsTheWindow() {
        now = 1_000
        assertTrue(throttle.shouldEmit())

        now = 500
        assertFalse(throttle.shouldEmit())
        now = 1_050
        assertFalse(throttle.shouldEmit())
        now = 1_100
        assertTrue(throttle.shouldEmit())
    }
}
