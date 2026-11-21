package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpInfoJsonParserTest {

    @Test
    fun readsOnlyActualFormatsAndPairsVideoOnlyWithAnActualAudioFormat() {
        val info = YtDlpInfoJsonParser.parse(singleVideoJson)

        assertEquals("dQw4w9WgXcQ", info.videoId)
        assertEquals("Sample video", info.title)
        assertEquals(listOf("137", "18"), info.selectableVideoFormats.map { it.formatId })
        assertEquals(listOf("137"), info.videoOnlyFormats.map { it.formatId })
        assertEquals(listOf("140"), info.audioOnlyFormats.map { it.formatId })
        assertEquals(listOf("18"), info.muxedFormats.map { it.formatId })
        assertEquals(
            "137+140",
            YtDlpInfoJsonParser.buildFormatSelector(
                info.videoOnlyFormats.single(),
                info.audioOnlyFormats.single()
            )
        )
        assertEquals(
            "18",
            YtDlpInfoJsonParser.buildFormatSelector(info.muxedFormats.single())
        )
    }

    @Test
    fun drmFormatsAreNotOfferedAsSelectableVideoOrAudio() {
        val drmVideo = """
            {
              "id":"dQw4w9WgXcQ",
              "title":"DRM sample",
              "formats":[
                {"format_id":"137","width":1920,"height":1080,"vcodec":"avc1","acodec":"none","has_drm":true},
                {"format_id":"140","vcodec":"none","acodec":"mp4a.40.2","drm_families":["widevine"]}
              ]
            }
        """.trimIndent()
        val info = YtDlpInfoJsonParser.parse(drmVideo)

        assertTrue(info.selectableVideoFormats.isEmpty())
        assertTrue(info.audioOnlyFormats.isEmpty())
    }

    @Test
    fun malformedAndPlaylistPayloadsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            YtDlpInfoJsonParser.parse("not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            YtDlpInfoJsonParser.parse("""{"id":"playlist-1","title":"Playlist","entries":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            YtDlpInfoJsonParser.parse("""{"id":"dQw4w9WgXcQ","formats":[]}""")
        }
    }

    @Test
    fun unsafeFormatIdsAreNotExposedAsSelectableOptions() {
        val unsafeAudioId = singleVideoJson.replace(
            "\"format_id\": \"140\"",
            "\"format_id\": \"140+best\""
        )
        val info = YtDlpInfoJsonParser.parse(unsafeAudioId)

        assertTrue(info.audioOnlyFormats.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            YtDlpInfoJsonParser.buildFormatSelector(info.videoOnlyFormats.single())
        }

        val foreignAudio = YtDlpFormat(
            videoId = "another-video",
            formatId = "140",
            extension = "m4a",
            resolution = null,
            width = null,
            height = null,
            framesPerSecond = null,
            videoCodec = "none",
            audioCodec = "mp4a.40.2",
            bitrateKbps = null,
            audioBitrateKbps = 128.0,
            fileSizeBytes = null,
            protocol = "https",
            language = null,
            isDrm = false
        )
        assertThrows(IllegalArgumentException::class.java) {
            YtDlpInfoJsonParser.buildFormatSelector(info.videoOnlyFormats.single(), foreignAudio)
        }
    }

    @Test
    fun signedMediaUrlsAndRequestHeadersAreNeverRetained() {
        val info = YtDlpInfoJsonParser.parse(singleVideoJson)
        val modelText = info.toString()

        assertFalse(modelText.contains("signed.example"))
        assertFalse(modelText.contains("Bearer secret-header"))
        assertFalse(modelText.contains("token=secret"))
    }

    private companion object {
        val singleVideoJson = """
            {
              "id": "dQw4w9WgXcQ",
              "title": "Sample video",
              "webpage_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
              "thumbnail": "https://img.example/thumb.jpg",
              "duration": 91.5,
              "uploader": "Example channel",
              "formats": [
                {
                  "format_id": "137",
                  "ext": "mp4",
                  "width": 1920,
                  "height": 1080,
                  "vcodec": "avc1",
                  "acodec": "none",
                  "tbr": 2500,
                  "filesize_approx": 12000000,
                  "protocol": "https",
                  "url": "https://signed.example/media?token=secret",
                  "http_headers": {"Authorization": "Bearer secret-header"}
                },
                {
                  "format_id": "140",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "abr": 128,
                  "url": "https://signed.example/audio?token=secret"
                },
                {
                  "format_id": "18",
                  "ext": "mp4",
                  "width": 640,
                  "height": 360,
                  "vcodec": "avc1",
                  "acodec": "mp4a.40.2",
                  "tbr": 650
                }
              ]
            }
        """.trimIndent()
    }
}
