package com.sxsbsxs.filesniffing.data.repository

import com.sxsbsxs.filesniffing.data.local.DownloadRecordDao
import com.sxsbsxs.filesniffing.data.local.DownloadRecordEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val dao: DownloadRecordDao
) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecordEntity>> = dao.observeAll()

    override suspend fun findById(id: String): DownloadRecordEntity? = dao.findById(id)

    override suspend fun save(record: DownloadRecordEntity) = dao.upsert(record)

    override suspend fun delete(id: String) = dao.deleteById(id)
}
