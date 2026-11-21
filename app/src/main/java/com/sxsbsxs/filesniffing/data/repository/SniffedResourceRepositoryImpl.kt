package com.sxsbsxs.filesniffing.data.repository

import com.sxsbsxs.filesniffing.data.local.SniffedResourceDao
import com.sxsbsxs.filesniffing.data.local.SniffedResourceEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SniffedResourceRepositoryImpl @Inject constructor(
    private val dao: SniffedResourceDao
) : SniffedResourceRepository {
    override fun observeForPage(pageUrl: String): Flow<List<SniffedResourceEntity>> =
        dao.observeForPage(pageUrl)

    override fun observeAll(): Flow<List<SniffedResourceEntity>> = dao.observeAll()

    override suspend fun save(resource: SniffedResourceEntity) = dao.upsert(resource)

    override suspend fun clearPage(pageUrl: String) = dao.clearPage(pageUrl)
}
