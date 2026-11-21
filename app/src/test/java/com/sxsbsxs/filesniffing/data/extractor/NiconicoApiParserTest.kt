package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NiconicoApiParserTest {
    @Test fun parsesCurrentDomandTracksAndBuildsVideoAudioPairs() {
        val json = """
            {
              "meta":{"status":200},
              "data":{
                "video":{"title":"Sample Niconico video"},
                "client":{"watchTrackId":"watch-track"},
                "media":{"domand":{
                  "accessRightKey":"temporary-access-key",
                  "videos":[
                    {"id":"v1080","isAvailable":true},
                    {"id":"v720","isAvailable":true},
                    {"id":"v480","isAvailable":false}
                  ],
                  "audios":[
                    {"id":"a256","isAvailable":true},
                    {"id":"a128","isAvailable":true}
                  ]
                }}
              }
            }
        """.trimIndent()

        val parsed = NiconicoApiParser.parseWatchResponse(json)
        assertEquals("Sample Niconico video", parsed.title)
        assertEquals(listOf("v1080", "v720"), parsed.videoIds)
        assertEquals(listOf("a256", "a128"), parsed.audioIds)
        assertEquals(4, parsed.outputCount)
        assertEquals(4, NiconicoApiParser.buildOutputs(parsed).size())
    }

    @Test fun acceptsContentsTrackShape() {
        val json = """
            {
              "meta":{"status":200},
              "data":{
                "video":{"title":"Legacy DMS shape"},
                "client":{"watchTrackId":"track"},
                "media":{"accessRightKey":"key","contents":{
                  "videos":[{"id":"video-a"}],
                  "audios":[{"id":"audio-a"}]
                }}
              }
            }
        """.trimIndent()

        val parsed = NiconicoApiParser.parseWatchResponse(json)
        assertEquals(listOf("video-a"), parsed.videoIds)
        assertEquals(listOf("audio-a"), parsed.audioIds)
    }

    @Test fun reportsPremiumAndRegionRestrictionsWithoutBypassingThem() {
        val json = """{"meta":{"status":403},"data":{"reasonCode":"PREMIUM_ONLY"}}"""
        try {
            NiconicoApiParser.parseWatchResponse(json)
            fail("Expected Niconico access error")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("Premium"))
        }

        val regional = """{"meta":{"status":403},"data":{"reasonCode":"DOMESTIC_VIDEO"}}"""
        try {
            NiconicoApiParser.parseWatchResponse(regional)
            fail("Expected region restriction")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("地区"))
        }
    }

    @Test fun validatesReturnedHlsUrl() {
        assertEquals(
            "https://media.example.test/master.m3u8",
            NiconicoApiParser.parseHlsContentUrl(
                """{"meta":{"status":200},"data":{"contentUrl":"https://media.example.test/master.m3u8"}}"""
            )
        )
        try {
            NiconicoApiParser.parseHlsContentUrl(
                """{"meta":{"status":200},"data":{"contentUrl":"http://media.example.test/master.m3u8"}}"""
            )
            fail("Expected HTTPS validation failure")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("无效"))
        }
    }
}
