package com.sxsbsxs.filesniffing.data.network

import java.util.ArrayDeque

/**
 * Moving speed average over the most recent time window. Samples use their real elapsed
 * duration, so callback jitter does not turn a single fast interval into an exaggerated rate.
 */
internal class RollingDownloadSpeedWindow(windowMillis: Long = 5_000L) {
    private data class Sample(
        val completedAtNanos: Long,
        val byteDelta: Long,
        val elapsedNanos: Long
    )

    private val windowNanos = windowMillis.coerceAtLeast(1L) * 1_000_000L
    private val samples = ArrayDeque<Sample>()
    private var previousBytes = 0L
    private var previousAtNanos = System.nanoTime()

    @Synchronized
    fun reset(bytes: Long = 0L, nowNanos: Long = System.nanoTime()) {
        samples.clear()
        previousBytes = bytes.coerceAtLeast(0L)
        previousAtNanos = nowNanos
    }

    @Synchronized
    fun record(currentBytes: Long, nowNanos: Long = System.nanoTime()): Long {
        val safeCurrent = currentBytes.coerceAtLeast(0L)
        val elapsed = (nowNanos - previousAtNanos).coerceAtLeast(0L)
        val delta = (safeCurrent - previousBytes).coerceAtLeast(0L)
        previousBytes = safeCurrent
        previousAtNanos = nowNanos

        if (delta > 0L && elapsed > 0L) {
            samples.addLast(Sample(nowNanos, delta, elapsed))
        }

        val cutoff = nowNanos - windowNanos
        while (samples.isNotEmpty() && samples.first().completedAtNanos < cutoff) {
            samples.removeFirst()
        }

        var bytes = 0L
        var elapsedNanos = 0L
        samples.forEach { sample ->
            bytes = safeAdd(bytes, sample.byteDelta)
            elapsedNanos = safeAdd(elapsedNanos, sample.elapsedNanos)
        }
        if (elapsedNanos <= 0L) return 0L
        return (bytes.toDouble() * 1_000_000_000.0 / elapsedNanos)
            .toLong()
            .coerceAtLeast(0L)
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
