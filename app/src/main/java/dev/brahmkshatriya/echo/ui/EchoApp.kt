package dev.brahmkshatriya.echo.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.ui.components.MessageBox
import dev.brahmkshatriya.echo.ui.extensions.AddExtensionScreen
import dev.brahmkshatriya.echo.ui.extensions.ExtensionNavigation
import dev.brahmkshatriya.echo.ui.extensions.ExtensionScreen
import dev.brahmkshatriya.echo.ui.extensions.ExtensionsNavigation
import dev.brahmkshatriya.echo.ui.extensions.ExtensionsScreen
import dev.brahmkshatriya.echo.ui.extensions.LoginScreen
import dev.brahmkshatriya.echo.ui.feed.FeedNavigation
import dev.brahmkshatriya.echo.ui.feed.FeedScreen
import dev.brahmkshatriya.echo.ui.feed.FeedViewModel
import dev.brahmkshatriya.echo.ui.home.HomeNavigation
import dev.brahmkshatriya.echo.ui.home.HomeScreen
import dev.brahmkshatriya.echo.ui.media.MediaNavigation
import dev.brahmkshatriya.echo.ui.media.MediaScreen
import dev.brahmkshatriya.echo.ui.player.PlayerScreen
import dev.brahmkshatriya.echo.ui.player.QueueScreen
import dev.brahmkshatriya.echo.ui.settings.AboutScreen
import dev.brahmkshatriya.echo.ui.settings.ExtensionSettingsScreen
import dev.brahmkshatriya.echo.ui.settings.SettingsScreen
import dev.brahmkshatriya.echo.ui.theme.EchoTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@Composable
fun EchoApp() {
    EchoTheme {
        val navController = rememberSwipeDismissableNavController()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            EchoNavHost(navController)
            MessageOverlay()
        }
    }
}

private data class Toast(val text: String, val isError: Boolean)

/** Snackbar-like overlay for `App.messageFlow` / `App.throwFlow`. */
@Composable
private fun MessageOverlay(app: App = koinInject()) {
    var toast by remember { mutableStateOf<Toast?>(null) }
    val errorPrefix = stringResource(R.string.error)
    // Buffer the flows so that emitters are never suspended while a toast is on screen.
    val queue = remember { Channel<Toast>(Channel.BUFFERED) }
    LaunchedEffect(Unit) {
        merge(
            app.messageFlow.map { Toast(it.message, false) },
            app.throwFlow.map { Toast(it.toUserMessagePlain(errorPrefix), true) }
        ).collect { queue.trySend(it) }
    }
    LaunchedEffect(Unit) {
        for (t in queue) {
            toast = t
            delay(if (t.isError) 4000 else 2500)
            toast = null
            delay(150)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            val t = toast ?: return@AnimatedVisibility
            Text(
                t.text,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .background(
                        if (t.isError) MaterialTheme.colors.error.copy(alpha = 0.9f)
                        else MaterialTheme.colors.surface.copy(alpha = 0.95f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                color = if (t.isError) MaterialTheme.colors.onError else MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Throwable.toUserMessagePlain(prefix: String): String {
    val msg = message?.takeIf { it.isNotBlank() }
        ?: cause?.message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: prefix
    return msg
}

@Composable
private fun EchoNavHost(navController: NavHostController) {
    val openItem: (String, EchoMediaItem) -> Unit = { extId, item ->
        navController.navigate(Routes.media(extId, item))
    }
    val openFeed: (String, Feed<Shelf>) -> Unit =
        { title, feed -> navController.navigate(Routes.customFeed(title, feed)) }
    val openPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } }
    val openExtensions = { navController.navigate(Routes.EXTENSIONS) }
    val back: () -> Unit = { navController.popBackStack() }

    val feedNavigation = FeedNavigation(openItem, openFeed, openExtensions)
    val mediaNavigation = MediaNavigation(openItem, openFeed, openPlayer)

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                HomeNavigation(
                    openHomeFeed = { navController.navigate(Routes.feed("home")) },
                    openSearch = { navController.navigate(Routes.feed("search")) },
                    openLibrary = { navController.navigate(Routes.feed("library")) },
                    openPlayer = openPlayer,
                    openExtensions = openExtensions,
                    openSettings = { navController.navigate(Routes.SETTINGS) },
                )
            )
        }
        composable(
            Routes.FEED,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { entry ->
            val type = when (entry.arguments?.getString("type")) {
                "search" -> FeedViewModel.Type.Search
                "library" -> FeedViewModel.Type.Library
                else -> FeedViewModel.Type.Home
            }
            FeedScreen(type = type, navigation = feedNavigation)
        }
        composable(
            Routes.CUSTOM_FEED,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { entry ->
            val payload = NavStore.get<Routes.CustomFeed>(entry.arguments?.getString("key"))
            if (payload == null) MissingPayload(back)
            else FeedScreen(
                type = FeedViewModel.Type.Custom,
                navigation = feedNavigation,
                title = payload.title,
                customFeed = payload.feed
            )
        }
        composable(
            Routes.MEDIA,
            arguments = listOf(
                navArgument("extId") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType },
            )
        ) { entry ->
            val extId = Uri.decode(entry.arguments?.getString("extId").orEmpty())
            val item = NavStore.get<EchoMediaItem>(entry.arguments?.getString("key"))
            if (item == null) MissingPayload(back)
            else MediaScreen(extensionId = extId, item = item, navigation = mediaNavigation)
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                openQueue = { navController.navigate(Routes.QUEUE) },
                openTrack = openItem
            )
        }
        composable(Routes.QUEUE) { QueueScreen(openTrack = openItem) }
        composable(Routes.EXTENSIONS) {
            ExtensionsScreen(
                ExtensionsNavigation(
                    openExtension = { type, id -> navController.navigate(Routes.extension(type, id)) },
                    openAdd = { navController.navigate(Routes.ADD_EXTENSION) },
                    back = back
                )
            )
        }
        composable(
            Routes.EXTENSION,
            arguments = extensionArgs()
        ) { entry ->
            val (type, id) = entry.extensionArgs()
            ExtensionScreen(
                type = type, id = id,
                navigation = ExtensionNavigation(
                    openLogin = { t, i -> navController.navigate(Routes.login(t, i)) },
                    openSettings = { t, i -> navController.navigate(Routes.extensionSettings(t, i)) },
                    back = back
                )
            )
        }
        composable(Routes.ADD_EXTENSION) { AddExtensionScreen(back = back) }
        composable(Routes.LOGIN, arguments = extensionArgs()) { entry ->
            val (type, id) = entry.extensionArgs()
            LoginScreen(type = type, id = id, back = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(openAbout = { navController.navigate(Routes.ABOUT) })
        }
        composable(Routes.EXTENSION_SETTINGS, arguments = extensionArgs()) { entry ->
            val (type, id) = entry.extensionArgs()
            ExtensionSettingsScreen(type = type, id = id)
        }
        composable(Routes.ABOUT) { AboutScreen() }
    }
}

private fun extensionArgs() = listOf(
    navArgument("type") { type = NavType.StringType },
    navArgument("id") { type = NavType.StringType },
)

private fun NavBackStackEntry.extensionArgs(): Pair<ExtensionType, String> {
    val type = runCatching {
        ExtensionType.valueOf(arguments?.getString("type").orEmpty())
    }.getOrDefault(ExtensionType.MUSIC)
    val id = Uri.decode(arguments?.getString("id").orEmpty())
    return type to id
}

@Composable
private fun MissingPayload(back: () -> Unit) {
    LaunchedEffect(Unit) { back() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MessageBox(stringResource(R.string.error))
    }
}
