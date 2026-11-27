package dev.brahmkshatriya.echo.di

import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.playback.PlayerService
import dev.brahmkshatriya.echo.playback.PlayerState
import dev.brahmkshatriya.echo.ui.extensions.ExtensionsViewModel
import dev.brahmkshatriya.echo.ui.extensions.LoginViewModel
import dev.brahmkshatriya.echo.ui.feed.FeedViewModel
import dev.brahmkshatriya.echo.ui.media.MediaViewModel
import dev.brahmkshatriya.echo.ui.player.PlayerViewModel
import dev.brahmkshatriya.echo.ui.settings.SettingsViewModel
import dev.brahmkshatriya.echo.utils.ContextUtils.getSettings
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

object DI {
    val appModule = module {
        single { App(androidApplication(), androidApplication().getSettings()) }
        single { PlayerService.getCache(androidApplication(), get<App>().settings) }
        single { ExtensionLoader(get()) }
        single { PlayerState() }

        viewModel { PlayerViewModel(get(), get(), get()) }
        viewModel { FeedViewModel(get(), get()) }
        viewModel { MediaViewModel(get(), get()) }
        viewModel { ExtensionsViewModel(get(), get()) }
        viewModel { LoginViewModel(get(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
    }
}
