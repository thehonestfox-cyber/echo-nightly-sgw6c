package dev.brahmkshatriya.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import coil3.compose.AsyncImage
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.utils.image.ImageUtils.createRequest

@Composable
fun CoverImage(
    cover: ImageHolder?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholder: Int = R.drawable.ic_music,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(placeholder),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxSize()
                .padding(size / 5)
        )
        if (cover != null) {
            val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
            AsyncImage(
                model = cover.createRequest(context, sizePx),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FullBleedCover(cover: ImageHolder?, modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    val context = LocalContext.current
    if (cover == null) return
    AsyncImage(
        model = cover.createRequest(context, 320),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

fun EchoMediaItem.placeholder() = when (this) {
    is Artist -> R.drawable.ic_artist
    is Album -> R.drawable.ic_album
    is Playlist -> R.drawable.ic_playlist
    is Radio -> R.drawable.ic_radio
    is Track -> R.drawable.ic_music
}

fun EchoMediaItem.coverShape(): Shape =
    if (this is Artist) CircleShape else RoundedCornerShape(8.dp)
