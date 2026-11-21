package com.sxsbsxs.filesniffing.data.local

import androidx.room.Entity

@Entity(
    tableName = "sniffed_resources",
    primaryKeys = ["pageUrl", "resourceUrl"]
)
data class SniffedResourceEntity(
    val pageUrl: String,
    val resourceUrl: String,
    val resourceKind: String,
    val fileName: String,
    val mimeType: String?,
    val pageTitle: String,
    val firstSeenAtMillis: Long
)
