package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpPlaylistDetectorTest {
    @Test
    fun recognizesKnownYoutubePlaylistLinks() {
        assertTrue(
            YtDlpPlaylistDetector.isLikelyPlaylistUrl(
                "https://www.youtube.com/playlist?list=PL1234567890"
            )
        )
    }

    @Test
    fun recognizesCommonGenericPlaylistShapes() {
        assertTrue(
            YtDlpPlaylistDetector.isLikelyPlaylistUrl(
                "https://www.nicovideo.jp/mylist/12345678"
            )
        )
        assertTrue(
            YtDlpPlaylistDetector.isLikelyPlaylistUrl(
                "https://video.example/watch?playlist_id=abc123"
            )
        )
    }

    @Test
    fun doesNotTreatAnOrdinaryWatchUrlAsAPlaylist() {
        assertFalse(
            YtDlpPlaylistDetector.isLikelyPlaylistUrl(
                "https://www.nicovideo.jp/watch/sm37593092"
            )
        )
        assertFalse(
            YtDlpPlaylistDetector.isLikelyPlaylistUrl(
                "https://video.example/watch?id=123"
            )
        )
    }

    @Test
    fun rejectsNonHttpUrls() {
        assertFalse(YtDlpPlaylistDetector.isLikelyPlaylistUrl("javascript:alert(1)"))
        assertFalse(YtDlpPlaylistDetector.isLikelyPlaylistUrl("file:///tmp/list.m3u8"))
    }
}
