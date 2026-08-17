package org.schabi.newpipe.player.mediabrowser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT
import androidx.media.MediaBrowserServiceCompat.Result
import androidx.media.utils.MediaConstants
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * This class is used to cleanly separate the Service implementation (in
 * [org.schabi.newpipe.player.PlayerService]) and the media browser implementation (in this file).
 *
 * @param notifyChildrenChanged takes the parent id of the children that changed
 */
class MediaBrowserImpl(
    private val context: Context,
    // parentId
    notifyChildrenChanged: Consumer<String>
) {
    private val packageValidator = PackageValidator(context)
    private val database = NewPipeDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // this will listen to changes in the bookmarks until this MediaBrowserImpl is dispose()d
        scope.launch {
            getMergedPlaylists().collect {
                notifyChildrenChanged.accept(ID_BOOKMARKS)
            }
        }
    }

    //region Cleanup
    fun dispose() {
        scope.cancel()
    }
    //endregion

    //region onGetRoot
    fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot? {
        if (DEBUG) {
            Log.d(TAG, "onGetRoot($clientPackageName, $clientUid, $rootHints)")
        }

        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // this is a caller we can't trust (see PackageValidator's rules taken from uamp)
            return null
        }

        if (rootHints?.getBoolean(EXTRA_RECENT, false) == true) {
            // the system is asking for a root to do media resumption, but we can't handle that yet,
            // see https://developer.android.com/media/implement/surfaces/mobile#mediabrowserservice_implementation
            return null
        }

        val extras = Bundle()
        extras.putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED,
            true
        )
        return MediaBrowserServiceCompat.BrowserRoot(ID_ROOT, extras)
    }
    //endregion

    //region onLoadChildren
    fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (DEBUG) {
            Log.d(TAG, "onLoadChildren($parentId)")
        }

        result.detach() // allows sendResult() to happen later
        scope.launch {
            try {
                result.sendResult(onLoadChildren(parentId))
            } catch (throwable: Throwable) {
                // null indicates an error, see the docs of MediaSessionCompat.onSearch()
                result.sendResult(null)
                Log.e(TAG, "onLoadChildren error for parentId=$parentId: $throwable")
            }
        }
    }

    private suspend fun onLoadChildren(parentId: String): List<MediaBrowserCompat.MediaItem> = withContext(Dispatchers.IO) {
        try {
            val parentIdUri = parentId.toUri()
            val path = ArrayList(parentIdUri.pathSegments)

            if (path.isEmpty()) {
                return@withContext listOf(
                    createRootMediaItem(
                        ID_BOOKMARKS,
                        context.resources.getString(R.string.tab_bookmarks_short),
                        R.drawable.ic_bookmark_white
                    ),
                    createRootMediaItem(
                        ID_HISTORY,
                        context.resources.getString(R.string.action_history),
                        R.drawable.ic_history_white
                    )
                )
            }

            when (path.removeAt(0)) {
                ID_BOOKMARKS -> {
                    if (path.isEmpty()) {
                        return@withContext populateBookmarks()
                    }
                    if (path.size == 2) {
                        val localOrRemote = path[0]
                        val playlistId = path[1].toLong()
                        if (localOrRemote == ID_LOCAL) {
                            return@withContext populateLocalPlaylist(playlistId)
                        } else if (localOrRemote == ID_REMOTE) {
                            return@withContext populateRemotePlaylist(playlistId)
                        }
                    }
                    Log.w(TAG, "Unknown playlist URI: $parentId")
                    throw parseError(parentId)
                }

                ID_HISTORY -> return@withContext populateHistory()

                else -> throw parseError(parentId)
            }
        } catch (e: ContentNotAvailableException) {
            throw e
        }
    }

    private fun createRootMediaItem(
        mediaId: String?,
        folderName: String?,
        @DrawableRes iconResId: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(mediaId)
        builder.setTitle(folderName)
        val resources = context.resources
        builder.setIconUri(
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(iconResId))
                .appendPath(resources.getResourceTypeName(iconResId))
                .appendPath(resources.getResourceEntryName(iconResId))
                .build()
        )

        val extras = Bundle()
        extras.putString(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
            context.getString(R.string.app_name)
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createPlaylistMediaItem(playlist: PlaylistLocalItem): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder
            .setMediaId(createMediaIdForInfoItem(playlist is PlaylistRemoteEntity, playlist.uid))
            .setTitle(playlist.orderingName)
            .setIconUri(imageUriOrNullIfDisabled(playlist.thumbnailUrl))

        val extras = Bundle()
        extras.putString(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
            context.resources.getString(R.string.tab_bookmarks)
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createInfoItemMediaItem(item: InfoItem): MediaBrowserCompat.MediaItem? {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForInfoItem(item))
            .setTitle(item.name)

        when (item.infoType) {
            InfoType.STREAM -> builder.setSubtitle((item as StreamInfoItem).uploaderName)
            InfoType.PLAYLIST -> builder.setSubtitle((item as PlaylistInfoItem).uploaderName)
            InfoType.CHANNEL -> builder.setSubtitle((item as ChannelInfoItem).description)
            else -> return null
        }

        ImageStrategy.choosePreferredImage(item.thumbnails)?.let {
            builder.setIconUri(imageUriOrNullIfDisabled(it))
        }

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun buildMediaId(): Uri.Builder {
        return Uri.Builder().authority(ID_AUTHORITY)
    }

    private fun buildPlaylistMediaId(playlistType: String?): Uri.Builder {
        return buildMediaId()
            .appendPath(ID_BOOKMARKS)
            .appendPath(playlistType)
    }

    private fun buildLocalPlaylistItemMediaId(isRemote: Boolean, playlistId: Long): Uri.Builder {
        return buildPlaylistMediaId(if (isRemote) ID_REMOTE else ID_LOCAL)
            .appendPath(playlistId.toString())
    }

    private fun buildInfoItemMediaId(item: InfoItem): Uri.Builder {
        return buildMediaId()
            .appendPath(ID_INFO_ITEM)
            .appendPath(infoItemTypeToString(item.infoType))
            .appendPath(item.serviceId.toString())
            .appendQueryParameter(ID_URL, item.url)
    }

    private fun createMediaIdForInfoItem(isRemote: Boolean, playlistId: Long): String {
        return buildLocalPlaylistItemMediaId(isRemote, playlistId)
            .build().toString()
    }

    private fun createLocalPlaylistStreamMediaItem(
        playlistId: Long,
        item: PlaylistStreamEntry,
        index: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForPlaylistIndex(false, playlistId, index))
            .setTitle(item.streamEntity.title)
            .setSubtitle(item.streamEntity.uploader)
            .setIconUri(imageUriOrNullIfDisabled(item.streamEntity.thumbnailUrl))

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createRemotePlaylistStreamMediaItem(
        playlistId: Long,
        item: StreamInfoItem,
        index: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForPlaylistIndex(true, playlistId, index))
            .setTitle(item.name)
            .setSubtitle(item.uploaderName)

        ImageStrategy.choosePreferredImage(item.thumbnails)?.let {
            builder.setIconUri(imageUriOrNullIfDisabled(it))
        }

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createMediaIdForPlaylistIndex(
        isRemote: Boolean,
        playlistId: Long,
        index: Int
    ): String {
        return buildLocalPlaylistItemMediaId(isRemote, playlistId)
            .appendPath(index.toString())
            .build().toString()
    }

    private fun createMediaIdForInfoItem(item: InfoItem): String {
        return buildInfoItemMediaId(item).build().toString()
    }

    private suspend fun populateHistory(): List<MediaBrowserCompat.MediaItem> {
        val history = database.streamHistoryDAO().getHistory().first()
        return history.map { this.createHistoryMediaItem(it) }
    }

    private fun createHistoryMediaItem(streamHistoryEntry: StreamHistoryEntry): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val mediaId = buildMediaId()
            .appendPath(ID_HISTORY)
            .appendPath(streamHistoryEntry.streamId.toString())
            .build().toString()
        builder.setMediaId(mediaId)
            .setTitle(streamHistoryEntry.streamEntity.title)
            .setSubtitle(streamHistoryEntry.streamEntity.uploader)
            .setIconUri(imageUriOrNullIfDisabled(streamHistoryEntry.streamEntity.thumbnailUrl))

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun getMergedPlaylists(): Flow<List<PlaylistLocalItem>> {
        return MergedPlaylistManager.getMergedOrderedPlaylists(
            LocalPlaylistManager(database),
            RemotePlaylistManager(database)
        )
    }

    private suspend fun populateBookmarks(): List<MediaBrowserCompat.MediaItem> {
        val playlists = getMergedPlaylists().first()
        return playlists.map { this.createPlaylistMediaItem(it) }
    }

    private suspend fun populateLocalPlaylist(playlistId: Long): List<MediaBrowserCompat.MediaItem> {
        val playlist = LocalPlaylistManager(database).getPlaylistStreams(playlistId).first()
        return playlist.mapIndexed { index, item ->
            createLocalPlaylistStreamMediaItem(playlistId, item, index)
        }
    }

    private suspend fun populateRemotePlaylist(playlistId: Long): List<MediaBrowserCompat.MediaItem> {
        val it = RemotePlaylistManager(database).getPlaylist(playlistId).first()
        val info = ExtractorHelper.getPlaylistInfo(it.serviceId, it.url!!, false)
        // ignore it.errors, i.e. ignore errors about specific items, since there would
        // be no way to show the error properly in Android Auto anyway
        return info.relatedItems.mapIndexed { index, item ->
            createRemotePlaylistStreamMediaItem(playlistId, item, index)
        }
    }
    //endregion

    //region Search
    fun onSearch(
        query: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        if (DEBUG) {
            Log.d(TAG, "onSearch($query)")
        }

        result.detach() // allows sendResult() to happen later
        scope.launch {
            try {
                val searchInfo = searchMusicBySongTitle(query)
                // ignore it.errors, i.e. ignore errors about specific items, since there would
                // be no way to show the error properly in Android Auto anyway
                val items = searchInfo.relatedItems.mapNotNull(this@MediaBrowserImpl::createInfoItemMediaItem)
                result.sendResult(items)
            } catch (throwable: Throwable) {
                // null indicates an error, see the docs of MediaSessionCompat.onSearch()
                result.sendResult(null)
                Log.e(TAG, "Search error for query=\"$query\": $throwable")
            }
        }
    }

    private suspend fun searchMusicBySongTitle(query: String?): SearchInfo {
        val serviceId = ServiceHelper.getSelectedServiceId(context)
        return ExtractorHelper.searchFor(serviceId, query ?: "", listOf(), "")
    }
    //endregion

    companion object {
        private val TAG: String = MediaBrowserImpl::class.java.getSimpleName()

        fun imageUriOrNullIfDisabled(url: String?): Uri? {
            return if (ImageStrategy.shouldLoadImages()) {
                url?.toUri()
            } else {
                null
            }
        }
    }
}
