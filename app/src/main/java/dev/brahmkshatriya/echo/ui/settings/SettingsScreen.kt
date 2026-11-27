package dev.brahmkshatriya.echo.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import dev.brahmkshatriya.echo.BuildConfig
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.playback.PlayerService
import dev.brahmkshatriya.echo.playback.listener.PlayerRadio
import dev.brahmkshatriya.echo.ui.CrashActivity
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.utils.CrashHandler
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun SettingsScreen(
    openAbout: () -> Unit,
    viewModel: SettingsViewModel = koinActivityViewModel(),
) {
    // Re-read preferences whenever one changes.
    val revision by viewModel.revision.collectAsStateWithLifecycle()
    val prefs = viewModel.prefs

    ListScreen {
        item { SectionHeader(stringResource(R.string.settings)) }

        item { SectionHeader(stringResource(R.string.playback)) }
        qualityItem(
            title = R.string.stream_quality,
            key = PlayerService.STREAM_QUALITY,
            value = prefs.getString(PlayerService.STREAM_QUALITY, "medium") ?: "medium",
            allowOff = false,
            viewModel = viewModel
        )
        qualityItem(
            title = R.string.unmetered_stream_quality,
            key = PlayerService.UNMETERED_STREAM_QUALITY,
            value = prefs.getString(PlayerService.UNMETERED_STREAM_QUALITY, "off") ?: "off",
            allowOff = true,
            viewModel = viewModel
        )
        switchItem(
            title = R.string.auto_start_radio,
            summary = R.string.auto_start_radio_summary,
            checked = prefs.getBoolean(PlayerRadio.AUTO_START_RADIO, true)
        ) { viewModel.putBoolean(PlayerRadio.AUTO_START_RADIO, it) }
        switchItem(
            title = R.string.skip_silence,
            summary = R.string.skip_silence_summary,
            checked = prefs.getBoolean(PlayerService.SKIP_SILENCE, false)
        ) { viewModel.putBoolean(PlayerService.SKIP_SILENCE, it) }
        switchItem(
            title = R.string.audio_offload,
            summary = R.string.audio_offload_summary,
            checked = prefs.getBoolean(PlayerService.MORE_BRAIN_CAPACITY, false)
        ) { viewModel.putBoolean(PlayerService.MORE_BRAIN_CAPACITY, it) }
        switchItem(
            title = R.string.close_player_when_app_closes,
            summary = R.string.close_player_when_app_closes_summary,
            checked = prefs.getBoolean(PlayerService.CLOSE_PLAYER, false)
        ) { viewModel.putBoolean(PlayerService.CLOSE_PLAYER, it) }

        item { SectionHeader(stringResource(R.string.cache)) }
        item {
            val current = prefs.getInt(PlayerService.CACHE_SIZE, 100)
            val sizes = listOf(50, 100, 250, 500, 1000)
            Chip(
                onClick = {
                    val next = sizes[(sizes.indexOf(current).coerceAtLeast(0) + 1) % sizes.size]
                    viewModel.putInt(PlayerService.CACHE_SIZE, next)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(stringResource(R.string.cache_size)) },
                secondaryLabel = { Text(stringResource(R.string.x_mb, current)) }
            )
        }
        item {
            Text(
                stringResource(R.string.cache_size_summary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item { SectionHeader(stringResource(R.string.about)) }
        item {
            Chip(
                onClick = openAbout,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_info), null) },
                label = { Text(stringResource(R.string.app_name)) },
                secondaryLabel = {
                    Text(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
        item {
            val context = LocalContext.current
            val lastCrash = remember(revision) { CrashHandler.lastCrash(context) }
            if (lastCrash != null) Chip(
                onClick = { context.startActivity(CrashActivity.intent(context, lastCrash)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_error), null) },
                label = { Text(stringResource(R.string.last_crash)) },
                secondaryLabel = {
                    Text(
                        stringResource(R.string.last_crash_summary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

private fun ScalingLazyListScope.switchItem(
    title: Int,
    summary: Int?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    item {
        ToggleChip(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            secondaryLabel = summary?.let {
                { Text(stringResource(it), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
            toggleControl = {
                Icon(imageVector = ToggleChipDefaults.switchIcon(checked), contentDescription = null)
            }
        )
    }
}

private fun ScalingLazyListScope.qualityItem(
    title: Int,
    key: String,
    value: String,
    allowOff: Boolean,
    viewModel: SettingsViewModel,
) {
    item {
        val values = buildList {
            if (allowOff) add("off")
            addAll(PlayerService.streamQualities)
        }
        Chip(
            onClick = {
                val next = values[(values.indexOf(value).coerceAtLeast(0) + 1) % values.size]
                viewModel.putString(key, next)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.secondaryChipColors(),
            icon = { Icon(painterResource(R.drawable.ic_high_quality), null) },
            label = { Text(stringResource(title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            secondaryLabel = { Text(qualityLabel(value)) }
        )
    }
}

@Composable
fun qualityLabel(value: String) = stringResource(
    when (value) {
        "highest" -> R.string.highest
        "medium" -> R.string.medium
        "lowest" -> R.string.lowest
        else -> R.string.off
    }
)
