package dev.brahmkshatriya.echo.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import dev.brahmkshatriya.echo.BuildConfig
import dev.brahmkshatriya.echo.ui.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash reporter. There is no easy way to read logcat on a watch, so every
 * uncaught exception is written to a file (readable with `adb pull`) and shown on screen by
 * [CrashActivity], which runs in its own process and therefore survives the crash.
 */
object CrashHandler {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val report = buildReport(app, thread, throwable)
                runCatching { save(app, report) }
                app.startActivity(CrashActivity.intent(app, report))
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else {
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(10)
            }
        }
    }

    fun lastCrashFile(context: Context): File = File(File(context.filesDir, DIR), FILE)

    fun lastCrash(context: Context): String? =
        lastCrashFile(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        lastCrashFile(context).delete()
        context.getExternalFilesDir(null)?.let { File(File(it, DIR), FILE).delete() }
    }

    private fun save(context: Context, report: String) {
        val targets = listOfNotNull(
            context.filesDir,
            runCatching { context.getExternalFilesDir(null) }.getOrNull()
        )
        targets.forEach { base ->
            runCatching {
                val dir = File(base, DIR).apply { mkdirs() }
                File(dir, FILE).writeText(report)
            }
        }
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            append("Echo Wear crash report\n")
            append("Date: ").append(date).append('\n')
            append("App: ").append(context.packageName).append(' ')
                .append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.BUILD_TYPE)
                .append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread: ").append(thread.name).append("\n\n")
            append(trace)
        }
    }
}
