package com.claymachinegames.bookofrecords.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerClockTest {
    private var fakeTime = 0L
    private val clock = MarkerClock { fakeTime }

    @Test
    fun elapsedCountsFromStart() {
        fakeTime = 1_000; clock.start()
        fakeTime = 4_500
        assertEquals(3_500, clock.elapsedMs())
    }

    @Test
    fun pauseFreezesElapsed() {
        fakeTime = 0; clock.start()
        fakeTime = 10_000; clock.pause()
        fakeTime = 60_000
        assertEquals(10_000, clock.elapsedMs())
    }

    @Test
    fun resumeSubtractsPausedTime() {
        fakeTime = 0; clock.start()
        fakeTime = 10_000; clock.pause()
        fakeTime = 30_000; clock.resume()
        fakeTime = 35_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun multiplePauseCyclesAccumulate() {
        fakeTime = 0; clock.start()
        fakeTime = 5_000; clock.pause()
        fakeTime = 10_000; clock.resume()   // 5s pausiert
        fakeTime = 15_000; clock.pause()
        fakeTime = 25_000; clock.resume()   // +10s pausiert
        fakeTime = 30_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun doublePauseAndDoubleResumeAreIdempotent() {
        fakeTime = 0; clock.start()
        fakeTime = 5_000; clock.pause()
        fakeTime = 6_000; clock.pause()
        fakeTime = 10_000; clock.resume()
        fakeTime = 11_000; clock.resume()
        fakeTime = 20_000
        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun notRunningReturnsZero() {
        assertEquals(0, clock.elapsedMs())
    }
}
