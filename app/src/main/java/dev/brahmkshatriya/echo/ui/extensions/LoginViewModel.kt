package dev.brahmkshatriya.echo.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.get
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.db.models.CurrentUser
import dev.brahmkshatriya.echo.extensions.db.models.UserEntity
import dev.brahmkshatriya.echo.extensions.db.models.UserEntity.Companion.toCurrentUser
import dev.brahmkshatriya.echo.extensions.db.models.UserEntity.Companion.toEntity
import dev.brahmkshatriya.echo.extensions.exceptions.AppException.Companion.toAppException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Account management for one extension. Only `LoginClient.CustomInput` (username /
 * password / token forms) can be completed on the watch: there is no WebView on Wear OS.
 */
class LoginViewModel(
    val app: App,
    val extensionLoader: ExtensionLoader,
) : ViewModel() {

    data class LoginState(
        val extension: Extension<*>? = null,
        val supportsCustomInput: Boolean = false,
        val supportsWebView: Boolean = false,
        val forms: List<LoginClient.Form> = emptyList(),
        val loading: Boolean = true,
        val submitting: Boolean = false,
    )

    private val userDao = extensionLoader.db.userDao()

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _selection = MutableStateFlow<Pair<ExtensionType, String>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val users = _selection.flatMapLatest { sel ->
        if (sel == null) flowOf(emptyList())
        else userDao.observeAllUsers(sel.first, sel.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUser = _selection.flatMapLatest { sel ->
        if (sel == null) flowOf(null)
        else userDao.observeCurrentUser().combine(users) { current, all ->
            val curr = current.find { it.type == sel.first && it.extId == sel.second }
            all.find { it.id == curr?.userId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inputs = mutableMapOf<String, String?>()

    fun load(type: ExtensionType, id: String) {
        if (_selection.value == type to id && _state.value.extension != null) return
        _selection.value = type to id
        inputs.clear()
        viewModelScope.launch {
            _state.value = LoginState(loading = true)
            val extension = extensionLoader.getFlow(type).value.find { it.id == id }
            if (extension == null) {
                _state.value = LoginState(loading = false)
                return@launch
            }
            val info = extension.get {
                Triple(
                    this is LoginClient.CustomInput,
                    this is LoginClient.WebView,
                    (this as? LoginClient.CustomInput)?.forms.orEmpty()
                )
            }.getOrElse {
                app.throwFlow.emit(it)
                Triple(false, false, emptyList())
            }
            _state.value = LoginState(
                extension = extension,
                supportsCustomInput = info.first,
                supportsWebView = info.second,
                forms = info.third,
                loading = false,
            )
        }
    }

    fun submit(form: LoginClient.Form, onDone: (Boolean) -> Unit) {
        val extension = _state.value.extension ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            val result = extension.getAs<LoginClient.CustomInput, List<User>> {
                onLogin(form.key, inputs.toMap())
            }
            val success = afterLogin(extension, result)
            _state.value = _state.value.copy(submitting = false)
            onDone(success)
        }
    }

    private suspend fun afterLogin(extension: Extension<*>, result: Result<List<User>>): Boolean {
        val users = result.getOrElse {
            app.throwFlow.emit(it.toAppException(extension))
            return false
        }
        if (users.isEmpty()) {
            app.messageFlow.emit(Message(app.context.getString(R.string.no_user_found)))
            return false
        }
        val entities = users.map { it.toEntity(extension.type, extension.id) }
        userDao.insertUsers(entities)
        userDao.setCurrentUser(entities.first().toCurrentUser())
        app.messageFlow.emit(Message(app.context.getString(R.string.logged_in_as_x, users.first().name)))
        return true
    }

    fun setLoginUser(user: UserEntity?) {
        val sel = _selection.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userDao.setCurrentUser(CurrentUser(sel.first, sel.second, user?.id))
        }
    }

    fun logout(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val sel = _selection.value
            if (sel != null && currentUser.value?.id == user.id)
                userDao.setCurrentUser(CurrentUser(sel.first, sel.second, null))
            userDao.deleteUser(user)
        }
    }
}
