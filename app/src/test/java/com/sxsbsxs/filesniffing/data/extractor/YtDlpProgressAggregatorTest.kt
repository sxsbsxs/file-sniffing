package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpProgressAggregatorTest {
    @Test
    fun combinesTwoFormatStreamsWithoutProgressGoingBackWhenSecondStarts() {
        val aggregator = YtDlpProgressAggregator(expectedStreams = 2)
        val videoHalf = aggregator.update(
            YtDlpLineMetrics(
                streamId = "137", status = "downloading", downloadedBytes = 50L,
                totalBytes = 100L, speedBytesPerSecond = 10L, progressPercent = 50f
            )
        )
        assertEquals(25f, videoHalf.progressPercent, 0.01f)
        assertEquals(-1L, videoHalf.totalBytes)
        assertFalse(videoHalf.allExpectedStreamsFinished)

        val audioStarts = aggregator.update(
            YtDlpLineMetrics(
                streamId = "140", status = "downloading", downloadedBytes = 0L,
                totalBytes = 40L, speedBytesPerSecond = 5L, progressPercent = 0f
            )
        )
        assertEquals(25f, audioStarts.progressPercent, 0.01f)
        assertEquals(15L, audioStarts.speedBytesPerSecond)
        assertEquals(140L, audioStarts.totalBytes)
    }

    @Test
    fun completedStreamRemainsCountedWhileSecondStreamDownloads() {
        val aggregator = YtDlpProgressAggregator(expectedStreams = 2)
        val first = aggregator.update(
            YtDlpLineMetrics(streamId = "137", status = "finished", downloadedBytes = 100L, totalBytes = 100L)
        )
        val second = aggregator.update(
            YtDlpLineMetrics(
                streamId = "140", status = "downloading", downloadedBytes = 20L,
                totalBytes = 40L, progressPercent = 50f
            )
        )
        assertTrue(second.progressPercent >= first.progressPercent)
        assertEquals(120L, second.downloadedBytes)
        assertEquals(140L, second.totalBytes)
    }

    @Test
    fun completesWhenAllSelectedStreamsFinish() {
        val aggregator = YtDlpProgressAggregator(expectedStreams = 2)
        aggregator.update(YtDlpLineMetrics(streamId = "137", status = "finished", downloadedBytes = 100L, totalBytes = 100L))
        val result = aggregator.update(
            YtDlpLineMetrics(streamId = "140", status = "finished", downloadedBytes = 40L, totalBytes = 40L)
        )
        assertTrue(result.allExpectedStreamsFinished)
        assertEquals(100f, result.progressPercent, 0.01f)
        assertEquals(140L, result.downloadedBytes)
        assertEquals(140L, result.totalBytes)
    }
}
