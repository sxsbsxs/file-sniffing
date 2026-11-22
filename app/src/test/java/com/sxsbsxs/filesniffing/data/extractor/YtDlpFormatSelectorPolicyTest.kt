package com.sxsbsxs.filesniffing.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpFormatSelectorPolicyTest {
    @Test
    fun allowsRealFormatIdsPairsAndBuiltInFallbacks() {
        assertTrue(YtDlpFormatSelectorPolicy.isSafe("137"))
        assertTrue(YtDlpFormatSelectorPolicy.isSafe("137+140"))
        assertTrue(YtDlpFormatSelectorPolicy.isSafe("bestaudio/best"))
        assertTrue(YtDlpFormatSelectorPolicy.isSafe("bv*+ba/b"))
    }

    @Test
    fun rejectsSelectorExpressionsNotProducedByTheApp() {
        assertFalse(YtDlpFormatSelectorPolicy.isSafe("137/best"))
        assertFalse(YtDlpFormatSelectorPolicy.isSafe("137[height>720]"))
        assertFalse(YtDlpFormatSelectorPolicy.isSafe("137+140+251"))
        assertFalse(YtDlpFormatSelectorPolicy.isSafe("--exec=touch"))
        assertFalse(YtDlpFormatSelectorPolicy.isSafe(""))
        assertFalse(YtDlpFormatSelectorPolicy.isSafe("a".repeat(161)))
    }
}
