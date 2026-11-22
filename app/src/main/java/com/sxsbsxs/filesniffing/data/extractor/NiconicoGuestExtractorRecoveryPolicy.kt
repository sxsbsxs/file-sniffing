package com.sxsbsxs.filesniffing.data.extractor

import com.sxsbsxs.filesniffing.NiconicoUrlParser

/**
 * Detects the known legacy yt-dlp guest-extractor failure without treating all
 * Niconico errors as reasons to download or request credentials.
 */
internal object NiconicoGuestExtractorRecoveryPolicy {
    private const val LEGACY_GUEST_ERROR = "This video is only available for registered users"

    fun shouldRefreshAndRetry(url: String, errorMessage: String?): Boolean {
        if (NiconicoUrlParser.extractVideoId(url) == null) return false
        return errorMessage?.contains(LEGACY_GUEST_ERROR, ignoreCase = true) == true
    }
}
