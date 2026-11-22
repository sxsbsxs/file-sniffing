package com.sxsbsxs.filesniffing.data.extractor

import com.sxsbsxs.filesniffing.DownloadOutputCopyProgressPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpOverallProgressPolicyTest {

    @Test
    fun mapsMediaStreamTransferIntoTheFirstEightyPercentOfTheWholeTask() {
        assertEquals(0, YtDlpOverallProgressPolicy.streamDownloadPercent(0f))
        assertEquals(20, YtDlpOverallProgressPolicy.streamDownloadPercent(25f))
        assertEquals(40, YtDlpOverallProgressPolicy.streamDownloadPercent(50f))
        assertEquals(60, YtDlpOverallProgressPolicy.streamDownloadPercent(75f))
        assertEquals(80, YtDlpOverallProgressPolicy.streamDownloadPercent(100f))
    }

    @Test
    fun clampsStreamTransferPercentagesAndHandlesUnknownValues() {
        assertEquals(0, YtDlpOverallProgressPolicy.streamDownloadPercent(Float.NaN))
        assertEquals(0, YtDlpOverallProgressPolicy.streamDownloadPercent(Float.NEGATIVE_INFINITY))
        assertEquals(0, YtDlpOverallProgressPolicy.streamDownloadPercent(-20f))
        assertEquals(80, YtDlpOverallProgressPolicy.streamDownloadPercent(120f))
    }

    @Test
    fun keepsMergeProcessingInsideTheSameOverallProgressRange() {
        assertEquals(80, YtDlpOverallProgressPolicy.postProcessingPercent("started"))
        assertEquals(84, YtDlpOverallProgressPolicy.postProcessingPercent("processing"))
        assertEquals(88, YtDlpOverallProgressPolicy.postProcessingPercent("finished"))
        assertEquals(80, YtDlpOverallProgressPolicy.postProcessingPercent(null))
        assertEquals(90, YtDlpOverallProgressPolicy.postProcessingCompletePercent())
    }

    @Test
    fun mediaTransferMergeAndSaveStagesFitOneContinuousProgressBar() {
        val streamDone = YtDlpOverallProgressPolicy.streamDownloadPercent(100f)
        val mergeStarted = YtDlpOverallProgressPolicy.postProcessingPercent("started")
        val mergeActive = YtDlpOverallProgressPolicy.postProcessingPercent("processing")
        val mergeFinished = YtDlpOverallProgressPolicy.postProcessingPercent("finished")
        val mergeComplete = YtDlpOverallProgressPolicy.postProcessingCompletePercent()
        val copyStarted = DownloadOutputCopyProgressPolicy.percent(0L, 100L)
        val copyComplete = DownloadOutputCopyProgressPolicy.percent(100L, 100L)

        assertEquals(80, streamDone)
        assertEquals(80, mergeStarted)
        assertEquals(84, mergeActive)
        assertEquals(88, mergeFinished)
        assertEquals(90, mergeComplete)
        assertEquals(90, copyStarted)
        assertEquals(99, copyComplete)
    }
}
