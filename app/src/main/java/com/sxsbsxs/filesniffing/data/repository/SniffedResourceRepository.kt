package com.sxsbsxs.filesniffing.data.repository

import com.sxsbsxs.filesniffing.data.local.SniffedResourceEntity
import kotlinx.coroutines.flow.Flow

interface SniffedResourceRepository {
    fun observeForPage(pageUrl: String): Flow<List<SniffedResourceEntity>>
    fun observeAll(): Flow<List<SniffedResourceEntity>>
    suspend fun save(resource: SniffedResourceEntity)
    suspend fun clearPage(pageUrl: String)
}
