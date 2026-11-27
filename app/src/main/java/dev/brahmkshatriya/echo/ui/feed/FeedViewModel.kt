package dev.brahmkshatriya.echo.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.get
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Loads a [Feed] of shelves (home, library, search or any nested "more"/category feed)
 * for the currently selected music extension and exposes it page by page.
 */
class FeedViewModel(
    val app: App,
    val extensions: ExtensionLoader,
) : ViewModel() {

    enum class Type { Home, Library, Search, Custom }

    sealed class State {
        data object NoExtension : State()
        data object Loading : State()
        data class Error(val throwable: Throwable) : State()
        data class Loaded(
            val extensionId: String,
            val tabs: List<Tab>,
            val selectedTab: Tab?,
            val shelves: List<Shelf>,
            val buttons: Feed.Buttons?,
            val hasMore: Boolean,
            val loadingMore: Boolean,
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val current = extensions.current

    private var type = Type.Home
    private var customFeed: Feed<Shelf>? = null
    private var feed: Feed<Shelf>? = null
    private var pagedData: PagedData<Shelf>? = null
    private var continuation: String? = null
    private var job: Job? = null
    private var observing = false

    /** Follows the selected extension: every time it changes the feed is reloaded. */
    fun observe(type: Type) {
        this.type = type
        if (observing) return
        observing = true
        viewModelScope.launch {
            extensions.current.collectLatest { ext ->
                if (ext == null) _state.value = State.NoExtension
                else load(ext)
            }
        }
    }

    fun setCustomFeed(feed: Feed<Shelf>?) {
        type = Type.Custom
        customFeed = feed
        if (observing) return
        observing = true
        viewModelScope.launch {
            extensions.current.collectLatest { ext ->
                if (ext == null) _state.value = State.NoExtension
                else load(ext)
            }
        }
    }

    fun search(query: String) {
        _query.value = query
        val ext = extensions.current.value ?: return
        load(ext)
    }

    fun refresh() {
        val ext = extensions.current.value ?: return
        load(ext)
    }

    private fun load(extension: MusicExtension) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = State.Loading
            if (type == Type.Search && _query.value.isBlank()) {
                _state.value = State.Loaded(
                    extension.id, emptyList(), null, emptyList(), null,
                    hasMore = false, loadingMore = false
                )
                return@launch
            }
            val result: Result<Feed<Shelf>?> = when (type) {
                Type.Home -> extension.getAs<HomeFeedClient, Feed<Shelf>?> { loadHomeFeed() }
                Type.Library -> extension.getAs<LibraryFeedClient, Feed<Shelf>?> {
                    loadLibraryFeed()
                }

                Type.Search -> extension.getAs<SearchFeedClient, Feed<Shelf>?> {
                    loadSearchFeed(_query.value)
                }

                Type.Custom -> Result.success(customFeed)
            }
            val loadedFeed = result.getOrElse {
                _state.value = State.Error(it)
                return@launch
            }
            if (loadedFeed == null) {
                _state.value = State.Loaded(
                    extension.id, emptyList(), null, emptyList(), null,
                    hasMore = false, loadingMore = false
                )
                return@launch
            }
            feed = loadedFeed
            selectTab(loadedFeed.notSortTabs.firstOrNull(), extension)
        }
    }

    fun selectTab(tab: Tab?) {
        val ext = extensions.current.value ?: return
        job?.cancel()
        job = viewModelScope.launch { selectTab(tab, ext) }
    }

    private suspend fun selectTab(tab: Tab?, extension: MusicExtension) {
        val currentFeed = feed ?: return
        _state.value = State.Loaded(
            extension.id, currentFeed.notSortTabs, tab, emptyList(), null,
            hasMore = false, loadingMore = true
        )
        val data = extension.get { currentFeed.getPagedData(tab) }.getOrElse {
            _state.value = State.Error(it)
            return
        }
        pagedData = data.pagedData
        continuation = null
        val page = extension.get { data.pagedData.loadPage(null) }.getOrElse {
            _state.value = State.Error(it)
            return
        }
        continuation = page.continuation
        _state.value = State.Loaded(
            extension.id, currentFeed.notSortTabs, tab, page.data, data.buttons,
            hasMore = page.continuation != null, loadingMore = false
        )
    }

    fun loadMore() {
        val state = _state.value as? State.Loaded ?: return
        if (!state.hasMore || state.loadingMore) return
        val ext = extensions.current.value ?: return
        val data = pagedData ?: return
        val cont = continuation ?: return
        viewModelScope.launch {
            _state.value = state.copy(loadingMore = true)
            val page = ext.get { data.loadPage(cont) }.getOrElse {
                app.throwFlow.emit(it)
                _state.value = state.copy(loadingMore = false, hasMore = false)
                return@launch
            }
            continuation = page.continuation
            _state.value = state.copy(
                shelves = state.shelves + page.data,
                hasMore = page.continuation != null,
                loadingMore = false
            )
        }
    }
}
