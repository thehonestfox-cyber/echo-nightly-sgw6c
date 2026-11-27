package dev.brahmkshatriya.echo.ui.components

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper

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
            // Same extras `androidx.wear.input.wearableExtender` (wear-input 1.2) would add:
            // hide the emoji-only option and show a "done" action instead of "send".
            val extras = Bundle().apply {
                putBoolean("android.support.wearable.input.extra.DISALLOW_EMOJI", true)
                putInt("android.support.wearable.input.extra.INPUT_ACTION_TYPE", 2) // done
            }
            val remoteInput = RemoteInput.Builder(KEY)
                .setLabel(label)
                .addExtras(extras)
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
