package com.sxsbsxs.filesniffing.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SniffedResourceDao {

    @Upsert
    suspend fun upsert(resource: SniffedResourceEntity)

    @Upsert
    suspend fun upsertAll(resources: List<SniffedResourceEntity>)

    @Query("SELECT * FROM sniffed_resources WHERE pageUrl = :pageUrl ORDER BY firstSeenAtMillis DESC")
    fun observeForPage(pageUrl: String): Flow<List<SniffedResourceEntity>>

    @Query("SELECT * FROM sniffed_resources ORDER BY firstSeenAtMillis DESC")
    fun observeAll(): Flow<List<SniffedResourceEntity>>

    @Query("DELETE FROM sniffed_resources WHERE pageUrl = :pageUrl")
    suspend fun clearPage(pageUrl: String)
}
