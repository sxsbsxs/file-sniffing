package com.sxsbsxs.filesniffing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NiconicoUrlParserTest {
    @Test fun parsesCanonicalWatchLinks() {
        assertEquals("sm12345678", NiconicoUrlParser.extractVideoId(
            "https://www.nicovideo.jp/watch/sm12345678?ref=share"
        ))
        assertEquals("so9876543", NiconicoUrlParser.extractVideoId(
            "https://m.nicovideo.jp/watch/so9876543/"
        ))
    }

    @Test fun parsesOfficialShortLinks() {
        assertEquals("sm12345678", NiconicoUrlParser.extractVideoId("https://nico.ms/sm12345678"))
    }

    @Test fun rejectsSpoofedHostsAndLiveUrls() {
        assertNull(NiconicoUrlParser.extractVideoId("https://nicovideo.jp.example.com/watch/sm12345678"))
        assertNull(NiconicoUrlParser.extractVideoId("https://live.nicovideo.jp/watch/lv12345678"))
        assertNull(NiconicoUrlParser.extractVideoId("javascript://nicovideo.jp/watch/sm12345678"))
    }

    @Test fun canonicalizesVideoId() {
        assertEquals(
            "https://www.nicovideo.jp/watch/sm12345678",
            NiconicoUrlParser.canonicalWatchUrl("SM12345678")
        )
    }
}
