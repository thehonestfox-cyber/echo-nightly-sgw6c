package dev.brahmkshatriya.echo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.extensions.exceptions.AppException
import dev.brahmkshatriya.echo.extensions.exceptions.ExtensionLoaderException
import dev.brahmkshatriya.echo.extensions.exceptions.ExtensionNotFoundException
import dev.brahmkshatriya.echo.extensions.exceptions.InvalidExtensionListException
import dev.brahmkshatriya.echo.extensions.exceptions.RequiredExtensionsMissingException
import dev.brahmkshatriya.echo.playback.exceptions.PlayerException

/** Standard page: time at the top, vignette, position indicator and a scaling list. */
@Composable
fun ListScreen(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState = rememberScalingLazyListState(),
    showTime: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        timeText = if (showTime) ({ TimeText() }) else null,
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    ListHeader {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.primary
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.surface,
            strokeWidth = 3.dp
        )
        if (text != null) Text(
            text,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FullScreenLoading(text: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox(text = text)
    }
}

@Composable
fun MessageBox(text: String, icon: Int? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) Icon(
            painterResource(icon), null,
            modifier = Modifier.size(28.dp).padding(bottom = 4.dp),
            tint = MaterialTheme.colors.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorBox(throwable: Throwable, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_error), null,
            modifier = Modifier.size(28.dp).padding(bottom = 4.dp),
            tint = MaterialTheme.colors.error
        )
        Text(
            throwable.toUserMessage(),
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        if (onRetry != null) Chip(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ChipDefaults.secondaryChipColors(),
            label = { Text(stringResource(R.string.retry)) },
            icon = { Icon(painterResource(R.drawable.ic_refresh), null) }
        )
    }
}

@Composable
fun Throwable.toUserMessage(): String = when (this) {
    is AppException.Unauthorized -> stringResource(R.string.unauthorized_for_x, extension.name)
    is AppException.LoginRequired -> stringResource(R.string.login_required_for_x, extension.name)
    is AppException.NotSupported -> stringResource(
        R.string.x_is_not_supported_in_x, operation, extension.name
    )

    is AppException.Other -> "${extension.name}: ${cause.toUserMessage()}"
    is ExtensionNotFoundException -> stringResource(R.string.extension_not_found_x, id ?: "?")
    is ExtensionLoaderException -> stringResource(R.string.failed_to_load_extension_x, source)
    is RequiredExtensionsMissingException -> stringResource(
        R.string.required_extensions_missing_x, required.joinToString(", ")
    )

    is InvalidExtensionListException -> stringResource(R.string.invalid_extension_list_x, link)
    is PlayerException -> cause.toUserMessage()
    else -> message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "Error"
}
