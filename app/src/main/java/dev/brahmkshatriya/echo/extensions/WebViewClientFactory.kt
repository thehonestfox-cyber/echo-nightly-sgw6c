package dev.brahmkshatriya.echo.extensions

import android.content.Context
import dev.brahmkshatriya.echo.common.helpers.WebViewClient
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Metadata

/**
 * Wear OS does not ship a WebView implementation, so extensions that need one
 * (`WebViewClientProvider`) get a client that fails fast with an explicit error
 * instead of crashing the process. Login flows that need a browser must therefore be
 * completed with `LoginClient.CustomInput` (supported) or on a phone.
 */
class WebViewClientFactory(
    @Suppress("unused") private val context: Context,
) {
    fun createFor(metadata: Metadata) = object : WebViewClient {
        override suspend fun await(
            showWebView: Boolean,
            reason: String,
            request: WebViewRequest<String>,
        ): Result<String?> = Result.failure(
            WebViewUnavailableException(metadata, reason)
        )
    }

    class WebViewUnavailableException(
        val extension: Metadata,
        val reason: String,
    ) : UnsupportedOperationException(
        "WebView is not available on Wear OS (${extension.name} requested it: $reason)"
    )
}
