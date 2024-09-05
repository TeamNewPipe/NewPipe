package org.schabi.newpipe.player.mediabrowser

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.PlaybackPreparer
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleSource
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import java.util.stream.Collectors

class MediaBrowserConnector(private val playerService: PlayerService) : PlaybackPreparer {
    private val mediaSession = MediaSessionCompat(playerService, TAG)
    val sessionConnector = MediaSessionConnector(mediaSession).apply {
        setMetadataDeduplicationEnabled(true)
        setPlaybackPreparer(this@MediaBrowserConnector)
    }

    private val database: AppDatabase by lazy { NewPipeDatabase.getInstance(playerService) }
    private val localPlaylistManager: LocalPlaylistManager by lazy { LocalPlaylistManager(database) }
    private val remotePlaylistManager: RemotePlaylistManager by lazy {
        RemotePlaylistManager(database)
    }
    private val playlists: Flowable<List<PlaylistLocalItem?>>
        get() {
            return MergedPlaylistManager.getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
        }

    private var prepareOrPlayDisposable: Disposable? = null
    private var searchDisposable: Disposable? = null
    private var bookmarksNotificationsDisposable: Disposable? = null

    init {
        playerService.sessionToken = mediaSession.sessionToken
        setupBookmarksNotifications()
    }

    fun release() {
        disposePrepareOrPlayCommands()
        disposeBookmarksNotifications()
        mediaSession.release()
    }

    private fun createRootMediaItem(
        mediaId: String?,
        folderName: String,
        @DrawableRes iconResId: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(mediaId)
        builder.setTitle(folderName)
        val resources = playerService.resources
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
            playerService.getString(R.string.app_name)
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createPlaylistMediaItem(playlist: PlaylistLocalItem): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val remote = playlist is PlaylistRemoteEntity
        builder.setMediaId(createMediaIdForInfoItem(remote, playlist.uid))
            .setTitle(playlist.orderingName)
            .setIconUri(Uri.parse(playlist.thumbnailUrl))

        val extras = Bundle()
        extras.putString(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
            playerService.resources.getString(R.string.tab_bookmarks)
        )
        builder.setExtras(extras)
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createInfoItemMediaItem(item: InfoItem): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForInfoItem(item))
            .setTitle(item.name)

        when (item.infoType) {
            InfoType.STREAM -> builder.setSubtitle((item as StreamInfoItem).uploaderName)
            InfoType.PLAYLIST -> builder.setSubtitle((item as PlaylistInfoItem).uploaderName)
            InfoType.CHANNEL -> builder.setSubtitle((item as ChannelInfoItem).description)
            else -> {}
        }
        item.thumbnails.firstOrNull()?.let {
            builder.setIconUri(Uri.parse(it.url))
        }
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun buildMediaId() = Uri.Builder().authority(ID_AUTHORITY)

    private fun buildPlaylistMediaId(playlistType: String) =
        buildMediaId()
            .appendPath(ID_BOOKMARKS)
            .appendPath(playlistType)

    private fun buildLocalPlaylistItemMediaId(
        remote: Boolean,
        playlistId: Long,
    ) = buildPlaylistMediaId(if (remote) ID_REMOTE else ID_LOCAL)
        .appendPath(playlistId.toString())

    private fun buildInfoItemMediaId(item: InfoItem) =
        buildMediaId()
            .appendPath(ID_INFO_ITEM)
            .appendPath(infoItemTypeToString(item.infoType))
            .appendPath(item.serviceId.toString())
            .appendQueryParameter(ID_URL, item.url)

    private fun createMediaIdForInfoItem(
        remote: Boolean,
        playlistId: Long,
    ) = buildLocalPlaylistItemMediaId(remote, playlistId)
        .build()
        .toString()

    private fun createLocalPlaylistStreamMediaItem(
        playlistId: Long,
        item: PlaylistStreamEntry,
        index: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(createMediaIdForPlaylistIndex(false, playlistId, index))
            .setTitle(item.streamEntity.title)
            .setSubtitle(item.streamEntity.uploader)
            .setIconUri(Uri.parse(item.streamEntity.thumbnailUrl))

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
        item.thumbnails.firstOrNull()?.let {
            builder.setIconUri(Uri.parse(it.url))
        }

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createMediaIdForPlaylistIndex(
        remote: Boolean,
        playlistId: Long,
        index: Int,
    ) = buildLocalPlaylistItemMediaId(remote, playlistId)
        .appendPath(index.toString())
        .build()
        .toString()

    private fun createMediaIdForInfoItem(item: InfoItem) = buildInfoItemMediaId(item).build().toString()

    fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot {
        if (MainActivity.DEBUG) {
            Log.d(
                TAG,
                String.format(
                    "MediaBrowserService.onGetRoot(%s, %s, %s)",
                    clientPackageName, clientUid, rootHints
                )
            )
        }

        val extras = Bundle()
        extras.putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true
        )
        return MediaBrowserServiceCompat.BrowserRoot(ID_ROOT, extras)
    }

    fun onLoadChildren(parentId: String): Single<List<MediaBrowserCompat.MediaItem>> {
        if (MainActivity.DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId))
        }

        try {
            val parentIdUri = Uri.parse(parentId)
            val path = ArrayList(parentIdUri.pathSegments)

            if (path.isEmpty()) {
                val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
                mediaItems.add(
                    createRootMediaItem(
                        ID_BOOKMARKS,
                        playerService.resources.getString(
                            R.string.tab_bookmarks_short
                        ),
                        R.drawable.ic_bookmark_white
                    )
                )
                mediaItems.add(
                    createRootMediaItem(
                        ID_HISTORY,
                        playerService.resources.getString(R.string.action_history),
                        R.drawable.ic_history_white
                    )
                )
                return Single.just(mediaItems)
            }

            val uriType = path[0]
            path.removeAt(0)

            when (uriType) {
                ID_BOOKMARKS -> {
                    if (path.isEmpty()) {
                        return populateBookmarks()
                    }
                    if (path.size == 2) {
                        val localOrRemote = path[0]
                        val playlistId = path[1].toLong()
                        if (localOrRemote == ID_LOCAL) {
                            return populateLocalPlaylist(playlistId)
                        } else if (localOrRemote == ID_REMOTE) {
                            return populateRemotePlaylist(playlistId)
                        }
                    }
                    Log.w(TAG, "Unknown playlist URI: $parentId")
                    throw parseError()
                }

                ID_HISTORY -> return populateHistory()
                else -> throw parseError()
            }
        } catch (e: ContentNotAvailableException) {
            return Single.error(e)
        }
    }

    private fun populateHistory(): Single<List<MediaBrowserCompat.MediaItem>> =
        database
            .streamHistoryDAO()
            .history
            .firstOrError()
            .map { items -> items.map(::createHistoryMediaItem) }

    private fun createHistoryMediaItem(streamHistoryEntry: StreamHistoryEntry): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        val mediaId = buildMediaId()
            .appendPath(ID_HISTORY)
            .appendPath(streamHistoryEntry.streamId.toString())
            .build().toString()
        builder.setMediaId(mediaId)
            .setTitle(streamHistoryEntry.streamEntity.title)
            .setSubtitle(streamHistoryEntry.streamEntity.uploader)
            .setIconUri(Uri.parse(streamHistoryEntry.streamEntity.thumbnailUrl))

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun setupBookmarksNotifications() {
        bookmarksNotificationsDisposable =
            playlists.subscribe { _ ->
                playerService.notifyChildrenChanged(ID_BOOKMARKS)
            }
    }

    private fun disposeBookmarksNotifications() {
        bookmarksNotificationsDisposable?.dispose()
    }

    // Suppress Sonar warning replace list collection by Stream.toList call, as this method is only
    // available in Android API 34 and not currently available with desugaring
    private fun populateBookmarks() =
        playlists.firstOrError().map { playlist ->
            playlist.filterNotNull().map { createPlaylistMediaItem(it) }
        }

    private fun populateLocalPlaylist(playlistId: Long): Single<List<MediaBrowserCompat.MediaItem>> =
        localPlaylistManager
            .getPlaylistStreams(playlistId)
            .firstOrError()
            .map { items: List<PlaylistStreamEntry> ->
                val results: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
                for ((index, item) in items.withIndex()) {
                    results.add(createLocalPlaylistStreamMediaItem(playlistId, item, index))
                }
                results
            }

    private fun getRemotePlaylist(playlistId: Long): Single<List<Pair<StreamInfoItem, Int>>> =
        remotePlaylistManager
            .getPlaylist(playlistId)
            .firstOrError()
            .map { playlistEntities ->
                val playlist = playlistEntities[0]
                ExtractorHelper
                    .getPlaylistInfo(playlist.serviceId, playlist.url, false)
                    .map { info ->
                        handlePlaylistInfoErrors(info)
                        info.relatedItems.withIndex().map { (index, item) -> item to index }
                    }
            }.flatMap { it }

    private fun handlePlaylistInfoErrors(info: PlaylistInfo) {
        val nonContentNotSupportedErrors = info.errors.filterNot { it is ContentNotSupportedException }
        if (nonContentNotSupportedErrors.isNotEmpty()) {
            throw nonContentNotSupportedErrors.first()
        }
    }

    private fun populateRemotePlaylist(playlistId: Long): Single<List<MediaBrowserCompat.MediaItem>> =
        getRemotePlaylist(playlistId).map { items ->
            items.map { pair ->
                createRemotePlaylistStreamMediaItem(
                    playlistId,
                    pair.first,
                    pair.second,
                )
            }
        }

    private fun playbackError(@StringRes resId: Int, code: Int) {
        playerService.stopForImmediateReusing()
        sessionConnector.setCustomErrorMessage(playerService.getString(resId), code)
    }

    private fun playbackError(errorInfo: ErrorInfo) {
        playbackError(errorInfo.messageStringId, PlaybackStateCompat.ERROR_CODE_APP_ERROR)
    }

    private fun extractLocalPlayQueue(playlistId: Long, index: Int): Single<PlayQueue> {
        return localPlaylistManager.getPlaylistStreams(playlistId)
            .firstOrError()
            .map { items: List<PlaylistStreamEntry> ->
                val infoItems = items.stream()
                    .map { obj: PlaylistStreamEntry -> obj.toStreamInfoItem() }
                    .collect(Collectors.toList())
                SinglePlayQueue(infoItems, index)
            }
    }

    private fun extractRemotePlayQueue(playlistId: Long, index: Int): Single<PlayQueue> {
        return getRemotePlaylist(playlistId).map { items ->
            val infoItems = items.map { (item, _) -> item }
            SinglePlayQueue(infoItems, index)
        }
    }

    private fun extractPlayQueueFromMediaId(mediaId: String): Single<PlayQueue> {
        try {
            val mediaIdUri = Uri.parse(mediaId)
            val path = ArrayList(mediaIdUri.pathSegments)

            if (path.isEmpty()) {
                throw parseError()
            }

            val uriType = path[0]
            path.removeAt(0)

            return when (uriType) {
                ID_BOOKMARKS -> extractPlayQueueFromPlaylistMediaId(
                    path,
                    mediaIdUri.getQueryParameter(ID_URL)
                )

                ID_HISTORY -> extractPlayQueueFromHistoryMediaId(path)
                ID_INFO_ITEM -> extractPlayQueueFromInfoItemMediaId(
                    path,
                    mediaIdUri.getQueryParameter(ID_URL)
                )

                else -> throw parseError()
            }
        } catch (error: ContentNotAvailableException) {
            return Single.error(error)
        }
    }

    @Throws(ContentNotAvailableException::class)
    private fun extractPlayQueueFromPlaylistMediaId(
        mediaIdSegments: ArrayList<String>,
        url: String?,
    ): Single<PlayQueue> {
        if (mediaIdSegments.isEmpty()) {
            throw parseError()
        }

        val playlistType = mediaIdSegments.first()
        mediaIdSegments.removeAt(0)

        when (playlistType) {
            ID_LOCAL, ID_REMOTE -> {
                if (mediaIdSegments.size != 2) {
                    throw parseError()
                }
                val playlistId = mediaIdSegments[0].toLong()
                val index = mediaIdSegments[1].toInt()
                return if (playlistType == ID_LOCAL) {
                    extractLocalPlayQueue(playlistId, index)
                } else {
                    extractRemotePlayQueue(playlistId, index)
                }
            }

            ID_URL -> {
                if (mediaIdSegments.size != 1) {
                    throw parseError()
                }

                val serviceId = mediaIdSegments[0].toInt()
                return ExtractorHelper
                    .getPlaylistInfo(serviceId, url, false)
                    .map(::PlaylistPlayQueue)
            }

            else -> throw parseError()
        }
    }

    @Throws(ContentNotAvailableException::class)
    private fun extractPlayQueueFromHistoryMediaId(
        path: List<String>
    ): Single<PlayQueue> {
        if (path.size != 1) {
            throw parseError()
        }

        val streamId = path[0].toLong()
        return database
            .streamHistoryDAO()
            .history
            .firstOrError()
            .map { items ->
                val infoItems =
                    items
                        .filter { streamHistoryEntry -> streamHistoryEntry.streamId == streamId }
                        .map { streamHistoryEntry -> streamHistoryEntry.toStreamInfoItem() }
                SinglePlayQueue(infoItems, 0)
            }
    }

    override fun getSupportedPrepareActions() = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    private fun disposePrepareOrPlayCommands() {
        prepareOrPlayDisposable?.dispose()
    }

    override fun onPrepare(playWhenReady: Boolean) {
        disposePrepareOrPlayCommands()
        // No need to prepare
    }

    override fun onPrepareFromMediaId(
        mediaId: String,
        playWhenReady: Boolean,
        extras: Bundle?
    ) {
        if (MainActivity.DEBUG) {
            Log.d(
                TAG,
                String.format(
                    "MediaBrowserConnector.onPrepareFromMediaId(%s, %s, %s)",
                    mediaId, playWhenReady, extras
                )
            )
        }

        disposePrepareOrPlayCommands()
        prepareOrPlayDisposable = extractPlayQueueFromMediaId(mediaId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { playQueue ->
                    sessionConnector.setCustomErrorMessage(null)
                    NavigationHelper.playOnBackgroundPlayer(
                        playerService, playQueue,
                        playWhenReady
                    )
                },
                { throwable ->
                    playbackError(
                        ErrorInfo(
                            throwable, UserAction.PLAY_STREAM,
                            "Failed playback of media ID [$mediaId]: "
                        )
                    )
                }
            )
    }

    override fun onPrepareFromSearch(
        query: String,
        playWhenReady: Boolean,
        extras: Bundle?
    ) {
        disposePrepareOrPlayCommands()
        playbackError(
            R.string.content_not_supported,
            PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED
        )
    }

    private fun searchMusicBySongTitle(query: String): Single<SearchInfo> {
        val serviceId = ServiceHelper.getSelectedServiceId(playerService)
        return ExtractorHelper.searchFor(
            serviceId, query,
            ArrayList(), ""
        )
    }

    private fun mediaItemsFromInfoItemList(result: ListInfo<InfoItem>): SingleSource<List<MediaBrowserCompat.MediaItem>> {
        result.errors
            .takeIf { exceptions ->
                exceptions.isNotEmpty() &&
                    !(
                        exceptions.size == 1 &&
                            exceptions.first() is NothingFoundException
                        )
            }?.let { exceptions ->
                return Single.error(exceptions.first())
            }

        val items = result.relatedItems
        if (items.isEmpty()) {
            return Single.error(NullPointerException("Got no search results."))
        }
        try {
            val results =
                items
                    .filter { item ->
                        item.infoType == InfoType.STREAM ||
                            item.infoType == InfoType.PLAYLIST ||
                            item.infoType == InfoType.CHANNEL
                    }.map { item -> this.createInfoItemMediaItem(item) }

            return Single.just(results)
        } catch (error: Exception) {
            return Single.error(error)
        }
    }

    private fun handleSearchError(throwable: Throwable) {
        Log.e(TAG, "Search error: $throwable")
        disposePrepareOrPlayCommands()
        playbackError(R.string.content_not_supported, PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED)
    }

    override fun onPrepareFromUri(
        uri: Uri,
        playWhenReady: Boolean,
        extras: Bundle?
    ) {
        disposePrepareOrPlayCommands()
        playbackError(R.string.content_not_supported, PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED)
    }

    override fun onCommand(
        player: Player,
        command: String,
        extras: Bundle?,
        cb: ResultReceiver?,
    ) = false

    fun onSearch(
        query: String,
        result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>,
    ) {
        result.detach()
        searchDisposable?.dispose()
        searchDisposable =
            searchMusicBySongTitle(query)
                .flatMap(::mediaItemsFromInfoItemList)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { mediaItemsResult -> result.sendResult(mediaItemsResult) },
                    { throwable -> this.handleSearchError(throwable) },
                )
    }

    companion object {
        private val TAG: String = MediaBrowserConnector::class.java.simpleName

        private const val ID_AUTHORITY = BuildConfig.APPLICATION_ID
        private const val ID_ROOT = "//$ID_AUTHORITY"
        private const val ID_BOOKMARKS = "playlists"
        private const val ID_HISTORY = "history"
        private const val ID_INFO_ITEM = "item"

        private const val ID_LOCAL = "local"
        private const val ID_REMOTE = "remote"
        private const val ID_URL = "url"
        private const val ID_STREAM = "stream"
        private const val ID_PLAYLIST = "playlist"
        private const val ID_CHANNEL = "channel"

        private fun infoItemTypeToString(type: InfoType): String {
            return when (type) {
                InfoType.STREAM -> ID_STREAM
                InfoType.PLAYLIST -> ID_PLAYLIST
                InfoType.CHANNEL -> ID_CHANNEL
                else -> throw IllegalStateException("Unexpected value: $type")
            }
        }

        private fun infoItemTypeFromString(type: String): InfoType {
            return when (type) {
                ID_STREAM -> InfoType.STREAM
                ID_PLAYLIST -> InfoType.PLAYLIST
                ID_CHANNEL -> InfoType.CHANNEL
                else -> throw IllegalStateException("Unexpected value: $type")
            }
        }

        private fun parseError(): ContentNotAvailableException {
            return ContentNotAvailableException("Failed to parse media ID")
        }

        @Throws(ContentNotAvailableException::class)
        private fun extractPlayQueueFromInfoItemMediaId(
            path: List<String>,
            url: String?
        ): Single<PlayQueue> {
            if (path.size != 2) {
                throw parseError()
            }
            val infoItemType = infoItemTypeFromString(path[0])
            val serviceId = path[1].toInt()
            return when (infoItemType) {
                InfoType.STREAM -> ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .map(::SinglePlayQueue)

                InfoType.PLAYLIST -> ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                    .map(::PlaylistPlayQueue)

                InfoType.CHANNEL -> ExtractorHelper.getChannelInfo(serviceId, url, false)
                    .map { info ->
                        val playableTab = info.tabs
                            .stream()
                            .filter { tab -> ChannelTabHelper.isStreamsTab(tab) }
                            .findFirst()
                        if (playableTab.isPresent) {
                            return@map ChannelTabPlayQueue(
                                serviceId,
                                ListLinkHandler(playableTab.get())
                            )
                        } else {
                            throw ContentNotAvailableException("No streams tab found")
                        }
                    }

                else -> throw parseError()
            }
        }
    }
}
