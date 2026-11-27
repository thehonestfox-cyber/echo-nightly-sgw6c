package dev.brahmkshatriya.echo.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.ui.components.ErrorBox
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.ShelfActions
import dev.brahmkshatriya.echo.ui.components.rememberTextInput
import dev.brahmkshatriya.echo.ui.components.shelfItems
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import dev.brahmkshatriya.echo.ui.components.activityViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Callbacks a feed needs from the navigation host. */
data class FeedNavigation(
    val openItem: (extensionId: String, item: EchoMediaItem) -> Unit,
    val openFeed: (title: String, feed: Feed<Shelf>) -> Unit,
    val openExtensions: () -> Unit,
)

@Composable
fun FeedScreen(
    type: FeedViewModel.Type,
    navigation: FeedNavigation,
    title: String? = null,
    customFeed: Feed<Shelf>? = null,
    viewModel: FeedViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = activityViewModel(),
) {
    LaunchedEffect(type, customFeed) {
        if (type == FeedViewModel.Type.Custom) viewModel.setCustomFeed(customFeed)
        else viewModel.observe(type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val launchSearch = rememberTextInput(stringResource(R.string.search)) { viewModel.search(it) }

    // Infinite scroll
    LaunchedEffect(listState, state) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { last ->
                val total = listState.layoutInfo.totalItemsCount
                last != null && total > 0 && last >= total - 3
            }
            .collect { viewModel.loadMore() }
    }

    val screenTitle = title ?: when (type) {
        FeedViewModel.Type.Home -> current?.name ?: stringResource(R.string.home)
        FeedViewModel.Type.Library -> stringResource(R.string.library)
        FeedViewModel.Type.Search -> stringResource(R.string.search)
        FeedViewModel.Type.Custom -> ""
    }

    ListScreen(state = listState) {
        if (screenTitle.isNotBlank()) item { SectionHeader(screenTitle) }

        if (type == FeedViewModel.Type.Search) item {
            Chip(
                onClick = { launchSearch(query) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_search_outline), null) },
                label = {
                    Text(
                        query.ifBlank { stringResource(R.string.search_hint) },
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        when (val s = state) {
            FeedViewModel.State.NoExtension -> item {
                NoExtensionBox(navigation.openExtensions)
            }

            FeedViewModel.State.Loading -> item { LoadingBox() }

            is FeedViewModel.State.Error -> item {
                ErrorBox(s.throwable, onRetry = { viewModel.refresh() })
            }

            is FeedViewModel.State.Loaded -> {
                val actions = ShelfActions(
                    extensionId = s.extensionId,
                    openItem = { navigation.openItem(s.extensionId, it) },
                    playTracks = { tracks, index, context ->
                        playerViewModel.playTracks(s.extensionId, tracks, index, context)
                    },
                    openCategory = { category ->
                        val feed = category.feed
                        if (feed != null) navigation.openFeed(category.title, feed)
                    },
                    openMore = navigation.openFeed,
                )
                if (s.tabs.size > 1) item {
                    TabsRow(s.tabs, s.selectedTab) { viewModel.selectTab(it) }
                }
                feedButtons(s, playerViewModel)
                val single = s.shelves.singleOrNull() as? Shelf.Lists<*>
                if (single != null && type == FeedViewModel.Type.Custom) {
                    shelfItems(single, actions, limit = Int.MAX_VALUE, showHeader = false)
                } else s.shelves.forEach { shelfItems(it, actions) }

                if (s.shelves.isEmpty() && !s.loadingMore) item {
                    MessageBox(
                        if (type == FeedViewModel.Type.Search && query.isBlank())
                            stringResource(R.string.search_hint)
                        else stringResource(R.string.list_is_empty),
                        icon = R.drawable.ic_info
                    )
                }
                if (s.loadingMore) item { LoadingBox() }
            }
        }
    }
}

private fun ScalingLazyListScope.feedButtons(
    state: FeedViewModel.State.Loaded,
    playerViewModel: PlayerViewModel,
) {
    val buttons = state.buttons ?: return
    val tracks = buttons.customTrackList
    if (!buttons.showPlayAndShuffle || tracks.isNullOrEmpty()) return
    item {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            CompactChip(
                onClick = { playerViewModel.playTracks(state.extensionId, tracks, 0) },
                label = { Text(stringResource(R.string.play)) },
                icon = { Icon(painterResource(R.drawable.ic_play), null) }
            )
            CompactChip(
                onClick = { playerViewModel.playTracks(state.extensionId, tracks, 0, shuffle = true) },
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.shuffle)) },
                icon = { Icon(painterResource(R.drawable.ic_shuffle_40dp), null) }
            )
        }
    }
}

@Composable
fun TabsRow(tabs: List<Tab>, selected: Tab?, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selected?.id
            CompactChip(
                onClick = { onSelect(tab) },
                colors = if (isSelected) ChipDefaults.primaryChipColors()
                else ChipDefaults.secondaryChipColors(),
                label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
fun NoExtensionBox(openExtensions: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MessageBox(stringResource(R.string.no_extension_selected), icon = R.drawable.ic_extension)
        Chip(
            onClick = openExtensions,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.extensions)) },
            icon = { Icon(painterResource(R.drawable.ic_extension), null) }
        )
    }
}
