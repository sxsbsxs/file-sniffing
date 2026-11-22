package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpProgressStagePolicyTest {

    @Test
    fun detectsMergerOutputEvenWhenPercentageIsUnavailable() {
        assertTrue(
            YtDlpProgressStagePolicy.isPostProcessing(
                -1f,
                "[Merger] Merging formats into output.mp4"
            )
        )
        assertEquals(
            "正在合并视频与音频流",
            YtDlpProgressStagePolicy.detail("[Merger] Merging formats into output.mp4")
        )
    }

    @Test
    fun switchesToIndeterminateWhenAllReportedDownloadWorkReachesOneHundredPercent() {
        assertTrue(YtDlpProgressStagePolicy.isPostProcessing(100f, "[download] 100%"))
    }

    @Test
    fun normalDownloadLinesRemainDeterminate() {
        assertFalse(
            YtDlpProgressStagePolicy.isPostProcessing(
                68.4f,
                "[download] 68.4% of 120MiB at 4MiB/s ETA 00:09"
            )
        )
    }
}
