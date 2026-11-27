package dev.brahmkshatriya.echo.extensions.cache

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.FollowClient
import dev.brahmkshatriya.echo.common.clients.HideClient
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SaveClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.di.App
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getAs
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.getIf
import dev.brahmkshatriya.echo.extensions.ExtensionUtils.isClient
import dev.brahmkshatriya.echo.extensions.MediaState
import dev.brahmkshatriya.echo.extensions.builtin.offline.OfflineExtension.Companion.EXTENSION_ID
import dev.brahmkshatriya.echo.utils.CacheUtils.getFromCache
import dev.brahmkshatriya.echo.utils.CacheUtils.saveToCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Loads media items through the extension clients and keeps a small JSON copy on disk so
 * the player can still resume the last queue when the extension is temporarily failing
 * (no network, expired session, ...). Trimmed down port of the phone `Cached` object.
 */
object Cached {
    class NotFound(id: String) : Exception("Cache not found for $id")

    @PublishedApi
    internal const val FOLDER = "media"

    suspend inline fun <reified T : EchoMediaItem> getMedia(
        app: App, extensionId: String, itemId: String,
    ) = runCatching {
        val id = "media-$extensionId-$itemId-state"
        app.context.getFromCache<MediaState.Loaded<T>>(id, FOLDER) ?: throw NotFound(id)
    }

    suspend inline fun <reified T : EchoMediaItem> loadMedia(
        app: App, extension: Extension<*>, state: MediaState<T>,
    ) = coroutineScope {
        runCatching {
            val result = runCatching {
                val new = loadItem(extension, state.item).getOrThrow()
                val isSaved = async {
                    if (new.isSaveable) extension.getIf<SaveClient, Boolean> {
                        isItemSaved(new)
                    } else null
                }
                val isFollowed = async {
                    if (new.isFollowable) extension.getIf<FollowClient, Boolean> {
                        isFollowing(new)
                    } else null
                }
                val followers = async {
                    if (new.isFollowable) extension.getIf<FollowClient, Long?> {
                        getFollowersCount(new)
                    } else null
                }
                val isLiked = async {
                    if (new.isLikeable) extension.getIf<LikeClient, Boolean> {
                        isItemLiked(new)
                    } else null
                }
                val isHidden = async {
                    if (new.isHideable) extension.getIf<HideClient, Boolean> {
                        isItemHidden(new)
                    } else null
                }
                val newState = MediaState.Loaded(
                    item = new,
                    extensionId = extension.id,
                    isSaved = isSaved.await()?.getOrThrow(),
                    isFollowed = isFollowed.await()?.getOrThrow(),
                    followers = followers.await()?.getOrThrow(),
                    isLiked = isLiked.await()?.getOrThrow(),
                    isHidden = isHidden.await()?.getOrThrow(),
                    showRadio = new.isRadioSupported && extension.isClient<RadioClient>(),
                    showShare = new.isShareable && extension.isClient<ShareClient>(),
                )
                val id = "media-${extension.id}-${newState.item.id}-state"
                app.context.saveToCache(id, newState, FOLDER)
                newState
            }
            result.getOrElse {
                getMedia<T>(app, extension.id, state.item.id).getOrNull()
                    ?: throw it
            }
        }
    }

    suspend inline fun <reified T : EchoMediaItem> loadItem(
        extension: Extension<*>, item: T,
    ) = runCatching {
        when (item) {
            is Artist -> extension.getAs<ArtistClient, Artist> { loadArtist(item) }
            is Album -> extension.getAs<AlbumClient, Album> { loadAlbum(item) }
            is Playlist -> extension.getAs<PlaylistClient, Playlist> { loadPlaylist(item) }
            is Track -> extension.getAs<TrackClient, Track> { loadTrack(item, false) }
            is Radio -> extension.getAs<RadioClient, Radio> { loadRadio(item) }
        }.getOrThrow() as T
    }

    suspend fun loadStreamableMedia(
        app: App, extension: Extension<*>, track: Track, streamable: Streamable,
    ) = runCatching {
        val extId = track.extras[EXTENSION_ID] ?: extension.id
        val id = "media-${extId}-${track.id}-${streamable.id}"
        val media = extension.getAs<TrackClient, Streamable.Media> {
            loadStreamableMedia(streamable, false)
        }.getOrElse { throwable ->
            app.context.getFromCache<Streamable.Media>(id, FOLDER) ?: throw throwable
        }
        app.context.saveToCache(id, media, FOLDER)
        media
    }

    suspend fun loadTracks(extension: Extension<*>, item: EchoMediaItem) = runCatching {
        when (item) {
            is Album -> extension.getAs<AlbumClient, Feed<Track>?> { loadTracks(item) }
            is Playlist -> extension.getAs<PlaylistClient, Feed<Track>> { loadTracks(item) }
            is Radio -> extension.getAs<RadioClient, Feed<Track>> { loadTracks(item) }
            is Artist -> null
            is Track -> null
        }?.getOrThrow()
    }

    suspend fun loadFeed(extension: Extension<*>, item: EchoMediaItem) = runCatching {
        when (item) {
            is Artist -> extension.getAs<ArtistClient, Feed<Shelf>> { loadFeed(item) }
            is Album -> extension.getAs<AlbumClient, Feed<Shelf>?> { loadFeed(item) }
            is Playlist -> extension.getAs<PlaylistClient, Feed<Shelf>?> { loadFeed(item) }
            is Track -> extension.getAs<TrackClient, Feed<Shelf>?> { loadFeed(item) }
            is Radio -> null
        }?.getOrThrow()
    }
}
