package dev.brahmkshatriya.echo.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingItem
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingMultipleChoice
import dev.brahmkshatriya.echo.common.settings.SettingOnClick
import dev.brahmkshatriya.echo.common.settings.SettingSlider
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.ui.components.ErrorBox
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.rememberTextInput
import dev.brahmkshatriya.echo.ui.components.activityViewModel

@Composable
fun ExtensionSettingsScreen(
    type: ExtensionType,
    id: String,
    viewModel: SettingsViewModel = activityViewModel(),
) {
    val all by viewModel.extensionLoader.all.collectAsStateWithLifecycle()
    val extension = all.find { it.type == type && it.id == id }
    LaunchedEffect(extension?.id) { if (extension != null) viewModel.loadExtensionSettings(extension) }
    val state by viewModel.extensionSettings.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val revision by viewModel.revision.collectAsStateWithLifecycle()

    var pendingTextKey by remember { mutableStateOf<String?>(null) }
    var pendingTextLabel by remember { mutableStateOf("") }
    val launchText = rememberTextInput(pendingTextLabel) { text ->
        val key = pendingTextKey ?: return@rememberTextInput
        viewModel.extensionPrefs()?.edit()?.putString(key, text)?.apply()
    }

    ListScreen {
        item { SectionHeader(extension?.name ?: stringResource(R.string.settings)) }
        if (extension == null) {
            item { MessageBox(stringResource(R.string.extension_not_found_x, id)) }
            return@ListScreen
        }
        if (state.loading) {
            item { LoadingBox() }
            return@ListScreen
        }
        val error = state.error
        if (error != null) {
            item { ErrorBox(error, onRetry = { viewModel.loadExtensionSettings(extension) }) }
            return@ListScreen
        }
        if (state.items.isEmpty()) {
            item { MessageBox(stringResource(R.string.no_settings_available), icon = R.drawable.ic_info) }
            return@ListScreen
        }
        state.items.forEach {
            settingItems(it, viewModel) { key, label ->
                pendingTextKey = key
                pendingTextLabel = label
                launchText(viewModel.extensionPrefs()?.getString(key, null))
            }
        }
    }
}

private fun ScalingLazyListScope.settingItems(
    setting: Setting,
    viewModel: SettingsViewModel,
    requestText: (key: String, label: String) -> Unit,
) {
    val prefs = viewModel.extensionPrefs() ?: return
    when (setting) {
        is SettingCategory -> {
            item { SectionHeader(setting.title) }
            setting.items.forEach { settingItems(it, viewModel, requestText) }
        }

        is SettingItem -> item {
            Chip(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = setting.summary?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            )
        }

        is SettingSwitch -> item {
            val checked = prefs.getBoolean(setting.key, setting.defaultValue)
            ToggleChip(
                checked = checked,
                onCheckedChange = { prefs.edit().putBoolean(setting.key, it).apply() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = setting.summary?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                toggleControl = {
                    Icon(imageVector = ToggleChipDefaults.switchIcon(checked), contentDescription = null)
                }
            )
        }

        is SettingList -> item {
            val values = setting.entryValues
            val titles = setting.entryTitles
            val current = prefs.getString(setting.key, null)
                ?: setting.defaultEntryIndex?.let { values.getOrNull(it) }
            val index = values.indexOf(current)
            Chip(
                onClick = {
                    if (values.isEmpty()) return@Chip
                    val next = values[(index + 1) % values.size]
                    prefs.edit().putString(setting.key, next).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        titles.getOrNull(index) ?: setting.summary ?: "",
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        is SettingMultipleChoice -> {
            item { SectionHeader(setting.title) }
            val values = setting.entryValues
            val titles = setting.entryTitles
            items(values.size) { i ->
                val selected = prefs.getStringSet(setting.key, null)
                    ?: setting.defaultEntryIndices?.mapNotNull { values.getOrNull(it) }?.toSet()
                    ?: emptySet()
                val checked = values[i] in selected
                ToggleChip(
                    checked = checked,
                    onCheckedChange = { on ->
                        val new = if (on) selected + values[i] else selected - values[i]
                        prefs.edit().putStringSet(setting.key, new).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(titles.getOrNull(i) ?: values[i], maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    toggleControl = {
                        Icon(imageVector = ToggleChipDefaults.checkboxIcon(checked), contentDescription = null)
                    }
                )
            }
        }

        is SettingSlider -> item {
            val step = setting.steps?.takeIf { it > 0 } ?: 1
            val current = prefs.getInt(setting.key, setting.defaultValue ?: setting.from)
            Chip(
                onClick = {
                    var next = current + step
                    if (next > setting.to) next = setting.from
                    prefs.edit().putInt(setting.key, next).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        "$current  (${setting.from} – ${setting.to})",
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        is SettingTextInput -> item {
            val current = prefs.getString(setting.key, setting.defaultValue)
            Chip(
                onClick = { requestText(setting.key, setting.title) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_keyboard), null) },
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        current?.takeIf { it.isNotBlank() } ?: setting.summary
                        ?: stringResource(R.string.tap_to_enter),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        is SettingOnClick -> item {
            Chip(
                onClick = { viewModel.runOnClick(setting.onClick) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(setting.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = setting.summary?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            )
        }
    }
}
