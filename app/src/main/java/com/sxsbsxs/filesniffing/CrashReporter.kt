package com.sxsbsxs.filesniffing

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

/**
 * Stores the last uncaught crash in app-private storage.
 * Nothing is uploaded; the user can inspect or copy the report after reopening the app.
 */
internal object CrashReporter {
    private const val REPORT_FILE = "last-crash-report.txt"

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                val safeStack = writer.toString()
                    .replace(Regex("""(?i)(https?://[^\s?#]+)[?#][^\s]+""")) {
                        "${it.groupValues[1]}?[redacted]"
                    }
                    .replace(Regex("""(?im)^(cookie|authorization)\s*[:=].*$""")) {
                        "${it.groupValues[1]}: [redacted]"
                    }
                val report = buildString {
                    appendLine("File Sniffing crash report")
                    appendLine("time: ${Date()}")
                    appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("thread: ${thread.name}")
                    appendLine()
                    append(safeStack.takeLast(24000))
                }
                File(appContext.filesDir, REPORT_FILE).writeText(report)
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, REPORT_FILE)
        return runCatching { file.takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, REPORT_FILE).delete() }
    }
}
