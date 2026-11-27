package dev.brahmkshatriya.echo.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.dialog.Dialog
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.playback.MediaItemUtils.extensionId
import dev.brahmkshatriya.echo.playback.MediaItemUtils.track
import dev.brahmkshatriya.echo.playback.PlayerState
import dev.brahmkshatriya.echo.ui.components.FullBleedCover
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.toUserMessage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun PlayerScreen(
    openQueue: () -> Unit,
    openTrack: (extensionId: String, track: Track) -> Unit,
    viewModel: PlayerViewModel = koinActivityViewModel(),
) {
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val mediaItem by viewModel.currentItem.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val radio by viewModel.radio.collectAsStateWithLifecycle()

    var lastVolumeChange by remember { mutableLongStateOf(0L) }
    var showVolume by remember { mutableStateOf(false) }
    LaunchedEffect(lastVolumeChange) {
        if (lastVolumeChange == 0L) return@LaunchedEffect
        showVolume = true
        delay(1500)
        showVolume = false
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    var showSeek by remember { mutableStateOf(false) }

    val track = remember(mediaItem) { runCatching { mediaItem?.track }.getOrNull() }
    val extId = remember(mediaItem) { runCatching { mediaItem?.extensionId }.getOrNull() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val delta = event.verticalScrollPixels
                if (delta != 0f) {
                    viewModel.changeVolume(if (delta > 0) 1 else -1)
                    lastVolumeChange = System.currentTimeMillis()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        timeText = { TimeText() },
        positionIndicator = if (showVolume) ({
            PositionIndicator(
                value = { volume.toFloat() },
                range = 0f..viewModel.maxVolume.toFloat(),
                color = MaterialTheme.colors.primary
            )
        }) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            FullBleedCover(track?.cover, alpha = 0.35f)

            // Progress ring along the bezel
            val duration = progress.duration
            val fraction = if (duration > 0) (progress.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            CircularProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                startAngle = 295f,
                endAngle = 245f,
                strokeWidth = 4.dp,
                indicatorColor = MaterialTheme.colors.primary,
                trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
            )

            if (mediaItem == null) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MessageBox(stringResource(R.string.nothing_playing), icon = R.drawable.ic_music)
                    Chip(
                        onClick = { viewModel.resume() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(),
                        enabled = controller != null,
                        label = { Text(stringResource(R.string.resume_last_queue)) },
                        icon = { Icon(painterResource(R.drawable.ic_play), null) }
                    )
                }
                return@Box
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clickable(enabled = track != null && extId != null) {
                            if (track != null && extId != null) openTrack(extId, track)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        mediaItem?.mediaMetadata?.title?.toString() ?: "",
                        style = MaterialTheme.typography.button,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                    val artist = mediaItem?.mediaMetadata?.artist?.toString()
                    if (!artist.isNullOrBlank()) Text(
                        artist,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.previous() },
                        colors = ButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_skip_previous_40dp),
                            stringResource(R.string.previous),
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { viewModel.playPause() },
                            colors = ButtonDefaults.primaryButtonColors(),
                            modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                        ) {
                            Icon(
                                painterResource(
                                    if (isPlaying) R.drawable.ic_pause_48dp else R.drawable.ic_play_48dp
                                ),
                                stringResource(if (isPlaying) R.string.pause else R.string.play),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        if (isBuffering) CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.LargeButtonSize + 6.dp),
                            indicatorColor = MaterialTheme.colors.primary,
                            trackColor = Color.Transparent,
                            strokeWidth = 2.dp
                        )
                    }
                    Button(
                        onClick = { viewModel.next() },
                        colors = ButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_skip_next_40dp),
                            stringResource(R.string.next),
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${progress.position.toTimeString()} / ${duration.toTimeString()}",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.clickable(enabled = duration > 0) { showSeek = true }
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(
                            onClick = { viewModel.like(!isLiked) },
                            colors = ButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                painterResource(
                                    if (isLiked) R.drawable.ic_heart_filled_40dp
                                    else R.drawable.ic_heart_outline_40dp
                                ),
                                stringResource(if (isLiked) R.string.unlike else R.string.like),
                                tint = if (isLiked) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface
                            )
                        }
                        CompactButton(
                            onClick = { viewModel.toggleShuffle() },
                            colors = ButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                painterResource(
                                    if (shuffle) R.drawable.ic_shuffle_on_40dp
                                    else R.drawable.ic_shuffle_40dp
                                ),
                                stringResource(R.string.shuffle),
                                tint = if (shuffle) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface
                            )
                        }
                        CompactButton(
                            onClick = { viewModel.cycleRepeat() },
                            colors = ButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                painterResource(
                                    when (repeat) {
                                        Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_40dp
                                        Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_40dp
                                        else -> R.drawable.ic_repeat_off_40dp
                                    }
                                ),
                                stringResource(R.string.repeat),
                                tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface
                            )
                        }
                        CompactButton(
                            onClick = openQueue,
                            colors = ButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_queue_music),
                                stringResource(R.string.queue),
                                tint = if (radio !is PlayerState.Radio.Empty) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface
                            )
                        }
                    }
                }
            }

            val err = error
            if (err != null) Text(
                err.toUserMessage(),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }
    }

    SeekDialog(
        show = showSeek,
        position = progress.position,
        duration = progress.duration,
        onDismiss = { showSeek = false },
        onSeek = { viewModel.seekTo(it) }
    )
}

@Composable
private fun SeekDialog(
    show: Boolean,
    position: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Dialog(showDialog = show, onDismissRequest = onDismiss) {
        var value by remember(show) { mutableStateOf(position.toFloat()) }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${value.toLong().toTimeString()} / ${duration.toTimeString()}",
                style = MaterialTheme.typography.title3,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val step = (duration / 20f).coerceAtLeast(1000f)
            InlineSlider(
                value = value,
                onValueChange = {
                    value = it
                    onSeek(it.toLong())
                },
                steps = 19,
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                segmented = false,
                decreaseIcon = {
                    Icon(painterResource(R.drawable.ic_skip_previous_40dp), "-${(step / 1000).toInt()}s")
                },
                increaseIcon = {
                    Icon(painterResource(R.drawable.ic_skip_next_40dp), "+${(step / 1000).toInt()}s")
                },
            )
            Chip(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.done)) }
            )
        }
    }
}

fun Long.toTimeString(): String {
    if (this <= 0) return "0:00"
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
