package com.sxsbsxs.filesniffing.di

import android.content.Context
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.dispatcher.DownloadDispatcher
import com.liulishuo.okdownload.core.connection.DownloadOkHttp3Connection
import com.sxsbsxs.filesniffing.data.network.SizeAwareDownloadStrategy
import com.sxsbsxs.filesniffing.data.network.WebViewCookieInterceptor
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
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideOkDownload(
        @ApplicationContext context: Context,
        client: OkHttpClient
    ): OkDownload {
        // Favor a small number of active files so one large transfer can use
        // its range connections instead of having many tasks fragment bandwidth.
        DownloadDispatcher.setMaxParallelRunningCount(3)
        val connectionFactory = DownloadOkHttp3Connection.Factory()
            .setBuilder(client.newBuilder())
        val instance = OkDownload.Builder(context)
            .connectionFactory(connectionFactory)
            .downloadStrategy(SizeAwareDownloadStrategy())
            .build()
        OkDownload.setSingletonInstance(instance)
        return instance
    }
}
