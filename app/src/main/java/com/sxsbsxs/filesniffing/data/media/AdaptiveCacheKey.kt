package com.sxsbsxs.filesniffing.data.media

import java.security.MessageDigest

/**
 * Produces a stable cache namespace for resuming one adaptive download.
 * Session cookies are only consumed as input to the digest and are never persisted.
 */
internal object AdaptiveCacheKey {
    fun create(
        sourceUrl: String,
        referer: String,
        fileName: String,
        destinationTree: String,
        maxVideoHeight: Int?,
        sessionCookies: String
    ): String {
        val material = listOf(
            sourceUrl,
            referer,
            fileName,
            destinationTree,
            maxVideoHeight?.toString() ?: "auto",
            sessionCookies
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
