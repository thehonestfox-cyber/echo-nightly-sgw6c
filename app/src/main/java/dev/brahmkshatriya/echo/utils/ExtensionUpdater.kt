package dev.brahmkshatriya.echo.utils

import android.content.Context
import android.os.Build
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.extensions.exceptions.InvalidExtensionListException
import dev.brahmkshatriya.echo.utils.ContextUtils.getTempFile
import dev.brahmkshatriya.echo.utils.Serializer.toData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads extension APKs (and their updates) from GitHub releases, exactly like the
 * phone app does. The app itself is updated through the GitHub Actions nightly workflow.
 */
object ExtensionUpdater {

    val client = OkHttpClient()

    @Serializable
    data class ExtensionAssetResponse(
        val id: String,
        val name: String,
        val subtitle: String? = null,
        val iconUrl: String? = null,
        val updateUrl: String,
    )

    /** Resolves an extension-list link (full URL or a short v.gd code) to its entries. */
    suspend fun getExtensionList(link: String): List<ExtensionAssetResponse> {
        val actualLink = when {
            link.startsWith("http://") || link.startsWith("https://") -> link
            else -> "https://v.gd/$link"
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .addHeader("Cookie", "preview=1")
                    .url(actualLink).build()
                client.newCall(request).await().use { it.body.string() }
                    .toData<List<ExtensionAssetResponse>>().getOrThrow()
            }
        }.getOrElse {
            throw InvalidExtensionListException(actualLink, it)
        }
    }

    private val githubRegex = Regex("https://api\\.github\\.com/repos/([^/]*)/([^/]*)/")

    suspend fun getGithubUpdateUrl(
        currentVersion: String,
        updateUrl: String,
    ): String? {
        val (user, repo) = githubRegex.find(updateUrl)?.destructured
            ?: throw Exception("Invalid Github URL")
        val url = "https://api.github.com/repos/$user/$repo/releases/latest"
        val request = Request.Builder().url(url).build()
        val res = runCatching {
            client.newCall(request).await().use {
                it.body.string().toData<GithubReleaseResponse>()
            }.getOrThrow()
        }.getOrElse {
            throw Exception("Failed to fetch latest release", it)
        }
        return if (res.tagName != currentVersion) {
            res.assets.sortedByDescending {
                it.name.contains(Build.SUPPORTED_ABIS.first())
            }.firstOrNull {
                it.name.endsWith("apk")
            }?.browserDownloadUrl ?: throw Exception("No EApk assets found")
        } else null
    }

    @Serializable
    data class GithubReleaseResponse(
        @SerialName("tag_name")
        val tagName: String,
        @SerialName("created_at")
        val createdAt: String,
        val assets: List<Asset>,
    ) {
        @Serializable
        data class Asset(
            val name: String,
            @SerialName("browser_download_url")
            val browserDownloadUrl: String,
        )
    }

    suspend fun downloadFile(context: Context, url: String) = runIOCatching {
        val request = Request.Builder().url(url).build()
        val res = client.newCall(request).await().body.byteStream()
        val file = context.getTempFile()
        res.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file
    }

    suspend fun getUpdateFileUrl(
        currentVersion: String,
        updateUrl: String,
    ) = runIOCatching {
        if (updateUrl.isEmpty()) return@runIOCatching null
        if (updateUrl.startsWith("https://api.github.com/repos/")) {
            getGithubUpdateUrl(currentVersion, updateUrl)
        } else {
            throw Exception("Unsupported update url")
        }
    }

    private suspend fun <T> runIOCatching(
        block: suspend () -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching { runCatching { block() }.getOrElse { throw UpdateException(it) } }
    }

    class UpdateException(override val cause: Throwable) : Exception(cause) {
        override val message: String
            get() = "Update failed: ${cause.message}"
    }

    fun isUpdateCheckDue(context: Context): Boolean {
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_check", 0)
        val now = System.currentTimeMillis()
        if (now - last < 1000L * 60 * 60 * 6) return false
        prefs.edit().putLong("last_check", now).apply()
        return true
    }

    @Suppress("unused")
    fun File.isApk() = extension == "apk" || extension == "eapk"
}
