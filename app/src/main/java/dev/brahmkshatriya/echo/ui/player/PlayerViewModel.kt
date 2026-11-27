package dev.brahmkshatriya.echo.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.playback.MediaItemUtils.isLiked
import dev.brahmkshatriya.echo.playback.PlayerCommands
import dev.brahmkshatriya.echo.playback.PlayerService
import dev.brahmkshatriya.echo.playback.PlayerState
import dev.brahmkshatriya.echo.utils.Serializer.putSerialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the "Now playing" UI. It talks to [PlayerService] through a
 * [MediaController] and mirrors the player state into StateFlows that Compose can observe.
 */
class PlayerViewModel(
    private val app: App,
    val playerState: PlayerState,
    val extensions: ExtensionLoader,
) : ViewModel() {

    data class Progress(val position: Long = 0, val buffered: Long = 0, val duration: Long = 0)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller = _controller.asStateFlow()

    val current = playerState.current
    val radio = playerState.radio

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress = _progress.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex = _currentIndex.asStateFlow()

    private val _currentItem = MutableStateFlow<MediaItem?>(null)
    val currentItem = _currentItem.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked = _isLiked.asStateFlow()

    private val _error = MutableStateFlow<PlaybackException?>(null)
    val error = _error.asStateFlow()

    private val audioManager =
        app.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val _volume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val volume = _volume.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = sync()
        override fun onPlaybackStateChanged(playbackState: Int) = sync()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = sync()
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) =
            sync()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = sync()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = sync()
        override fun onRepeatModeChanged(repeatMode: Int) = sync()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) = sync()

        override fun onPlayerError(error: PlaybackException) {
            _error.value = error
        }
    }

    private var release: (() -> Unit)? = null

    init {
        release = PlayerService.getController(app.context) { controller ->
            controller.addListener(listener)
            _controller.value = controller
            sync()
        }
        viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val c = _controller.value
                if (c != null) {
                    _progress.value = Progress(
                        c.currentPosition.coerceAtLeast(0),
                        c.bufferedPosition.coerceAtLeast(0),
                        c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
                    )
                    _volume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
                delay(if (_isPlaying.value) 500 else 1000)
            }
        }
    }

    private fun sync() {
        val c = _controller.value ?: return
        _isPlaying.value = c.isPlaying
        _isBuffering.value = c.playbackState == Player.STATE_BUFFERING
        _shuffle.value = c.shuffleModeEnabled
        _repeatMode.value = c.repeatMode
        _currentIndex.value = c.currentMediaItemIndex
        _currentItem.value = c.currentMediaItem
        _isLiked.value = c.currentMediaItem?.isLiked ?: false
        _queue.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        if (c.playbackState != Player.STATE_IDLE) _error.value = null
    }

    private fun withController(block: MediaController.() -> Unit) {
        val c = _controller.value ?: return
        viewModelScope.launch(Dispatchers.Main) { c.block() }
    }

    fun playPause() = withController {
        if (isPlaying) pause()
        else {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    fun next() = withController { seekToNextMediaItem() }
    fun previous() = withController {
        if (currentPosition > 3000 || !hasPreviousMediaItem()) seekTo(0)
        else seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = withController { seekTo(ms.coerceIn(0, duration.coerceAtLeast(0))) }
    fun seekBy(ms: Long) = withController { seekTo((currentPosition + ms).coerceAtLeast(0)) }
    fun seekToIndex(index: Int) = withController {
        seekTo(index, 0)
        play()
    }

    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeFromQueue(index: Int) = withController { removeMediaItem(index) }
    fun clearQueue() = withController {
        stop()
        clearMediaItems()
    }

    fun like(liked: Boolean) = send(
        if (liked) PlayerCommands.likeCommand else PlayerCommands.unlikeCommand
    )

    fun setVolume(value: Int) {
        val v = value.coerceIn(0, maxVolume)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        _volume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun changeVolume(delta: Int) = setVolume(_volume.value + delta)

    fun play(
        extensionId: String,
        item: EchoMediaItem,
        loaded: Boolean = false,
        shuffle: Boolean = false,
        index: Int = 0,
    ) = send(PlayerCommands.playCommand, itemArgs(extensionId, item, loaded).apply {
        putBoolean("shuffle", shuffle)
        putInt("index", index)
    })

    fun playTracks(
        extensionId: String,
        tracks: List<Track>,
        index: Int = 0,
        context: EchoMediaItem? = null,
        shuffle: Boolean = false,
    ) = send(PlayerCommands.playTracksCommand, bundleOf("extId" to extensionId).apply {
        putSerialized("tracks", tracks)
        if (context != null) putSerialized("context", context)
        putBoolean("shuffle", shuffle)
        putInt("index", index)
    })

    fun addToQueue(extensionId: String, item: EchoMediaItem, loaded: Boolean = false) =
        send(PlayerCommands.addToQueueCommand, itemArgs(extensionId, item, loaded))

    fun addToNext(extensionId: String, item: EchoMediaItem, loaded: Boolean = false) =
        send(PlayerCommands.addToNextCommand, itemArgs(extensionId, item, loaded))

    fun radio(extensionId: String, item: EchoMediaItem, loaded: Boolean = false) =
        send(PlayerCommands.radioCommand, itemArgs(extensionId, item, loaded))

    fun sleepTimer(ms: Long) = send(PlayerCommands.sleepTimer, bundleOf("ms" to ms))

    fun resume(cleared: Boolean = true) =
        send(PlayerCommands.resumeCommand, bundleOf("cleared" to cleared))

    private fun itemArgs(extensionId: String, item: EchoMediaItem, loaded: Boolean) =
        bundleOf("extId" to extensionId, "loaded" to loaded).apply {
            putSerialized("item", item)
        }

    private fun send(command: SessionCommand, args: android.os.Bundle = android.os.Bundle.EMPTY) {
        viewModelScope.launch {
            val c = _controller.value ?: return@launch
            withContext(Dispatchers.Main) { c.sendCustomCommand(command, args) }
        }
    }

    override fun onCleared() {
        _controller.value?.removeListener(listener)
        release?.invoke()
        super.onCleared()
    }
}
