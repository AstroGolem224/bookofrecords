package com.claymachinegames.bookofrecords.domain

/**
 * Marker offsets must match audio position, so paused time is excluded.
 * Inject a monotonic clock (SystemClock::elapsedRealtime in production).
 */
class MarkerClock(private val now: () -> Long) {
    private var startedAt = 0L
    private var pausedTotal = 0L
    private var pauseStartedAt = -1L
    var running = false
        private set

    fun start() {
        startedAt = now(); pausedTotal = 0; pauseStartedAt = -1; running = true
    }

    fun pause() {
        if (running && pauseStartedAt < 0) pauseStartedAt = now()
    }

    fun resume() {
        if (running && pauseStartedAt >= 0) {
            pausedTotal += now() - pauseStartedAt
            pauseStartedAt = -1
        }
    }

    fun stop() { running = false }

    fun elapsedMs(): Long {
        if (!running) return 0
        val effectiveNow = if (pauseStartedAt >= 0) pauseStartedAt else now()
        return effectiveNow - startedAt - pausedTotal
    }
}
