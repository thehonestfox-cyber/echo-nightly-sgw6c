package dev.brahmkshatriya.echo.ui.extensions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ErrorBox
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.rememberTextInput
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun AddExtensionScreen(
    back: () -> Unit,
    viewModel: ExtensionsViewModel = koinActivityViewModel(),
) {
    val state by viewModel.addState.collectAsStateWithLifecycle()
    var link by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }
    DisposableEffect(Unit) { onDispose { viewModel.resetAddState() } }

    val launchInput = rememberTextInput(stringResource(R.string.link_or_code)) {
        link = it.trim()
        if (link.isNotEmpty()) viewModel.loadExtensionList(link)
    }

    ListScreen {
        item { SectionHeader(stringResource(R.string.add_extension)) }
        item {
            Chip(
                onClick = { launchInput(link) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_keyboard), null) },
                label = {
                    Text(
                        link.ifBlank { stringResource(R.string.link_or_code) },
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = { Text(stringResource(R.string.extension_list_hint)) }
            )
        }
        when (val s = state) {
            ExtensionsViewModel.AddState.Idle -> item {
                MessageBox(stringResource(R.string.add_extension_help), icon = R.drawable.ic_info)
            }

            ExtensionsViewModel.AddState.Loading -> item { LoadingBox(text = stringResource(R.string.loading)) }

            is ExtensionsViewModel.AddState.Error -> item {
                ErrorBox(s.throwable, onRetry = { if (link.isNotBlank()) viewModel.loadExtensionList(link) })
            }

            is ExtensionsViewModel.AddState.List -> {
                if (s.list.isEmpty()) item { MessageBox(stringResource(R.string.list_is_empty)) }
                items(s.list.size) { i ->
                    val entry = s.list[i]
                    val checked = entry.item.id in selected.value
                    ToggleChip(
                        checked = checked,
                        onCheckedChange = { on ->
                            selected.value =
                                if (on) selected.value + entry.item.id else selected.value - entry.item.id
                        },
                        enabled = !entry.isInstalled,
                        modifier = Modifier.fillMaxWidth(),
                        appIcon = {
                            CoverImage(
                                entry.item.iconUrl?.toImageHolder(),
                                size = ChipDefaults.IconSize,
                                shape = CircleShape,
                                placeholder = R.drawable.ic_extension
                            )
                        },
                        label = { Text(entry.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = {
                            Text(
                                if (entry.isInstalled) stringResource(R.string.installed)
                                else entry.item.subtitle ?: "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        },
                        toggleControl = {
                            Icon(
                                imageVector = ToggleChipDefaults.checkboxIcon(checked),
                                contentDescription = null
                            )
                        }
                    )
                }
                if (s.list.isNotEmpty()) item {
                    val count = selected.value.size
                    Chip(
                        onClick = {
                            val items = s.list.filter { it.item.id in selected.value }.map { it.item }
                            viewModel.install(items)
                        },
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(),
                        icon = { Icon(painterResource(R.drawable.ic_download_for_offline), null) },
                        label = { Text(stringResource(R.string.install_x, count)) }
                    )
                }
            }

            is ExtensionsViewModel.AddState.Installing -> item {
                LoadingBox(
                    text = stringResource(
                        R.string.installing_x_of_y, s.item.name, s.done + 1, s.total
                    )
                )
            }

            is ExtensionsViewModel.AddState.Done -> {
                item {
                    MessageBox(
                        stringResource(R.string.installed_x_failed_y, s.installed, s.failed),
                        icon = if (s.failed == 0) R.drawable.ic_check else R.drawable.ic_error
                    )
                }
                item {
                    Chip(
                        onClick = back,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(),
                        label = { Text(stringResource(R.string.done)) }
                    )
                }
            }
        }
    }
}
