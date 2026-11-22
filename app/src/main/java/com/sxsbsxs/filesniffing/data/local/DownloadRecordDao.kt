package com.sxsbsxs.filesniffing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadRecordDao {

    @Upsert
    suspend fun upsert(record: DownloadRecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<DownloadRecordEntity>)

    @Transaction
    suspend fun replaceForRetry(
        previous: DownloadRecordEntity,
        retry: DownloadRecordEntity
    ) {
        upsertAll(listOf(previous, retry))
    }

    @Query("SELECT * FROM download_records ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DownloadRecordEntity?

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_records")
    suspend fun clear()
}
