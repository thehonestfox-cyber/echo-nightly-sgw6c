package dev.brahmkshatriya.echo.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.ImportType
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getExtensionOrThrow
import dev.brahmkshatriya.echo.extensions.InstallationUtils.installFile
import dev.brahmkshatriya.echo.extensions.InstallationUtils.uninstallFile
import dev.brahmkshatriya.echo.extensions.db.models.ExtensionEntity
import dev.brahmkshatriya.echo.extensions.exceptions.AppException.Companion.toAppException
import dev.brahmkshatriya.echo.utils.ExtensionUpdater
import dev.brahmkshatriya.echo.utils.ExtensionUpdater.ExtensionAssetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manage extensions on the watch: enable/disable, select the active music extension,
 * install from an extension list link (or a v.gd code), update and uninstall.
 */
class ExtensionsViewModel(
    val app: App,
    val extensionLoader: ExtensionLoader,
) : ViewModel() {

    val all = extensionLoader.all
    val music = extensionLoader.music
    val current = extensionLoader.current

    sealed class AddState {
        data object Idle : AddState()
        data object Loading : AddState()
        data class List(val list: kotlin.collections.List<Item>) : AddState()
        data class Installing(val item: ExtensionAssetResponse, val done: Int, val total: Int) :
            AddState()

        data class Done(val installed: Int, val failed: Int) : AddState()
        data class Error(val throwable: Throwable) : AddState()
    }

    data class Item(val item: ExtensionAssetResponse, val isInstalled: Boolean)

    private val _addState = MutableStateFlow<AddState>(AddState.Idle)
    val addState = _addState.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()

    private val extensionDao = extensionLoader.db.extensionDao()

    fun setExtensionEnabled(type: ExtensionType, id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionDao.setExtension(ExtensionEntity(id, type, enabled))
        }
    }

    fun selectExtension(extension: MusicExtension) {
        extensionLoader.setupMusicExtension(extension, true)
    }

    fun selectExtension(id: String) {
        viewModelScope.launch {
            val ext = runCatching { extensionLoader.music.getExtensionOrThrow(id) }.getOrElse {
                app.throwFlow.emit(it)
                return@launch
            }
            extensionLoader.setupMusicExtension(ext, true)
        }
    }

    private suspend fun message(msg: String) = app.messageFlow.emit(Message(msg))

    fun loadExtensionList(link: String) {
        viewModelScope.launch {
            _addState.value = AddState.Loading
            val list = runCatching { ExtensionUpdater.getExtensionList(link) }.getOrElse {
                app.throwFlow.emit(it)
                _addState.value = AddState.Error(it)
                return@launch
            }
            val installed = extensionLoader.all.value.map { it.id }
            _addState.value = AddState.List(list.map { Item(it, it.id in installed) })
        }
    }

    fun resetAddState() {
        _addState.value = AddState.Idle
    }

    fun install(items: List<ExtensionAssetResponse>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var installed = 0
            var failed = 0
            items.forEachIndexed { index, item ->
                _addState.value = AddState.Installing(item, index, items.size)
                val result = runCatching {
                    val url = ExtensionUpdater.getUpdateFileUrl("", item.updateUrl).getOrThrow()
                        ?: throw Exception("No download url for ${item.name}")
                    val file = ExtensionUpdater.downloadFile(app.context, url).getOrThrow()
                    installFromFile(file)
                }
                result.onSuccess { installed++ }.onFailure {
                    failed++
                    app.throwFlow.emit(it)
                }
            }
            if (installed > 0) message(app.context.getString(R.string.extension_installed_successfully))
            _addState.value = AddState.Done(installed, failed)
        }
    }

    /** Parses the APK to find the extension id and moves it into the extensions folder. */
    private suspend fun installFromFile(file: File) = withContext(Dispatchers.IO) {
        val metadata = extensionLoader.parser.parseManifest(file, ImportType.File)
        installFile(app.context, extensionLoader.fileIgnoreFlow, metadata.id, file)
    }

    fun update(extension: Extension<*>) {
        viewModelScope.launch {
            withBusy(extension.id) {
                val updateUrl = extension.metadata.updateUrl
                if (updateUrl.isNullOrEmpty()) {
                    message(app.context.getString(R.string.no_update_available_for_x, extension.name))
                    return@withBusy
                }
                val url = ExtensionUpdater.getUpdateFileUrl(extension.version, updateUrl)
                    .recoverCatching { throw it.toAppException(extension) }
                    .getOrElse {
                        app.throwFlow.emit(it)
                        return@withBusy
                    }
                if (url == null) {
                    message(app.context.getString(R.string.no_update_available_for_x, extension.name))
                    return@withBusy
                }
                message(app.context.getString(R.string.downloading_update_for_x, extension.name))
                runCatching {
                    val file = ExtensionUpdater.downloadFile(app.context, url).getOrThrow()
                    installFromFile(file)
                }.onSuccess {
                    message(app.context.getString(R.string.extension_installed_successfully))
                }.onFailure {
                    app.throwFlow.emit(it.toAppException(extension))
                }
            }
        }
    }

    fun uninstall(extension: Extension<*>) {
        viewModelScope.launch {
            withBusy(extension.id) {
                if (extension.metadata.importType != ImportType.File) {
                    message(app.context.getString(R.string.cannot_uninstall_x, extension.name))
                    return@withBusy
                }
                runCatching {
                    uninstallFile(extensionLoader.fileIgnoreFlow, extension.metadata.path)
                }.onSuccess {
                    message(app.context.getString(R.string.extension_uninstalled_successfully))
                }.onFailure { app.throwFlow.emit(it) }
            }
        }
    }

    private suspend fun withBusy(id: String, block: suspend () -> Unit) {
        _busy.value = _busy.value + id
        try {
            block()
        } finally {
            _busy.value = _busy.value - id
        }
    }

    companion object {
    }
}
