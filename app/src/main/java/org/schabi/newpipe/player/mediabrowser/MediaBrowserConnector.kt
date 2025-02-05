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
import android.util.Pair
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
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
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
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import java.lang.Exception
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.util.ArrayList
import java.util.stream.Collectors
import java.util.stream.IntStream

class MediaBrowserConnector(playerService: PlayerService) : PlaybackPreparer {
    private val playerService: PlayerService
    private val sessionConnector: MediaSessionConnector
    private val mediaSession: MediaSessionCompat

    private var database: AppDatabase? = null
    private var localPlaylistManager: LocalPlaylistManager? = null
    private var remotePlaylistManager: RemotePlaylistManager? = null
    private var prepareOrPlayDisposable: Disposable? = null
    private var searchDisposable: Disposable? = null

    fun getSessionConnector(): MediaSessionConnector {
        return sessionConnector
    }

    fun release() {
        disposePrepareOrPlayCommands()
        disposeBookmarksNotifications()
        mediaSession.release()
    }

    private fun createRootMediaItem(
        mediaId: String?,
        folderName: String?,
        @DrawableRes iconResId: Int
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
        builder.setMediaId(mediaId)
        builder.setTitle(folderName)
        val resources = playerService.getResources()
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
            playerService.getResources().getString(R.string.tab_bookmarks)
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
            .setTitle(item.getName())

        when (item.getInfoType()) {
            InfoType.STREAM -> builder.setSubtitle((item as StreamInfoItem).getUploaderName())
            InfoType.PLAYLIST -> builder.setSubtitle((item as PlaylistInfoItem).getUploaderName())
            InfoType.CHANNEL -> builder.setSubtitle((item as ChannelInfoItem).getDescription())
            else -> {}
        }
        val thumbnails = item.getThumbnails()
        if (!thumbnails.isEmpty()) {
            builder.setIconUri(Uri.parse(thumbnails.get(0)!!.getUrl()))
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

    private fun buildLocalPlaylistItemMediaId(remote: Boolean, playlistId: Long): Uri.Builder {
        return buildPlaylistMediaId(if (remote) ID_REMOTE else ID_LOCAL)
            .appendPath(playlistId.toString())
    }

    private fun buildInfoItemMediaId(item: InfoItem): Uri.Builder {
        return buildMediaId()
            .appendPath(ID_INFO_ITEM)
            .appendPath(infoItemTypeToString(item.getInfoType()))
            .appendPath(item.getServiceId().toString())
            .appendQueryParameter(ID_URL, item.getUrl())
    }

    private fun createMediaIdForInfoItem(remote: Boolean, playlistId: Long): String {
        return buildLocalPlaylistItemMediaId(remote, playlistId)
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
            .setTitle(item.getName())
            .setSubtitle(item.getUploaderName())
        val thumbnails = item.getThumbnails()
        if (!thumbnails.isEmpty()) {
            builder.setIconUri(Uri.parse(thumbnails.get(0)!!.getUrl()))
        }

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun createMediaIdForPlaylistIndex(
        remote: Boolean,
        playlistId: Long,
        index: Int
    ): String {
        return buildLocalPlaylistItemMediaId(remote, playlistId)
            .appendPath(index.toString())
            .build().toString()
    }

    private fun createMediaIdForInfoItem(item: InfoItem): String {
        return buildInfoItemMediaId(item).build().toString()
    }

    fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot? {
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

    fun onLoadChildren(parentId: String): Single<MutableList<MediaBrowserCompat.MediaItem?>?>? {
        if (MainActivity.DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId))
        }

        try {
            val parentIdUri = Uri.parse(parentId)
            val path: MutableList<String> = ArrayList<String>(parentIdUri.getPathSegments())

            if (path.isEmpty()) {
                val mediaItems: MutableList<MediaBrowserCompat.MediaItem?> =
                    ArrayList<MediaBrowserCompat.MediaItem?>()
                mediaItems.add(
                    createRootMediaItem(
                        ID_BOOKMARKS,
                        playerService.getResources().getString(
                            R.string.tab_bookmarks_short
                        ),
                        R.drawable.ic_bookmark_white
                    )
                )
                mediaItems.add(
                    createRootMediaItem(
                        ID_HISTORY,
                        playerService.getResources().getString(R.string.action_history),
                        R.drawable.ic_history_white
                    )
                )
                return Single.just<MutableList<MediaBrowserCompat.MediaItem?>?>(mediaItems)
            }

            val uriType = path.get(0)
            path.removeAt(0)

            when (uriType) {
                ID_BOOKMARKS -> {
                    if (path.isEmpty()) {
                        return populateBookmarks()
                    }
                    if (path.size == 2) {
                        val localOrRemote = path.get(0)
                        val playlistId = path.get(1).toLong()
                        if (localOrRemote == ID_LOCAL) {
                            return populateLocalPlaylist(playlistId)
                        } else if (localOrRemote == ID_REMOTE) {
                            return populateRemotePlaylist(playlistId)
                        }
                    }
                    Log.w(TAG, "Unknown playlist URI: " + parentId)
                    throw parseError()
                }

                ID_HISTORY -> return populateHistory()
                else -> throw parseError()
            }
        } catch (e: ContentNotAvailableException) {
            return Single.error<MutableList<MediaBrowserCompat.MediaItem?>?>(e)
        }
    }

    private fun populateHistory(): Single<MutableList<MediaBrowserCompat.MediaItem?>?>? {
        val streamHistory = getDatabase().streamHistoryDAO()
        val history = streamHistory.getHistory().firstOrError()
        return history.map<MutableList<MediaBrowserCompat.MediaItem?>?>(
            Function { items: MutableList<StreamHistoryEntry?>? ->
                items!!.stream()
                    .map<MediaBrowserCompat.MediaItem?> { streamHistoryEntry: StreamHistoryEntry? ->
                        this.createHistoryMediaItem(
                            streamHistoryEntry!!
                        )
                    }
                    .collect(Collectors.toList())
            }
        )
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
            .setIconUri(Uri.parse(streamHistoryEntry.streamEntity.thumbnailUrl))

        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun getDatabase(): AppDatabase {
        if (database == null) {
            database = NewPipeDatabase.getInstance(playerService)
        }
        return database!!
    }

    private fun getPlaylists(): Flowable<MutableList<PlaylistLocalItem?>?>? {
        if (localPlaylistManager == null) {
            localPlaylistManager = LocalPlaylistManager(getDatabase())
        }
        if (remotePlaylistManager == null) {
            remotePlaylistManager = RemotePlaylistManager(getDatabase())
        }
        return MergedPlaylistManager.getMergedOrderedPlaylists(
            localPlaylistManager,
            remotePlaylistManager
        )
    }

    var bookmarksNotificationsDisposable: Disposable? = null

    init {
        this.playerService = playerService
        mediaSession = MediaSessionCompat(playerService, TAG)
        sessionConnector = MediaSessionConnector(mediaSession)
        sessionConnector.setMetadataDeduplicationEnabled(true)
        sessionConnector.setPlaybackPreparer(this)
        playerService.setSessionToken(mediaSession.getSessionToken())

        setupBookmarksNotifications()
    }

    private fun setupBookmarksNotifications() {
        bookmarksNotificationsDisposable = getPlaylists()!!.subscribe(
            Consumer { playlistMetadataEntries: MutableList<PlaylistLocalItem?>? ->
                playerService.notifyChildrenChanged(
                    ID_BOOKMARKS
                )
            }
        )
    }

    private fun disposeBookmarksNotifications() {
        if (bookmarksNotificationsDisposable != null) {
            bookmarksNotificationsDisposable!!.dispose()
            bookmarksNotificationsDisposable = null
        }
    }

    // Suppress Sonar warning replace list collection by Stream.toList call, as this method is only
    // available in Android API 34 and not currently available with desugaring
    private fun populateBookmarks(): Single<MutableList<MediaBrowserCompat.MediaItem?>?>? {
        val playlists = getPlaylists()!!.firstOrError()
        return playlists.map<MutableList<MediaBrowserCompat.MediaItem?>?>(
            Function { playlist: MutableList<PlaylistLocalItem?>? ->
                playlist!!.stream()
                    .map<MediaBrowserCompat.MediaItem?> { playlist: PlaylistLocalItem? ->
                        this.createPlaylistMediaItem(
                            playlist!!
                        )
                    }
                    .collect(Collectors.toList())
            }
        )
    }

    private fun populateLocalPlaylist(playlistId: Long): Single<MutableList<MediaBrowserCompat.MediaItem?>?>? {
        val playlist = localPlaylistManager!!.getPlaylistStreams(playlistId).firstOrError()
        return playlist.map<MutableList<MediaBrowserCompat.MediaItem?>?>(
            Function { items: MutableList<PlaylistStreamEntry>? ->
                val results: MutableList<MediaBrowserCompat.MediaItem?> =
                    ArrayList<MediaBrowserCompat.MediaItem?>()
                var index = 0
                for (item in items!!) {
                    results.add(createLocalPlaylistStreamMediaItem(playlistId, item, index))
                    ++index
                }
                results
            }
        )
    }

    private fun getRemotePlaylist(playlistId: Long): Single<MutableList<Pair<StreamInfoItem?, Int?>?>?>? {
        val playlistFlow = remotePlaylistManager!!.getPlaylist(playlistId).firstOrError()
        return playlistFlow.flatMap<MutableList<Pair<StreamInfoItem?, Int?>?>?>(
            Function { item: MutableList<PlaylistRemoteEntity>? ->
                val playlist = item!!.get(0)
                val playlistInfo = ExtractorHelper.getPlaylistInfo(
                    playlist.getServiceId(),
                    playlist.getUrl(), false
                )
                playlistInfo.flatMap<MutableList<Pair<StreamInfoItem?, Int?>?>?>(
                    Function { info: PlaylistInfo? ->
                        val infoItemsPage = info!!.getRelatedItems()
                        if (!info.getErrors().isEmpty()) {
                            val errors: MutableList<Throwable?> = ArrayList<Throwable?>(info.getErrors())

                            errors.removeIf { obj: Throwable? ->
                                ContentNotSupportedException::class.java.isInstance(
                                    obj
                                )
                            }

                            if (!errors.isEmpty()) {
                                return@flatMap Single.error<MutableList<Pair<StreamInfoItem?, Int?>?>?>(
                                    errors.get(0)
                                )
                            }
                        }
                        Single.just<MutableList<Pair<StreamInfoItem?, Int?>?>?>(
                            IntStream.range(0, infoItemsPage.size)
                                .mapToObj<Pair<StreamInfoItem?, Int?>?>(
                                    java.util.function.IntFunction { i: kotlin.Int ->
                                        android.util.Pair.create<StreamInfoItem?, kotlin.Int?>(
                                            infoItemsPage.get(i),
                                            i
                                        )
                                    }
                                )
                                .toList()!!
                        )
                    }
                )
            }
        )
    }

    private fun populateRemotePlaylist(playlistId: Long): Single<MutableList<MediaBrowserCompat.MediaItem?>?>? {
        return getRemotePlaylist(playlistId)!!.map<MutableList<MediaBrowserCompat.MediaItem?>?>(
            Function { items: MutableList<Pair<StreamInfoItem?, Int?>?>? ->
                items!!.stream()
                    .map<MediaBrowserCompat.MediaItem?> { pair: Pair<StreamInfoItem?, Int?>? ->
                        createRemotePlaylistStreamMediaItem(
                            playlistId,
                            pair!!.first!!,
                            pair.second!!
                        )
                    }.toList()
            }
        )
    }

    private fun playbackError(@StringRes resId: Int, code: Int) {
        playerService.stopForImmediateReusing()
        sessionConnector.setCustomErrorMessage(playerService.getString(resId), code)
    }

    private fun playbackError(errorInfo: ErrorInfo) {
        playbackError(errorInfo.messageStringId, PlaybackStateCompat.ERROR_CODE_APP_ERROR)
    }

    private fun extractLocalPlayQueue(playlistId: Long, index: Int): Single<PlayQueue?>? {
        return localPlaylistManager!!.getPlaylistStreams(playlistId)
            .firstOrError()
            .map<PlayQueue?>(
                Function { items: MutableList<PlaylistStreamEntry?>? ->
                    val infoItems = items!!.stream()
                        .map<StreamInfoItem?> { obj: PlaylistStreamEntry? -> obj!!.toStreamInfoItem() }
                        .collect(Collectors.toList())
                    SinglePlayQueue(infoItems, index)
                }
            )
    }

    private fun extractRemotePlayQueue(playlistId: Long, index: Int): Single<PlayQueue?>? {
        return getRemotePlaylist(playlistId)!!.map<PlayQueue?>(
            Function { items: MutableList<Pair<StreamInfoItem?, Int?>?>? ->
                val infoItems = items!!.stream()
                    .map<StreamInfoItem?> { pair: Pair<StreamInfoItem?, Int?>? -> pair!!.first }
                    .toList()
                SinglePlayQueue(infoItems, index)
            }
        )
    }

    private fun extractPlayQueueFromMediaId(mediaId: String?): Single<PlayQueue?>? {
        try {
            val mediaIdUri = Uri.parse(mediaId)
            val path: MutableList<String> = ArrayList<String>(mediaIdUri.getPathSegments())

            if (path.isEmpty()) {
                throw parseError()
            }

            val uriType: String? = path.get(0)
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
        } catch (e: ContentNotAvailableException) {
            return Single.error<PlayQueue?>(e)
        }
    }

    @Throws(ContentNotAvailableException::class)
    private fun extractPlayQueueFromPlaylistMediaId(
        path: MutableList<String>,
        url: String?
    ): Single<PlayQueue?>? {
        if (path.isEmpty()) {
            throw parseError()
        }

        val playlistType = path.get(0)
        path.removeAt(0)

        when (playlistType) {
            ID_LOCAL, ID_REMOTE -> {
                if (path.size != 2) {
                    throw parseError()
                }
                val playlistId = path.get(0).toLong()
                val index = path.get(1).toInt()
                return if (playlistType == ID_LOCAL)
                    extractLocalPlayQueue(playlistId, index)
                else
                    extractRemotePlayQueue(playlistId, index)
            }

            ID_URL -> {
                if (path.size != 1) {
                    throw parseError()
                }

                val serviceId = path.get(0).toInt()
                return ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                    .map<PlayQueue?>(Function { info: PlaylistInfo? -> PlaylistPlayQueue(info) })
            }

            else -> throw parseError()
        }
    }

    @Throws(ContentNotAvailableException::class)
    private fun extractPlayQueueFromHistoryMediaId(
        path: MutableList<String>
    ): Single<PlayQueue?>? {
        if (path.size != 1) {
            throw parseError()
        }

        val streamId = path.get(0).toLong()
        return getDatabase().streamHistoryDAO().getHistory()
            .firstOrError()
            .map<PlayQueue?>(
                Function { items: MutableList<StreamHistoryEntry?>? ->
                    val infoItems = items!!.stream()
                        .filter { it: StreamHistoryEntry? -> it!!.streamId == streamId }
                        .map<StreamInfoItem?> { obj: StreamHistoryEntry? -> obj!!.toStreamInfoItem() }
                        .collect(Collectors.toList())
                    SinglePlayQueue(infoItems, 0)
                }
            )
    }

    override fun getSupportedPrepareActions(): Long {
        return PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
    }

    private fun disposePrepareOrPlayCommands() {
        if (prepareOrPlayDisposable != null) {
            prepareOrPlayDisposable!!.dispose()
            prepareOrPlayDisposable = null
        }
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
        prepareOrPlayDisposable = extractPlayQueueFromMediaId(mediaId)!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                Consumer { playQueue: PlayQueue? ->
                    sessionConnector.setCustomErrorMessage(null)
                    NavigationHelper.playOnBackgroundPlayer(
                        playerService, playQueue,
                        playWhenReady
                    )
                },
                Consumer { throwable: Throwable? ->
                    playbackError(
                        ErrorInfo(
                            throwable, UserAction.PLAY_STREAM,
                            "Failed playback of media ID [" + mediaId + "]: "
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

    private fun searchMusicBySongTitle(query: String?): Single<SearchInfo?> {
        val serviceId = ServiceHelper.getSelectedServiceId(playerService)
        return ExtractorHelper.searchFor(
            serviceId, query,
            ArrayList<String?>(), ""
        )
    }

    private fun mediaItemsFromInfoItemList(result: ListInfo<InfoItem?>): SingleSource<MutableList<MediaBrowserCompat.MediaItem?>?> {
        val exceptions = result.getErrors()
        if (!exceptions.isEmpty() &&
            !(
                exceptions.size == 1 &&
                    exceptions.get(0) is NothingFoundException
                )
        ) {
            return Single.error<MutableList<MediaBrowserCompat.MediaItem?>?>(exceptions.get(0))
        }

        val items = result.getRelatedItems()
        if (items.isEmpty()) {
            return Single.error<MutableList<MediaBrowserCompat.MediaItem?>?>(NullPointerException("Got no search results."))
        }
        try {
            val results = items.stream()
                .filter { item: InfoItem? -> item!!.getInfoType() == InfoType.STREAM || item.getInfoType() == InfoType.PLAYLIST || item.getInfoType() == InfoType.CHANNEL }
                .map<MediaBrowserCompat.MediaItem?> { item: InfoItem? ->
                    this.createInfoItemMediaItem(
                        item!!
                    )
                }.toList()
            return Single.just<MutableList<MediaBrowserCompat.MediaItem?>?>(results)
        } catch (e: Exception) {
            return Single.error<MutableList<MediaBrowserCompat.MediaItem?>?>(e)
        }
    }

    private fun handleSearchError(throwable: Throwable?) {
        Log.e(TAG, "Search error: " + throwable)
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
        cb: ResultReceiver?
    ): Boolean {
        return false
    }

    fun onSearch(
        query: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem?>?>
    ) {
        result.detach()
        if (searchDisposable != null) {
            searchDisposable!!.dispose()
        }
        searchDisposable = searchMusicBySongTitle(query)
            .flatMap<MutableList<MediaBrowserCompat.MediaItem?>?>(
                Function { result: SearchInfo? ->
                    this.mediaItemsFromInfoItemList(
                        result!!
                    )
                }
            )
            .subscribeOn(Schedulers.io())
            .subscribe(
                Consumer { result: MutableList<MediaBrowserCompat.MediaItem?>? ->
                    result.sendResult(
                        result
                    )
                },
                Consumer { throwable: Throwable? -> this.handleSearchError(throwable) }
            )
    }

    companion object {
        private val TAG: String = MediaBrowserConnector::class.java.getSimpleName()

        private val ID_AUTHORITY = BuildConfig.APPLICATION_ID
        private val ID_ROOT = "//" + ID_AUTHORITY
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
                else -> throw IllegalStateException("Unexpected value: " + type)
            }
        }

        private fun infoItemTypeFromString(type: String): InfoType {
            return when (type) {
                ID_STREAM -> InfoType.STREAM
                ID_PLAYLIST -> InfoType.PLAYLIST
                ID_CHANNEL -> InfoType.CHANNEL
                else -> throw IllegalStateException("Unexpected value: " + type)
            }
        }

        private fun parseError(): ContentNotAvailableException {
            return ContentNotAvailableException("Failed to parse media ID")
        }

        @Throws(ContentNotAvailableException::class)
        private fun extractPlayQueueFromInfoItemMediaId(
            path: MutableList<String>,
            url: String?
        ): Single<PlayQueue?>? {
            if (path.size != 2) {
                throw parseError()
            }
            val infoItemType = infoItemTypeFromString(path.get(0))
            val serviceId = path.get(1).toInt()
            return when (infoItemType) {
                InfoType.STREAM -> ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .map<PlayQueue?>(Function { info: StreamInfo? -> SinglePlayQueue(info) })

                InfoType.PLAYLIST -> ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                    .map<PlayQueue?>(Function { info: PlaylistInfo? -> PlaylistPlayQueue(info) })

                InfoType.CHANNEL -> ExtractorHelper.getChannelInfo(serviceId, url, false)
                    .map<PlayQueue?>(
                        Function { info: ChannelInfo? ->
                            val playableTab = info!!.getTabs()
                                .stream()
                                .filter { tab: ListLinkHandler? -> ChannelTabHelper.isStreamsTab(tab) }
                                .findFirst()
                            if (playableTab.isPresent()) {
                                return@map ChannelTabPlayQueue(
                                    serviceId,
                                    ListLinkHandler(playableTab.get())
                                )
                            } else {
                                throw ContentNotAvailableException("No streams tab found")
                            }
                        }
                    )

                else -> throw parseError()
            }
        }
    }
}
