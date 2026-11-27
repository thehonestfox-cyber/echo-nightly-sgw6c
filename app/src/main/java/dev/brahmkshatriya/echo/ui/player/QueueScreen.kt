package dev.brahmkshatriya.echo.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaItemUtils.track
import dev.brahmkshatriya.echo.playback.PlayerState
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MediaItemChip
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.activityViewModel

@Composable
fun QueueScreen(
    openTrack: (extensionId: String, track: Track) -> Unit,
    viewModel: PlayerViewModel = activityViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val radio by viewModel.radio.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    LaunchedEffect(queue.size) {
        if (currentIndex in queue.indices) runCatching {
            listState.scrollToItem(currentIndex + 1)
        }
    }

    ListScreen(state = listState) {
        item { SectionHeader(stringResource(R.string.queue)) }
        if (queue.isEmpty()) item {
            MessageBox(stringResource(R.string.queue_is_empty), icon = R.drawable.ic_queue_music)
        }
        items(queue.size) { index ->
            val mediaItem = queue[index]
            val track = runCatching { mediaItem.track }.getOrNull()
            if (track == null) return@items
            MediaItemChip(
                item = track,
                onClick = { viewModel.seekToIndex(index) },
                highlighted = index == currentIndex,
                colors = if (index == currentIndex) ChipDefaults.gradientBackgroundChipColors()
                else ChipDefaults.secondaryChipColors(),
                onMore = {
                    val extId = runCatching { mediaItem.extensionId }.getOrNull()
                    if (extId != null) openTrack(extId, track)
                }
            )
        }
        if (radio is PlayerState.Radio.Loading) item { LoadingBox(text = stringResource(R.string.loading_radio)) }
        if (radio is PlayerState.Radio.Loaded) item {
            MessageBox(stringResource(R.string.radio_autoplay_on), icon = R.drawable.ic_radio)
        }
        if (queue.isNotEmpty()) item {
            Chip(
                onClick = { viewModel.clearQueue() },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_delete), null) },
                label = { Text(stringResource(R.string.clear_queue), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
