package dev.brahmkshatriya.echo.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.extensions.db.models.UserEntity
import dev.brahmkshatriya.echo.ui.components.CoverImage
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.LoadingBox
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.ui.components.rememberTextInput
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    type: ExtensionType,
    id: String,
    back: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    LaunchedEffect(type, id) { viewModel.load(type, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var selectedForm by remember { mutableIntStateOf(-1) }
    // Values typed so far, mirrored into viewModel.inputs
    val values = remember { mutableStateOf(mapOf<String, String>()) }
    var pendingField by remember { mutableStateOf<LoginClient.InputField?>(null) }

    val launchInput = rememberTextInput(pendingField?.label ?: "") { text ->
        val field = pendingField ?: return@rememberTextInput
        values.value = values.value + (field.key to text)
        viewModel.inputs[field.key] = text
    }

    ListScreen {
        item { SectionHeader(state.extension?.name ?: stringResource(R.string.login)) }

        if (state.loading) {
            item { LoadingBox() }
            return@ListScreen
        }
        if (state.extension == null) {
            item { MessageBox(stringResource(R.string.extension_not_found_x, id)) }
            return@ListScreen
        }

        // Existing accounts
        if (users.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.accounts)) }
            items(users.size) { i ->
                val user = users[i]
                UserChip(
                    user = user,
                    selected = user.id == currentUser?.id,
                    onClick = { viewModel.setLoginUser(user) },
                    onLogout = { viewModel.logout(user) }
                )
            }
            if (currentUser != null) item {
                Chip(
                    onClick = { viewModel.setLoginUser(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    icon = { Icon(painterResource(R.drawable.ic_logout), null) },
                    label = { Text(stringResource(R.string.use_without_account)) }
                )
            }
        }

        when {
            state.submitting -> item { LoadingBox(text = stringResource(R.string.logging_in)) }

            state.supportsCustomInput && state.forms.isNotEmpty() -> {
                item { SectionHeader(stringResource(R.string.login)) }
                if (state.forms.size > 1 && selectedForm == -1) {
                    items(state.forms.size) { i ->
                        val form = state.forms[i]
                        Chip(
                            onClick = { selectedForm = i },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.secondaryChipColors(),
                            icon = { Icon(painterResource(form.icon.icon()), null) },
                            label = { Text(form.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                } else {
                    val form = state.forms[selectedForm.coerceAtLeast(0)]
                    if (state.forms.size > 1) item {
                        Chip(
                            onClick = { selectedForm = -1 },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.childChipColors(),
                            label = { Text(form.label, color = MaterialTheme.colors.primary) }
                        )
                    }
                    items(form.inputFields.size) { i ->
                        val field = form.inputFields[i]
                        val value = values.value[field.key]
                        Chip(
                            onClick = {
                                pendingField = field
                                launchInput(value)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.secondaryChipColors(),
                            icon = { Icon(painterResource(field.type.icon()), null) },
                            label = {
                                Text(
                                    field.label + if (field.isRequired) " *" else "",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    when {
                                        value.isNullOrEmpty() -> stringResource(R.string.tap_to_enter)
                                        field.type == LoginClient.InputField.Type.Password -> "•".repeat(value.length.coerceAtMost(12))
                                        else -> value
                                    },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                    item {
                        val valid = form.inputFields.all { f ->
                            val v = values.value[f.key]
                            (!f.isRequired || !v.isNullOrBlank()) &&
                                    (v.isNullOrEmpty() || f.regex?.matches(v) != false)
                        }
                        Chip(
                            onClick = {
                                form.inputFields.forEach { f ->
                                    viewModel.inputs[f.key] = values.value[f.key]
                                }
                                viewModel.submit(form) { ok -> if (ok) back() }
                            },
                            enabled = valid,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.primaryChipColors(),
                            icon = { Icon(painterResource(R.drawable.ic_login), null) },
                            label = { Text(stringResource(R.string.login)) }
                        )
                    }
                }
            }

            state.supportsWebView -> item {
                MessageBox(
                    stringResource(R.string.webview_login_unsupported),
                    icon = R.drawable.ic_info
                )
            }

            else -> item {
                MessageBox(
                    stringResource(
                        R.string.x_is_not_supported_in_x,
                        stringResource(R.string.login), state.extension?.name ?: ""
                    ),
                    icon = R.drawable.ic_info
                )
            }
        }
    }
}

private fun LoginClient.InputField.Type.icon() = when (this) {
    LoginClient.InputField.Type.Email -> R.drawable.ic_account_circle
    LoginClient.InputField.Type.Username -> R.drawable.ic_person
    LoginClient.InputField.Type.Password -> R.drawable.ic_login
    LoginClient.InputField.Type.Number -> R.drawable.ic_keyboard
    LoginClient.InputField.Type.Url -> R.drawable.ic_article
    LoginClient.InputField.Type.Misc -> R.drawable.ic_keyboard
}

@Composable
private fun UserChip(
    user: UserEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val u = user.user.getOrNull()
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) ChipDefaults.gradientBackgroundChipColors()
        else ChipDefaults.secondaryChipColors(),
        icon = {
            CoverImage(
                u?.cover, size = ChipDefaults.LargeIconSize, shape = CircleShape,
                placeholder = R.drawable.ic_person
            )
        },
        label = {
            Text(
                u?.name ?: user.id, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painterResource(R.drawable.ic_logout),
                contentDescription = stringResource(R.string.logout),
                tint = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLogout)
            )
        },
        secondaryLabel = {
            Text(
                if (selected) stringResource(R.string.logged_in) else u?.subtitle ?: "",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    )
}
