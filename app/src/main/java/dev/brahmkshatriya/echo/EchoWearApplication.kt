package dev.brahmkshatriya.echo

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.di.DI
import dev.brahmkshatriya.echo.extensions.ExtensionLoader
import dev.brahmkshatriya.echo.utils.ContextUtils.cleanupTempApks
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class EchoWearApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@EchoWearApplication)
            modules(DI.appModule)
        }
        // Warm up the singletons so the player service and the UI share the same instances.
        get<App>()
        get<ExtensionLoader>()
        cleanupTempApks()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
