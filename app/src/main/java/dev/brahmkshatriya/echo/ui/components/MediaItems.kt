package dev.brahmkshatriya.echo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track

/** Everything a list of shelves can ask the host screen to do. */
data class ShelfActions(
    val extensionId: String,
    val openItem: (EchoMediaItem) -> Unit,
    val playTracks: (tracks: List<Track>, index: Int, context: EchoMediaItem?) -> Unit,
    val openCategory: (Shelf.Category) -> Unit,
    val openMore: (title: String, feed: Feed<Shelf>) -> Unit,
)

@Composable
fun MediaItemChip(
    item: EchoMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    colors: ChipColors = ChipDefaults.secondaryChipColors(),
    highlighted: Boolean = false,
) {
    val subtitle = item.subtitleWithE
    Chip(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        icon = {
            CoverImage(
                item.cover,
                size = ChipDefaults.LargeIconSize,
                shape = item.coverShape(),
                placeholder = item.placeholder()
            )
        },
        label = {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlighted) MaterialTheme.colors.primary else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            if (onMore != null) Icon(
                painterResource(R.drawable.ic_more_horiz),
                contentDescription = stringResource(R.string.more),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                tint = MaterialTheme.colors.onSurfaceVariant
            )
        },
        secondaryLabel = subtitle?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    )
}

@Composable
fun CategoryChip(category: Shelf.Category, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.gradientBackgroundChipColors(),
        icon = if (category.image != null) {
            { CoverImage(category.image, size = ChipDefaults.IconSize) }
        } else null,
        label = { Text(category.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = category.subtitle?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    )
}

@Composable
fun MoreChip(text: String = stringResource(R.string.show_all), onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CompactChip(
            onClick = onClick,
            colors = ChipDefaults.childChipColors(),
            label = { Text(text, color = MaterialTheme.colors.primary) }
        )
    }
}

const val SHELF_PREVIEW_COUNT = 5

/**
 * Emits the items of a [Shelf] into a scaling list. Lists are truncated to [limit] entries
 * followed by a "show all" chip; pass `Int.MAX_VALUE` to render the whole list.
 */
fun ScalingLazyListScope.shelfItems(
    shelf: Shelf,
    actions: ShelfActions,
    limit: Int = SHELF_PREVIEW_COUNT,
    showHeader: Boolean = true,
) {
    when (shelf) {
        is Shelf.Item -> item { MediaChipFor(shelf.media, actions, null, 0) }

        is Shelf.Category -> item { CategoryChip(shelf) { actions.openCategory(shelf) } }

        is Shelf.Lists<*> -> {
            if (shelf.list.isEmpty()) return
            if (showHeader) item { SectionHeader(shelf.title) }
            when (shelf) {
                is Shelf.Lists.Items -> {
                    val preview = shelf.list.take(limit)
                    items(preview.size) { i -> MediaChipFor(preview[i], actions, null, 0) }
                }

                is Shelf.Lists.Tracks -> {
                    val preview = shelf.list.take(limit)
                    items(preview.size) { i -> MediaChipFor(preview[i], actions, shelf.list, i) }
                }

                is Shelf.Lists.Categories -> {
                    val preview = shelf.list.take(limit)
                    items(preview.size) { i ->
                        CategoryChip(preview[i]) { actions.openCategory(preview[i]) }
                    }
                }
            }
            val more = shelf.more
            if (more != null || shelf.list.size > limit) item {
                MoreChip {
                    actions.openMore(shelf.title, more ?: listOf<Shelf>(shelf).toFeed())
                }
            }
        }
    }
}

@Composable
private fun MediaChipFor(
    item: EchoMediaItem,
    actions: ShelfActions,
    trackList: List<Track>?,
    index: Int,
) {
    if (item is Track) {
        MediaItemChip(
            item = item,
            onClick = { actions.playTracks(trackList ?: listOf(item), index, null) },
            onMore = { actions.openItem(item) }
        )
    } else {
        MediaItemChip(item = item, onClick = { actions.openItem(item) })
    }
}
