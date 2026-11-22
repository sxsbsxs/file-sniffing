package com.sxsbsxs.filesniffing.presentation.sniffing

import com.sxsbsxs.filesniffing.ResourceKind
import com.sxsbsxs.filesniffing.SniffedResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFormatComposerTest {
    private val url = "https://www.nicovideo.jp/watch/sm37593092"

    @Test
    fun muxedVideoKeepsItsEmbeddedAudioWithoutAddingAnotherTrack() {
        val video = video(
            videoId = "18",
            embeddedAudio = true,
            size = 120L
        )
        val result = DownloadFormatComposer.video(video)

        assertEquals("18", result?.formatSelector)
        assertEquals(120L, result?.fileSizeBytes)
        assertEquals(null, result?.audioFormatId)
        assertTrue(result?.qualityLabel?.contains("内嵌音轨") == true)
        assertNull(DownloadFormatComposer.video(video, audio("140")))
    }

    @Test
    fun videoOnlyFormatUsesTheSelectedAudioAndAddsEstimatedSizes() {
        val video = video(
            videoId = "137",
            embeddedAudio = false,
            size = 100L
        )
        val audio = audio(
            id = "251",
            size = 40L,
            language = "ja"
        )

        val result = DownloadFormatComposer.video(video, audio)

        assertEquals("137+251", result?.formatSelector)
        assertEquals(140L, result?.fileSizeBytes)
        assertEquals("251", result?.audioFormatId)
        assertEquals("ja", result?.audioLanguage)
        assertTrue(result?.fileName?.contains("audio-251") == true)
    }

    @Test
    fun videoOnlyFormatRequiresAnAudioTrack() {
        assertNull(
            DownloadFormatComposer.video(
                video(videoId = "137", embeddedAudio = false, size = 100L),
                null
            )
        )
    }

    @Test
    fun audioFromAnotherVideoCannotBePaired() {
        val foreignAudio = audio("140").copy(url = "https://example.com/watch/other")
        assertNull(
            DownloadFormatComposer.video(
                video(videoId = "137", embeddedAudio = false, size = 100L),
                foreignAudio
            )
        )
    }

    @Test
    fun unsafeFormatIdsCannotEnterTheSelector() {
        assertNull(
            DownloadFormatComposer.video(
                video(videoId = "137+best", embeddedAudio = false, size = 100L),
                audio("140")
            )
        )
        assertNull(
            DownloadFormatComposer.audio(
                audio("140/best")
            )
        )
    }

    private fun video(
        videoId: String,
        embeddedAudio: Boolean,
        size: Long
    ) = SniffedResource(
        url = url,
        kind = ResourceKind.VIDEO,
        fileName = "sample-1080p.mp4",
        mimeType = "video/mp4",
        pageTitle = "Sample",
        sourcePage = url,
        formatSelector = if (embeddedAudio) videoId else "$videoId+140",
        qualityLabel = "1080p",
        fileSizeBytes = size,
        videoFormatId = videoId,
        audioFormatId = if (embeddedAudio) null else "140",
        hasEmbeddedAudio = embeddedAudio,
        audioQualityLabel = if (embeddedAudio) "内嵌音轨" else "128 kbps"
    )

    private fun audio(
        id: String,
        size: Long? = 40L,
        language: String? = null
    ) = SniffedResource(
        url = url,
        kind = ResourceKind.AUDIO,
        fileName = "sample-audio.m4a",
        mimeType = "audio/mp4",
        pageTitle = "Sample",
        sourcePage = url,
        formatSelector = id,
        qualityLabel = "128 kbps",
        fileSizeBytes = size,
        audioFormatId = id,
        audioLanguage = language,
        hasEmbeddedAudio = true,
        codecLabel = "AAC"
    )
}
