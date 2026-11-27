package dev.brahmkshatriya.echo.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import dev.brahmkshatriya.echo.ui.components.activityViewModel

data class HomeNavigation(
    val openHomeFeed: () -> Unit,
    val openSearch: () -> Unit,
    val openLibrary: () -> Unit,
    val openPlayer: () -> Unit,
    val openExtensions: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun HomeScreen(
    navigation: HomeNavigation,
    playerViewModel: PlayerViewModel = activityViewModel(),
) {
    val current by playerViewModel.extensions.current.collectAsStateWithLifecycle()
    val mediaItem by playerViewModel.currentItem.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    ListScreen {
        item { SectionHeader(stringResource(R.string.app_name)) }

        item {
            val title = mediaItem?.mediaMetadata?.title?.toString()
            Chip(
                onClick = navigation.openPlayer,
                modifier = Modifier.fillMaxWidth(),
                colors = if (title != null) ChipDefaults.gradientBackgroundChipColors()
                else ChipDefaults.secondaryChipColors(),
                icon = {
                    Icon(
                        painterResource(
                            if (isPlaying) R.drawable.ic_equalizer else R.drawable.ic_play
                        ), null
                    )
                },
                label = {
                    Text(
                        title ?: stringResource(R.string.now_playing),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = if (title != null) {
                    {
                        Text(
                            mediaItem?.mediaMetadata?.artist?.toString()
                                ?: stringResource(R.string.now_playing),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null
            )
        }

        item {
            Chip(
                onClick = navigation.openExtensions,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                icon = {
                    if (current?.metadata?.icon != null) CoverImage(
                        current?.metadata?.icon, size = ChipDefaults.IconSize,
                        placeholder = R.drawable.ic_extension
                    ) else Icon(painterResource(R.drawable.ic_extension), null)
                },
                label = {
                    Text(
                        current?.name ?: stringResource(R.string.no_extension_selected),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = { Text(stringResource(R.string.extensions)) }
            )
        }

        item {
            MenuChip(R.string.home, R.drawable.ic_home_outline, navigation.openHomeFeed)
        }
        item {
            MenuChip(R.string.search, R.drawable.ic_search_outline, navigation.openSearch)
        }
        item {
            MenuChip(R.string.library, R.drawable.ic_library_music, navigation.openLibrary)
        }
        item {
            MenuChip(R.string.settings, R.drawable.ic_settings_outline, navigation.openSettings)
        }
    }
}

@Composable
private fun MenuChip(label: Int, icon: Int, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(),
        icon = { Icon(painterResource(icon), null) },
        label = { Text(stringResource(label)) }
    )
}
