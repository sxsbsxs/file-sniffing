package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NiconicoCookiePolicyTest {

    @Test
    fun extractsOnlyTheUserSessionCookie() {
        val header = "other=value; user_session=session-token-123; tracking=abc"
        assertEquals("session-token-123", NiconicoCookiePolicy.userSessionValue(header))
    }

    @Test
    fun producesNetscapeCookieFileScopedToNiconico() {
        val content = NiconicoCookiePolicy.toNetscapeCookieFile(
            "user_session=session-token-123; unrelated=secret"
        )
        assertTrue(content.orEmpty().startsWith("# Netscape HTTP Cookie File\n"))
        assertTrue(content.orEmpty().contains(".nicovideo.jp\\tTRUE\\t/\\tTRUE\\t0\\tuser_session\\tsession-token-123"))
        assertFalse(content.orEmpty().contains("unrelated"))
    }

    @Test
    fun missingOrDeletedSessionIsNotAuthenticated() {
        assertNull(NiconicoCookiePolicy.userSessionValue("other=value"))
        assertNull(NiconicoCookiePolicy.userSessionValue("user_session=deleted"))
        assertFalse(NiconicoCookiePolicy.hasUserSession(""))
    }

    @Test
    fun rejectsControlCharactersInCookieValue() {
        assertNull(NiconicoCookiePolicy.userSessionValue("user_session=bad\\nvalue"))
        assertNull(NiconicoCookiePolicy.userSessionValue("user_session=bad\\tvalue"))
    }
}
