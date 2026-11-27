package dev.brahmkshatriya.echo.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.get
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App settings (shared preferences used by [dev.brahmkshatriya.echo.playback.PlayerService])
 * plus the settings exposed by each extension through `SettingsProvider`.
 */
class SettingsViewModel(
    val app: App,
    val extensionLoader: ExtensionLoader,
) : ViewModel() {

    val prefs: SharedPreferences get() = app.settings

    /** Bumps every time a preference changes so Compose re-reads the values. */
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _revision.value++
    }

    init {
        app.settings.registerOnSharedPreferenceChangeListener(listener)
    }

    fun putBoolean(key: String, value: Boolean) = app.settings.edit().putBoolean(key, value).apply()
    fun putInt(key: String, value: Int) = app.settings.edit().putInt(key, value).apply()
    fun putString(key: String, value: String?) = app.settings.edit().putString(key, value).apply()

    data class ExtensionSettingsState(
        val extension: Extension<*>? = null,
        val items: List<Setting> = emptyList(),
        val loading: Boolean = true,
        val error: Throwable? = null,
    )

    private val _extensionSettings = MutableStateFlow(ExtensionSettingsState())
    val extensionSettings = _extensionSettings.asStateFlow()

    private var extensionPrefs: SharedPreferences? = null
    private val extensionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _revision.value++
    }

    fun loadExtensionSettings(extension: Extension<*>) {
        extensionPrefs?.unregisterOnSharedPreferenceChangeListener(extensionListener)
        val p = extension.prefs(app.context)
        extensionPrefs = p
        p.registerOnSharedPreferenceChangeListener(extensionListener)
        viewModelScope.launch {
            _extensionSettings.value = ExtensionSettingsState(extension, loading = true)
            val items = extension.get { getSettingItems() }.getOrElse {
                _extensionSettings.value = ExtensionSettingsState(extension, loading = false, error = it)
                return@launch
            }
            _extensionSettings.value = ExtensionSettingsState(extension, items, loading = false)
        }
    }

    fun extensionPrefs(): SharedPreferences? = extensionPrefs

    fun runOnClick(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { app.throwFlow.emit(it) }
        }
    }

    override fun onCleared() {
        app.settings.unregisterOnSharedPreferenceChangeListener(listener)
        extensionPrefs?.unregisterOnSharedPreferenceChangeListener(extensionListener)
        super.onCleared()
    }
}
