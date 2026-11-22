package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpProgressStagePolicyTest {

    @Test
    fun detectsMergerOutputEvenWhenPercentageIsUnavailable() {
        assertTrue(YtDlpProgressStagePolicy.isPostProcessing("[Merger] Merging formats into output.mp4"))
        assertEquals(
            "正在合并视频与音频流",
            YtDlpProgressStagePolicy.detail("[Merger] Merging formats into output.mp4")
        )
    }

    @Test
    fun perStreamHundredPercentDoesNotPrematurelyEndMultiStreamDownload() {
        assertFalse(YtDlpProgressStagePolicy.isPostProcessing("[download] 100% of 80MiB"))
        assertFalse(YtDlpProgressStagePolicy.isPostProcessing(
            """FILE_SNIFFING_PROGRESS|137|{"status":"finished","downloaded_bytes":83886080}"""
        ))
    }

    @Test
    fun detectsExplicitPostprocessorMarkers() {
        assertTrue(YtDlpProgressStagePolicy.isPostProcessing("[ExtractAudio] Destination: audio.m4a"))
        assertTrue(YtDlpProgressStagePolicy.isPostProcessing("[VideoRemuxer] Remuxing video"))
        assertTrue(YtDlpProgressStagePolicy.isPostProcessing("Post-process: fixing container"))
    }

    @Test
    fun normalDownloadLinesRemainOutsidePostProcessing() {
        assertFalse(YtDlpProgressStagePolicy.isPostProcessing(
            "[download] 68.4% of 120MiB at 4MiB/s ETA 00:09"
        ))
    }
}
