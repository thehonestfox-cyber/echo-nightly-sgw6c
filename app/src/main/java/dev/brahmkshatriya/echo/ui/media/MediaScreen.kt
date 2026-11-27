package dev.brahmkshatriya.echo.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ErrorBox
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MediaItemChip
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.ShelfActions
import dev.brahmkshatriya.echo.ui.components.coverShape
import dev.brahmkshatriya.echo.ui.components.placeholder
import dev.brahmkshatriya.echo.ui.components.shelfItems
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.brahmkshatriya.echo.ui.components.activityViewModel
import org.koin.compose.viewmodel.koinViewModel

data class MediaNavigation(
    val openItem: (extensionId: String, item: EchoMediaItem) -> Unit,
    val openFeed: (title: String, feed: Feed<Shelf>) -> Unit,
    val openPlayer: () -> Unit,
)

@Composable
fun MediaScreen(
    extensionId: String,
    item: EchoMediaItem,
    navigation: MediaNavigation,
    viewModel: MediaViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = activityViewModel(),
) {
    LaunchedEffect(extensionId, item.id) { viewModel.load(extensionId, item) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val media = state.item ?: item

    LaunchedEffect(listState, state.hasMoreTracks) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { last ->
                val total = listState.layoutInfo.totalItemsCount
                last != null && total > 0 && last >= total - 3
            }
            .collect { viewModel.loadMoreTracks() }
    }

    val actions = ShelfActions(
        extensionId = extensionId,
        openItem = { navigation.openItem(extensionId, it) },
        playTracks = { tracks, index, context ->
            playerViewModel.playTracks(extensionId, tracks, index, context)
        },
        openCategory = { category ->
            category.feed?.let { navigation.openFeed(category.title, it) }
        },
        openMore = navigation.openFeed,
    )

    ListScreen(state = listState) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoverImage(
                    media.cover,
                    size = 72.dp,
                    shape = media.coverShape(),
                    placeholder = media.placeholder()
                )
                Text(
                    media.title,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = media.subtitleWithE
                if (!subtitle.isNullOrBlank()) Text(
                    subtitle,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (state.loading) item { LoadingBox() }
        val error = state.error
        if (error != null) item {
            ErrorBox(error, onRetry = { viewModel.load(extensionId, item) })
        }

        // Primary actions
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val playable = media !is Artist || state.tracks.isNotEmpty()
                if (playable) Button(
                    onClick = {
                        if (media is Track) playerViewModel.playTracks(extensionId, listOf(media))
                        else playerViewModel.play(extensionId, media, state.loaded != null)
                        navigation.openPlayer()
                    },
                    colors = ButtonDefaults.primaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) { Icon(painterResource(R.drawable.ic_play), stringResource(R.string.play)) }

                if (media !is Track && playable) Button(
                    onClick = {
                        playerViewModel.play(extensionId, media, state.loaded != null, shuffle = true)
                        navigation.openPlayer()
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(painterResource(R.drawable.ic_shuffle_40dp), stringResource(R.string.shuffle))
                }

                if (state.loaded?.showRadio == true || media.isRadioSupported) Button(
                    onClick = {
                        playerViewModel.radio(extensionId, media, state.loaded != null)
                        navigation.openPlayer()
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) { Icon(painterResource(R.drawable.ic_radio), stringResource(R.string.radio)) }
            }
        }

        // Secondary toggles (like / save / follow) only when the extension supports them
        val loaded = state.loaded
        if (loaded != null) {
            val toggles = listOfNotNull(
                loaded.isLiked?.let { liked ->
                    Triple(
                        if (liked) R.drawable.ic_heart_filled_40dp else R.drawable.ic_heart_outline_40dp,
                        if (liked) R.string.unlike else R.string.like
                    ) { viewModel.toggleLike() }
                },
                loaded.isSaved?.let { saved ->
                    Triple(
                        if (saved) R.drawable.ic_check else R.drawable.ic_library_music,
                        if (saved) R.string.remove_from_library else R.string.save_to_library
                    ) { viewModel.toggleSave() }
                },
                loaded.isFollowed?.let { followed ->
                    Triple(
                        if (followed) R.drawable.ic_check else R.drawable.ic_person,
                        if (followed) R.string.unfollow else R.string.follow
                    ) { viewModel.toggleFollow() }
                },
            )
            toggles.forEach { (icon, label, onClick) ->
                item {
                    Chip(
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(),
                        icon = { Icon(painterResource(icon), null) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }

        if (media is Track) {
            item {
                Chip(
                    onClick = { playerViewModel.addToNext(extensionId, media, state.loaded != null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    icon = { Icon(painterResource(R.drawable.ic_playlist_play), null) },
                    label = { Text(stringResource(R.string.play_next)) }
                )
            }
            item {
                Chip(
                    onClick = { playerViewModel.addToQueue(extensionId, media, state.loaded != null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    icon = { Icon(painterResource(R.drawable.ic_queue_music), null) },
                    label = { Text(stringResource(R.string.add_to_queue)) }
                )
            }
            val album = media.album
            if (album != null) {
                item { SectionHeader(stringResource(R.string.album)) }
                item { MediaItemChip(album, onClick = { navigation.openItem(extensionId, album) }) }
            }
            if (media.artists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.artists)) }
                media.artists.forEach { artist ->
                    item {
                        MediaItemChip(artist, onClick = { navigation.openItem(extensionId, artist) })
                    }
                }
            }
        } else if (media is EchoMediaItem.Lists && media.artists.isNotEmpty()) {
            media.artists.forEach { artist ->
                item { MediaItemChip(artist, onClick = { navigation.openItem(extensionId, artist) }) }
            }
        }

        if (state.tracks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.songs)) }
            val tracks = state.tracks
            items(tracks.size) { i ->
                MediaItemChip(
                    item = tracks[i],
                    onClick = {
                        playerViewModel.playTracks(extensionId, tracks, i, media)
                        navigation.openPlayer()
                    },
                    onMore = { navigation.openItem(extensionId, tracks[i]) }
                )
            }
            if (state.loadingTracks) item { LoadingBox() }
        } else if (state.loadingTracks) item { LoadingBox() }

        val description = media.description
        if (!description.isNullOrBlank()) {
            item { SectionHeader(stringResource(R.string.description)) }
            item {
                Text(
                    description,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        state.shelves.forEach { shelfItems(it, actions) }
    }
}
