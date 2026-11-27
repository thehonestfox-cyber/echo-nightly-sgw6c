package dev.brahmkshatriya.echo.utils.image

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import coil3.asDrawable
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.size.Size
import dev.brahmkshatriya.echo.common.models.ImageHolder

/**
 * Bridges Echo's [ImageHolder] model with Coil 3. Used both by Compose (`CoverImage`)
 * and by the media session bitmap loader.
 */
object ImageUtils {

    private val ImageHolder.diskId
        get() = when (this) {
            is ImageHolder.NetworkRequestImageHolder -> request.toString().hashCode().toString()
            else -> null
        }

    fun ImageHolder.applyTo(builder: ImageRequest.Builder): ImageRequest.Builder = builder.apply {
        diskCacheKey(diskId)
        when (this@applyTo) {
            is ImageHolder.ResourceUriImageHolder -> data(uri)
            is ImageHolder.NetworkRequestImageHolder -> {
                val headerBuilder = NetworkHeaders.Builder()
                request.headers.forEach { (key, value) -> headerBuilder[key] = value }
                httpHeaders(headerBuilder.build())
                data(request.url)
            }

            is ImageHolder.ResourceIdImageHolder -> data(resId)
            is ImageHolder.HexColorImageHolder -> data(hex.toColorInt().toDrawable())
        }
    }

    fun ImageHolder?.createRequest(context: Context, sizePx: Int? = null): ImageRequest {
        val builder = ImageRequest.Builder(context)
        if (this != null) applyTo(builder)
        if (sizePx != null) builder.size(Size(sizePx, sizePx))
        return builder.build()
    }

    suspend fun ImageHolder?.loadDrawable(context: Context, sizePx: Int? = null): Drawable? {
        this ?: return null
        return runCatching {
            context.imageLoader.execute(createRequest(context, sizePx))
                .image?.asDrawable(context.resources)
        }.getOrElse {
            it.printStackTrace()
            null
        }
    }
}
