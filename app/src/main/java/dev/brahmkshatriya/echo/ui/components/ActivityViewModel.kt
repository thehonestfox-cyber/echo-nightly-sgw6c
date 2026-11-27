package dev.brahmkshatriya.echo.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * Activity-scoped Koin view model.
 *
 * Replacement for `org.koin.compose.viewmodel.koinActivityViewModel`, which in Koin 4.1.0 casts
 * the `LocalActivity` composition local itself (instead of its current value) to
 * `ComponentActivity` and therefore always throws a `ClassCastException`
 * (InsertKoinIO/koin#2227). The activity is resolved from [LocalContext] instead.
 */
@Composable
inline fun <reified T : ViewModel> activityViewModel(
    qualifier: Qualifier? = null,
    key: String? = null,
    noinline parameters: ParametersDefinition? = null,
): T {
    val activity = LocalContext.current.findComponentActivity()
        ?: error("activityViewModel() must be called from a composition hosted by a ComponentActivity")
    return koinViewModel(
        qualifier = qualifier,
        viewModelStoreOwner = activity,
        key = key,
        parameters = parameters,
    )
}

fun Context.findComponentActivity(): ComponentActivity? {
    var context: Context? = this
    while (context != null) {
        if (context is ComponentActivity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}
