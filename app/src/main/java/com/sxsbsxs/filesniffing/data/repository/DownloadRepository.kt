package com.sxsbsxs.filesniffing.data.repository

import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadRecordEntity>>
    suspend fun findById(id: String): DownloadRecordEntity?
    suspend fun save(record: DownloadRecordEntity)
    suspend fun delete(id: String)
}
