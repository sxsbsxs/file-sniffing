package com.sxsbsxs.filesniffing.di

import com.sxsbsxs.filesniffing.data.repository.DownloadRepository
import com.sxsbsxs.filesniffing.data.repository.DownloadRepositoryImpl
import com.sxsbsxs.filesniffing.data.repository.SniffedResourceRepository
import com.sxsbsxs.filesniffing.data.repository.SniffedResourceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        implementation: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSniffedResourceRepository(
        implementation: SniffedResourceRepositoryImpl
    ): SniffedResourceRepository
}
