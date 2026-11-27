package dev.brahmkshatriya.echo.ui.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.ImportType
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.activityViewModel

data class ExtensionNavigation(
    val openLogin: (ExtensionType, String) -> Unit,
    val openSettings: (ExtensionType, String) -> Unit,
    val back: () -> Unit,
)

@Composable
fun ExtensionScreen(
    type: ExtensionType,
    id: String,
    navigation: ExtensionNavigation,
    viewModel: ExtensionsViewModel = activityViewModel(),
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val extension = all.find { it.type == type && it.id == id }
    var confirmUninstall by remember { mutableStateOf(false) }

    ListScreen {
        if (extension == null) {
            item { MessageBox(stringResource(R.string.extension_not_found_x, id)) }
            return@ListScreen
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoverImage(
                    extension.metadata.icon, size = 56.dp, shape = CircleShape,
                    placeholder = R.drawable.ic_extension
                )
                Text(
                    extension.name,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "v${extension.version} · ${extension.metadata.author}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (extension.metadata.description.isNotBlank()) Text(
                    extension.metadata.description,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 6, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (busy.contains(extension.id)) item { LoadingBox() }

        item {
            ToggleChip(
                checked = extension.isEnabled,
                onCheckedChange = { viewModel.setExtensionEnabled(type, extension.id, it) },
                label = { Text(stringResource(R.string.enabled)) },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(extension.isEnabled),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (extension is MusicExtension && extension.isEnabled) item {
            val selected = current?.id == extension.id
            Chip(
                onClick = {
                    viewModel.selectExtension(extension)
                    navigation.back()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !selected,
                colors = ChipDefaults.primaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_check), null) },
                label = {
                    Text(stringResource(if (selected) R.string.selected else R.string.select))
                }
            )
        }

        item {
            Chip(
                onClick = { navigation.openLogin(type, extension.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                enabled = extension.isEnabled,
                icon = { Icon(painterResource(R.drawable.ic_account_circle), null) },
                label = { Text(stringResource(R.string.accounts)) }
            )
        }
        item {
            Chip(
                onClick = { navigation.openSettings(type, extension.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                enabled = extension.isEnabled,
                icon = { Icon(painterResource(R.drawable.ic_settings_outline), null) },
                label = { Text(stringResource(R.string.settings)) }
            )
        }
        if (!extension.metadata.updateUrl.isNullOrBlank()) item {
            Chip(
                onClick = { viewModel.update(extension) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                enabled = !busy.contains(extension.id),
                icon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                label = { Text(stringResource(R.string.check_for_updates)) }
            )
        }
        if (extension.metadata.importType == ImportType.File) item {
            Chip(
                onClick = { confirmUninstall = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                enabled = !busy.contains(extension.id),
                icon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colors.error) },
                label = { Text(stringResource(R.string.uninstall), color = MaterialTheme.colors.error) }
            )
        }
        item { SectionHeader(stringResource(R.string.information)) }
        item {
            Text(
                buildString {
                    append(stringResource(R.string.type_x, stringResource(type.label())))
                    append('\n')
                    append(stringResource(R.string.source_x, extension.metadata.importType.name))
                    append('\n')
                    append("ID: ${extension.id}")
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }

    Dialog(showDialog = confirmUninstall, onDismissRequest = { confirmUninstall = false }) {
        Alert(
            title = {
                Text(
                    stringResource(R.string.uninstall_x, extension?.name ?: id),
                    textAlign = TextAlign.Center
                )
            },
            negativeButton = {
                Button(
                    onClick = { confirmUninstall = false },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) }
            },
            positiveButton = {
                Button(
                    onClick = {
                        confirmUninstall = false
                        if (extension != null) viewModel.uninstall(extension)
                        navigation.back()
                    },
                    colors = ButtonDefaults.primaryButtonColors()
                ) { Icon(painterResource(R.drawable.ic_check), stringResource(R.string.uninstall)) }
            }
        ) {
            Text(
                stringResource(R.string.uninstall_confirmation),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2
            )
        }
    }
}
