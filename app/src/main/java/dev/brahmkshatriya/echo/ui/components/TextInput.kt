package dev.brahmkshatriya.echo.ui.components

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

/**
 * Wear OS has no inline keyboard in Compose: text is entered through the system
 * remote-input activity (keyboard, voice or handwriting). Returns a launcher function.
 */
@Composable
fun rememberTextInput(
    label: String,
    onResult: (String) -> Unit,
): (String?) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(KEY)?.toString() ?: return@rememberLauncherForActivityResult
        callback.value(text)
    }
    val launch: (String?) -> Unit = remember(label) {
        { initial: String? ->
            val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val remoteInput = RemoteInput.Builder(KEY)
                .setLabel(label)
                .wearableExtender {
                    setEmojisAllowed(false)
                    setInputActionType(EditorInfo.IME_ACTION_DONE)
                }
                .build()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            RemoteInputIntentHelper.putTitleExtra(intent, label)
            if (!initial.isNullOrBlank())
                RemoteInputIntentHelper.putSmartReplyContextExtra(intent, listOf(initial))
            runCatching { launcher.launch(intent) }
            Unit
        }
    }
    return launch
}

private const val KEY = "echo_text_input"
