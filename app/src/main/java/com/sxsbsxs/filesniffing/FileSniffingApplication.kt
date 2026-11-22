package com.sxsbsxs.filesniffing

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.liulishuo.okdownload.OkDownload
import com.sxsbsxs.filesniffing.data.extractor.YtDlpRuntime
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FileSniffingApplication : Application(), Configuration.Provider {

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Force the configured OkDownload singleton to be created at application startup,
        // before any screen, WorkManager Worker, or DownloadTask can trigger OkDownload.with().
        Log.i("FileSniffing", "OkDownload initialized: ${okDownloadInstance.javaClass.simpleName}")
        runtimeScope.launch {
            runCatching {
                // Update the embedded yt-dlp program before the first user parse when possible.
                ytDlpRuntime.prepare()
            }.onFailure { error ->
                Log.e("FileSniffing", "yt-dlp initialization failed; the bundled runtime remains available", error)
            }
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var ytDlpRuntime: YtDlpRuntime

    @Inject
    lateinit var okDownloadInstance: OkDownload

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DiagnosticWorkerFactory(workerFactory))
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
