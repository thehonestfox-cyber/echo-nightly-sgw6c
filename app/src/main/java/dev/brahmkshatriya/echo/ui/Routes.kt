package dev.brahmkshatriya.echo.ui

import android.net.Uri
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf

object Routes {
    const val HOME = "home"
    const val FEED = "feed/{type}"
    const val CUSTOM_FEED = "custom_feed/{key}"
    const val MEDIA = "media/{extId}/{key}"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val EXTENSIONS = "extensions"
    const val EXTENSION = "extension/{type}/{id}"
    const val ADD_EXTENSION = "add_extension"
    const val LOGIN = "login/{type}/{id}"
    const val SETTINGS = "settings"
    const val EXTENSION_SETTINGS = "extension_settings/{type}/{id}"
    const val ABOUT = "about"

    fun feed(type: String) = "feed/$type"

    data class CustomFeed(val title: String, val feed: Feed<Shelf>)

    fun customFeed(title: String, feed: Feed<Shelf>) =
        "custom_feed/${NavStore.put(CustomFeed(title, feed))}"

    fun media(extId: String, item: EchoMediaItem) =
        "media/${Uri.encode(extId)}/${NavStore.put(item)}"

    fun extension(type: ExtensionType, id: String) = "extension/${type.name}/${Uri.encode(id)}"
    fun login(type: ExtensionType, id: String) = "login/${type.name}/${Uri.encode(id)}"
    fun extensionSettings(type: ExtensionType, id: String) =
        "extension_settings/${type.name}/${Uri.encode(id)}"

}
