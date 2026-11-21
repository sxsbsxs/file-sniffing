package com.sxsbsxs.filesniffing.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_records")
data class DownloadRecordEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val fileName: String,
    val resourceKind: String,
    val mimeType: String?,
    val state: String,
    val progress: Int,
    val detail: String,
    val destinationUri: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "''") val referer: String = "",
    val destinationTreeUri: String? = null
)
