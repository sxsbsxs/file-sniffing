package com.sxsbsxs.filesniffing.di

import android.content.Context
import android.util.Log
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.dispatcher.DownloadDispatcher
import com.liulishuo.okdownload.core.connection.DownloadOkHttp3Connection
import com.sxsbsxs.filesniffing.data.network.SizeAwareDownloadStrategy
import com.sxsbsxs.filesniffing.data.network.DownloadSpeedLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(downloadSpeedLimiter: DownloadSpeedLimiter): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(downloadSpeedLimiter)
            .build()

    @Provides
    @Singleton
    fun provideOkDownload(
        @ApplicationContext context: Context,
        client: OkHttpClient
    ): OkDownload = synchronized(OkDownload::class.java) {
        // Favor a small number of active files so one large transfer can use
        // its range connections instead of having many tasks fragment bandwidth.
        DownloadDispatcher.setMaxParallelRunningCount(3)
        val connectionFactory = DownloadOkHttp3Connection.Factory()
            .setBuilder(client.newBuilder())
        val candidate = OkDownload.Builder(context)
            .connectionFactory(connectionFactory)
            .downloadStrategy(SizeAwareDownloadStrategy())
            .build()

        try {
            // OkDownload's singleton is process-global. Install our configured instance
            // before any DownloadTask is constructed (its constructor calls OkDownload.with()).
            OkDownload.setSingletonInstance(candidate)
            candidate
        } catch (alreadyInitialized: IllegalArgumentException) {
            // Another component may have called OkDownload.with() first. Reuse that global
            // instance instead of crashing every download task with "OkDownload must be null".
            val existing = OkDownload.with()
            Log.w(
                TAG,
                "OkDownload was already initialized; reusing the process singleton. " +
                    "The first initializer's connection strategy remains active.",
                alreadyInitialized
            )
            existing
        }
    }

    private const val TAG = "FileSniffingOkDownload"
}
