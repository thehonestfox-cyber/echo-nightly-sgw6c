package dev.brahmkshatriya.echo.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.activityViewModel

data class ExtensionsNavigation(
    val openExtension: (ExtensionType, String) -> Unit,
    val openAdd: () -> Unit,
    val back: () -> Unit,
)

@Composable
fun ExtensionsScreen(
    navigation: ExtensionsNavigation,
    viewModel: ExtensionsViewModel = activityViewModel(),
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()

    ListScreen {
        item { SectionHeader(stringResource(R.string.extensions)) }
        item {
            Chip(
                onClick = navigation.openAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                icon = { Icon(painterResource(R.drawable.ic_add_20dp), null) },
                label = { Text(stringResource(R.string.add_extension)) }
            )
        }
        ExtensionType.entries.forEach { type ->
            val list = all.filter { it.type == type }
            if (list.isEmpty()) return@forEach
            item { SectionHeader(stringResource(type.label())) }
            items(list.size) { i ->
                val ext = list[i]
                ExtensionChip(
                    extension = ext,
                    selected = ext.id == current?.id && type == ExtensionType.MUSIC,
                    onClick = {
                        if (ext is MusicExtension && ext.isEnabled) {
                            viewModel.selectExtension(ext)
                            navigation.back()
                        } else navigation.openExtension(type, ext.id)
                    },
                    onMore = { navigation.openExtension(type, ext.id) }
                )
            }
        }
        if (all.isEmpty()) item {
            MessageBox(stringResource(R.string.no_extensions_installed), icon = R.drawable.ic_extension)
        }
    }
}

fun ExtensionType.label() = when (this) {
    ExtensionType.MUSIC -> R.string.music
    ExtensionType.TRACKER -> R.string.tracker
    ExtensionType.LYRICS -> R.string.lyrics
    ExtensionType.MISC -> R.string.misc
}

@Composable
fun ExtensionChip(
    extension: Extension<*>,
    selected: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) ChipDefaults.gradientBackgroundChipColors()
        else ChipDefaults.secondaryChipColors(),
        icon = {
            CoverImage(
                extension.metadata.icon,
                size = ChipDefaults.LargeIconSize,
                shape = CircleShape,
                placeholder = R.drawable.ic_extension
            )
        },
        label = {
            Text(
                extension.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (!extension.isEnabled) MaterialTheme.colors.onSurfaceVariant
                else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            if (onMore != null) Icon(
                painterResource(R.drawable.ic_more_horiz),
                contentDescription = stringResource(R.string.more),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                tint = MaterialTheme.colors.onSurfaceVariant
            )
        },
        secondaryLabel = {
            Text(
                if (!extension.isEnabled) stringResource(R.string.disabled)
                else "v${extension.version} · ${extension.metadata.author}",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    )
}
