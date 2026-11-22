package com.sxsbsxs.filesniffing

import java.security.MessageDigest

/**
 * Stable private work key for one site-download selection.
 * It deliberately hashes the source and never writes raw URLs into cache paths or process IDs.
 */
internal object YtDlpSessionKey {
    fun create(
        sourceUrl: String,
        resourceKind: ResourceKind,
        formatSelector: String,
        fileName: String,
        destinationTreeUri: String?
    ): String {
        val canonical = listOf(
            sourceUrl,
            resourceKind.name,
            formatSelector,
            fileName,
            destinationTreeUri.orEmpty()
        ).joinToString(separator = "\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
