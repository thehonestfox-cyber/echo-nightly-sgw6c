package dev.brahmkshatriya.echo.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.FollowClient
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.clients.SaveClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.get
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtension
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.extensions.cache.Cached
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Detail page of an [EchoMediaItem] (album, artist, playlist, radio or track):
 * loads the full item, its tracks and the related shelves.
 */
class MediaViewModel(
    val app: App,
    val extensions: ExtensionLoader,
) : ViewModel() {

    data class State(
        val extensionId: String = "",
        val item: EchoMediaItem? = null,
        val loaded: MediaState.Loaded<EchoMediaItem>? = null,
        val tracks: List<Track> = emptyList(),
        val hasMoreTracks: Boolean = false,
        val shelves: List<Shelf> = emptyList(),
        val loading: Boolean = true,
        val loadingTracks: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var extension: MusicExtension? = null
    private var tracksPaged: PagedData<Track>? = null
    private var tracksContinuation: String? = null
    private var job: Job? = null

    fun load(extensionId: String, item: EchoMediaItem) {
        if (_state.value.item?.id == item.id && _state.value.extensionId == extensionId
            && _state.value.error == null
        ) return
        job?.cancel()
        _state.value = State(extensionId, item)
        job = viewModelScope.launch {
            val ext = extensions.music.getExtension(extensionId)
            if (ext == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = IllegalStateException("Extension $extensionId not found")
                )
                return@launch
            }
            extension = ext
            val loaded = Cached.loadMedia(app, ext, MediaState.Unloaded(extensionId, item))
                .getOrElse {
                    _state.value = _state.value.copy(loading = false, error = it)
                    return@launch
                }
            _state.value = _state.value.copy(
                item = loaded.item, loaded = loaded, loading = false, loadingTracks = true
            )
            val tracksFeed = Cached.loadTracks(ext, loaded.item).getOrElse {
                app.throwFlow.emit(it)
                null
            }
            if (tracksFeed != null) {
                val paged = ext.get { tracksFeed.pagedDataOfFirst() }.getOrNull()
                tracksPaged = paged
                if (paged != null) {
                    val page = ext.get { paged.loadPage(null) }.getOrElse {
                        app.throwFlow.emit(it)
                        null
                    }
                    tracksContinuation = page?.continuation
                    _state.value = _state.value.copy(
                        tracks = page?.data.orEmpty(),
                        hasMoreTracks = page?.continuation != null,
                        loadingTracks = false
                    )
                }
            }
            _state.value = _state.value.copy(loadingTracks = false)

            val feed = Cached.loadFeed(ext, loaded.item).getOrElse {
                app.throwFlow.emit(it)
                null
            }
            if (feed != null) {
                val shelves = ext.get { feed.pagedDataOfFirst().loadPage(null).data }.getOrElse {
                    app.throwFlow.emit(it)
                    emptyList()
                }
                _state.value = _state.value.copy(shelves = shelves)
            }
        }
    }

    fun loadMoreTracks() {
        val state = _state.value
        if (!state.hasMoreTracks || state.loadingTracks) return
        val ext = extension ?: return
        val paged = tracksPaged ?: return
        val cont = tracksContinuation ?: return
        viewModelScope.launch {
            _state.value = state.copy(loadingTracks = true)
            val page = ext.get { paged.loadPage(cont) }.getOrElse {
                app.throwFlow.emit(it)
                _state.value = _state.value.copy(loadingTracks = false, hasMoreTracks = false)
                return@launch
            }
            tracksContinuation = page.continuation
            _state.value = _state.value.copy(
                tracks = _state.value.tracks + page.data,
                hasMoreTracks = page.continuation != null,
                loadingTracks = false
            )
        }
    }

    fun toggleLike() {
        val ext = extension ?: return
        val loaded = _state.value.loaded ?: return
        val liked = loaded.isLiked ?: return
        viewModelScope.launch {
            ext.getAs<LikeClient, Unit> { likeItem(loaded.item, !liked) }.getOrElse {
                app.throwFlow.emit(it)
                return@launch
            }
            _state.value = _state.value.copy(loaded = loaded.copyWith(isLiked = !liked))
        }
    }

    fun toggleSave() {
        val ext = extension ?: return
        val loaded = _state.value.loaded ?: return
        val saved = loaded.isSaved ?: return
        viewModelScope.launch {
            ext.getAs<SaveClient, Unit> { saveToLibrary(loaded.item, !saved) }.getOrElse {
                app.throwFlow.emit(it)
                return@launch
            }
            _state.value = _state.value.copy(loaded = loaded.copyWith(isSaved = !saved))
        }
    }

    fun toggleFollow() {
        val ext = extension ?: return
        val loaded = _state.value.loaded ?: return
        val followed = loaded.isFollowed ?: return
        viewModelScope.launch {
            ext.getAs<FollowClient, Unit> { followItem(loaded.item, !followed) }.getOrElse {
                app.throwFlow.emit(it)
                return@launch
            }
            _state.value = _state.value.copy(loaded = loaded.copyWith(isFollowed = !followed))
        }
    }

    private fun MediaState.Loaded<EchoMediaItem>.copyWith(
        isLiked: Boolean? = this.isLiked,
        isSaved: Boolean? = this.isSaved,
        isFollowed: Boolean? = this.isFollowed,
    ) = MediaState.Loaded(
        extensionId, item, isFollowed, followers, isSaved, isLiked, isHidden, showRadio, showShare
    )
}
