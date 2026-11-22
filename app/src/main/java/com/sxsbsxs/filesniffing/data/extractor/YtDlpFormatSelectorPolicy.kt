package com.sxsbsxs.filesniffing.data.extractor

/** Allows only format IDs returned by yt-dlp and the two app-owned fallback selectors. */
internal object YtDlpFormatSelectorPolicy {
    private val formatId = Regex("^[A-Za-z0-9_.-]{1,96}$")
    private val pair = Regex("^[A-Za-z0-9_.-]{1,96}\+[A-Za-z0-9_.-]{1,96}$")
    private val fallbackSelectors = setOf("bv*+ba/b", "bestaudio/best")

    fun isSafe(selector: String): Boolean =
        selector.length <= 160 &&
            (selector in fallbackSelectors ||
                formatId.matches(selector) ||
                pair.matches(selector))
}
