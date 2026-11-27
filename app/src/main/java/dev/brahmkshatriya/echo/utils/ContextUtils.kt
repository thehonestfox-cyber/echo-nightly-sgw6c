package dev.brahmkshatriya.echo.utils

import android.content.Context
import android.os.Build.SUPPORTED_ABIS
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import dev.brahmkshatriya.echo.BuildConfig
import java.io.File

object ContextUtils {
    fun appVersion() = BuildConfig.VERSION_NAME + " " + BuildConfig.BUILD_TYPE
    fun getArch(): String {
        SUPPORTED_ABIS.firstOrNull()?.let { return it }
        return System.getProperty("os.arch")
            ?: System.getProperty("os.product.cpu.abi")
            ?: "Unknown"
    }

    fun <T> Context.listenFuture(future: ListenableFuture<T>, block: (Result<T>) -> Unit) {
        future.addListener({
            val result = runCatching { future.get() }
            block(result)
        }, ContextCompat.getMainExecutor(this))
    }

    const val SETTINGS_NAME = "settings"
    fun Context.getSettings() = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)!!

    private fun Context.getTempDir() = cacheDir.resolve("apks").apply { mkdirs() }
    fun Context.getTempFile(ext: String = "apk"): File =
        File.createTempFile("temp", ".$ext", getTempDir())

    fun Context.cleanupTempApks() {
        getTempDir().deleteRecursively()
    }
}
