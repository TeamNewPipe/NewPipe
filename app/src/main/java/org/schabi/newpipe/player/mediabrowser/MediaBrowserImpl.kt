package org.schabi.newpipe.player.mediabrowser

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionError
import androidx.media3.session.legacy.MediaConstants
import androidx.preference.PreferenceManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.schabi.newpipe.MainActivity.DEBUG
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
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ServiceSearchSupport
import org.schabi.newpipe.util.image.ExtractorImageCompat
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
    private val notifyChildrenChanged: Consumer<String>
) {
    private val packageValidator = PackageValidator(context)
    private val database = NewPipeDatabase.getInstance(context)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var disposables = CompositeDisposable()
    private var profileDisposables = CompositeDisposable()
    private val profilePreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) {
                bindProfileObservers()
                notifyProfileCollectionsChanged()
            }
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(profilePreferenceListener)
        bindProfileObservers()
    }

    private fun bindProfileObservers() {
        profileDisposables.clear()
        val profileId = ProfileManager.getActiveProfileId(context)
        profileDisposables.add(
            getMergedPlaylists(profileId).subscribe {
                notifyChildrenChanged.accept(ID_BOOKMARKS)
            }
        )
        profileDisposables.add(
            database.streamHistoryDAO().getHistoryForProfile(profileId).subscribe(
                { notifyHistoryCollectionsChanged() },
                { throwable -> Log.e(TAG, "History observation failed", throwable) }
            )
        )
    }

    private fun notifyHistoryCollectionsChanged() {
        notifyChildrenChanged.accept(ID_CONTINUE)
        notifyChildrenChanged.accept(ID_RECENT)
        notifyChildrenChanged.accept(ID_HISTORY)
    }

    private fun notifyProfileCollectionsChanged() {
        notifyChildrenChanged.accept(ID_BOOKMARKS)
        notifyHistoryCollectionsChanged()
    }

    //region Cleanup
    fun dispose() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(profilePreferenceListener)
        profileDisposables.dispose()
        disposables.dispose()
    }
    //endregion

    //region Library root
    fun onGetLibraryRoot(
        browser: ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (DEBUG) {
            Log.d(TAG, "onGetLibraryRoot($browser, $params)")
        }

        if (!packageValidator.isKnownCaller(browser.packageName, browser.uid)) {
            return immediateResult(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        }

        val recent = params?.isRecent == true
        val rootId = if (recent) ID_RECENT else ID_ROOT
        val root = createRootMediaItem(
            rootId,
            context.getString(R.string.app_name),
            R.drawable.ic_headset
        )
        val resultParams = LibraryParams.Builder()
            .setRecent(recent)
            .setExtras(createRootExtras(recent))
            .build()

        return immediateResult(LibraryResult.ofItem(root, resultParams))
    }

    internal fun createRootExtras(recent: Boolean = false): Bundle = Bundle().apply {
        putBoolean(
            MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED,
            ServiceSearchSupport.supportsSearch(ServiceHelper.getSelectedServiceId(context))
        )
        if (recent) {
            putBoolean("android.service.media.extra.RECENT", true)
        }
        putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
        )
        putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
    }
    //endregion

    //region onLoadChildren
    fun onGetChildren(
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (DEBUG) {
            Log.d(TAG, "onGetChildren($parentId, $page, $pageSize)")
        }

        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        disposables.add(
            loadChildren(parentId)
                .timeout(CarBrowsePolicy.LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { items ->
                        future.set(LibraryResult.ofItemList(paginate(items, page, pageSize), params))
                    },
                    { throwable ->
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                        Log.e(TAG, "onLoadChildren error for parentId=$parentId: $throwable")
                    }
                )
        )
        return future
    }

    private fun loadChildren(parentId: String): Single<List<MediaItem>> {
        try {
            val parentIdUri = parentId.toUri()
            val path = ArrayList(parentIdUri.pathSegments)

            if (path.isEmpty()) {
                return Single.just(
                    listOf(
                        createRootMediaItem(
                            ID_CONTINUE,
                            context.resources.getString(R.string.continue_listening),
                            R.drawable.ic_history_white
                        ),
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
                )
            }

            when (path.removeAt(0)) {
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
                    throw parseError(parentId)
                }

                ID_HISTORY -> return populateHistory()

                ID_CONTINUE -> return populateContinueListening()

                ID_RECENT -> return populateResumption()

                else -> throw parseError(parentId)
            }
        } catch (e: ContentNotAvailableException) {
            return Single.error(e)
        }
    }

    private fun createRootMediaItem(
        mediaId: String,
        folderName: String,
        @DrawableRes iconResId: Int
    ): MediaItem {
        val extras = Bundle().apply {
            putString(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                context.getString(R.string.app_name)
            )
        }
        return libraryItem(
            mediaId,
            MediaMetadata.Builder()
                .setTitle(folderName)
                .setArtworkUri(resourceUri(iconResId))
                .setExtras(extras),
            browsable = true
        )
    }

    private fun createPlaylistMediaItem(playlist: PlaylistLocalItem): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(playlist.orderingName)
            .setArtworkUri(imageUriOrNullIfDisabled(playlist.thumbnailUrl))

        metadata.setExtras(
            Bundle().apply {
                putString(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                    context.resources.getString(R.string.tab_bookmarks)
                )
            }
        )
        return libraryItem(
            createMediaIdForInfoItem(playlist is PlaylistRemoteEntity, playlist.uid),
            metadata,
            browsable = true
        )
    }

    private fun createInfoItemMediaItem(item: InfoItem): MediaItem? {
        val metadata = MediaMetadata.Builder().setTitle(item.name)

        when (item.infoType) {
            InfoType.STREAM -> metadata.setArtist((item as StreamInfoItem).uploaderName)
            InfoType.PLAYLIST -> metadata.setArtist((item as PlaylistInfoItem).uploaderName)
            InfoType.CHANNEL -> metadata.setDescription((item as ChannelInfoItem).description)
            else -> return null
        }

        ImageStrategy.choosePreferredImage(ExtractorImageCompat.thumbnailImages(item))?.let {
            metadata.setArtworkUri(imageUriOrNullIfDisabled(it))
        }

        return libraryItem(createMediaIdForInfoItem(item), metadata, browsable = false)
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
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.streamEntity.title)
            .setArtist(item.streamEntity.uploader)
            .setArtworkUri(imageUriOrNullIfDisabled(item.streamEntity.thumbnailUrl))

        return libraryItem(
            createMediaIdForPlaylistIndex(false, playlistId, index),
            metadata,
            browsable = false
        )
    }

    private fun createRemotePlaylistStreamMediaItem(
        playlistId: Long,
        item: StreamInfoItem,
        index: Int
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.uploaderName)

        ImageStrategy.choosePreferredImage(ExtractorImageCompat.thumbnailImages(item))?.let {
            metadata.setArtworkUri(imageUriOrNullIfDisabled(it))
        }

        return libraryItem(
            createMediaIdForPlaylistIndex(true, playlistId, index),
            metadata,
            browsable = false
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

    private fun populateHistory(): Single<List<MediaItem>> {
        val history = database.streamHistoryDAO()
            .getHistoryForProfile(ProfileManager.getActiveProfileId(context))
            .firstOrError()
        return history.map { items ->
            CarBrowsePolicy.browse(items).map { this.createHistoryMediaItem(it) }
        }
    }

    private fun populateContinueListening(): Single<List<MediaItem>> {
        return database.streamHistoryDAO()
            .getHistoryForProfile(ProfileManager.getActiveProfileId(context))
            .firstOrError()
            .map { items ->
                CarBrowsePolicy.continueListening(items).map(this::createHistoryMediaItem)
            }
    }

    private fun populateResumption(): Single<List<MediaItem>> {
        return database.streamHistoryDAO()
            .getHistoryForProfile(ProfileManager.getActiveProfileId(context))
            .firstOrError()
            .map { items ->
                CarBrowsePolicy.resumption(items).map(this::createHistoryMediaItem)
            }
    }

    private fun createHistoryMediaItem(streamHistoryEntry: StreamHistoryEntry): MediaItem {
        val mediaId = buildMediaId()
            .appendPath(ID_HISTORY)
            .appendPath(streamHistoryEntry.streamId.toString())
            .build().toString()
        val metadata = MediaMetadata.Builder()
            .setTitle(
                streamHistoryEntry.streamEntity.title.takeUnless { it.isBlank() }
                    ?: context.getString(R.string.app_name)
            )
            .setArtist(
                streamHistoryEntry.streamEntity.uploader.takeUnless { it.isNullOrBlank() }
                    ?: context.getString(R.string.app_name)
            )
            .setArtworkUri(
                imageUriOrNullIfDisabled(streamHistoryEntry.streamEntity.thumbnailUrl)
                    ?: resourceUri(R.drawable.ic_headset)
            )

        return libraryItem(mediaId, metadata, browsable = false)
    }

    private fun getMergedPlaylists(
        profileId: String = ProfileManager.getActiveProfileId(context)
    ): Flowable<MutableList<PlaylistLocalItem>> {
        return MergedPlaylistManager.getMergedOrderedPlaylists(
            LocalPlaylistManager(database, profileId),
            RemotePlaylistManager(database, profileId)
        )
    }

    private fun populateBookmarks(): Single<List<MediaItem>> {
        val playlists = getMergedPlaylists().firstOrError()
        return playlists.map { playlist ->
            CarBrowsePolicy.browse(playlist).map { this.createPlaylistMediaItem(it) }
        }
    }

    private fun populateLocalPlaylist(playlistId: Long): Single<List<MediaItem>> {
        val playlist = LocalPlaylistManager(
            database,
            ProfileManager.getActiveProfileId(context)
        ).getPlaylistStreams(playlistId).firstOrError()
        return playlist.map { items ->
            CarBrowsePolicy.browse(items).mapIndexed { index, item ->
                createLocalPlaylistStreamMediaItem(playlistId, item, index)
            }
        }
    }

    private fun populateRemotePlaylist(playlistId: Long): Single<List<MediaItem>> {
        return RemotePlaylistManager(
            database,
            ProfileManager.getActiveProfileId(context)
        ).getPlaylist(playlistId).firstOrError()
            .flatMap { ExtractorHelper.getPlaylistInfo(it.serviceId, it.url, false) }
            .map {
                // ignore it.errors, i.e. ignore errors about specific items, since there would
                // be no way to show the error properly in Android Auto anyway
                CarBrowsePolicy.browse(it.relatedItems).mapIndexed { index, item ->
                    createRemotePlaylistStreamMediaItem(playlistId, item, index)
                }
            }
    }
    //endregion

    //region Search
    fun onSearch(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        if (!ServiceSearchSupport.supportsSearch(ServiceHelper.getSelectedServiceId(context))) {
            return immediateResult(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        }
        if (DEBUG) {
            Log.d(TAG, "onSearch($query)")
        }

        val future = SettableFuture.create<LibraryResult<Void>>()
        disposables.add(
            search(query)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { items ->
                        session.notifySearchResultChanged(browser, query, items.size, params)
                        future.set(LibraryResult.ofVoid(params))
                    },
                    { throwable ->
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                        Log.e(TAG, "Search error for query=\"$query\": $throwable")
                    }
                )
        )
        return future
    }

    fun onGetSearchResult(
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!ServiceSearchSupport.supportsSearch(ServiceHelper.getSelectedServiceId(context))) {
            return immediateResult(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        }
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        disposables.add(
            search(query)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { items ->
                        future.set(LibraryResult.ofItemList(paginate(items, page, pageSize), params))
                    },
                    { throwable ->
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                        Log.e(TAG, "Search result error for query=\"$query\": $throwable")
                    }
                )
        )
        return future
    }

    private fun search(query: String): Single<List<MediaItem>> {
        return searchMusicBySongTitle(query).map {
            // Item-specific extraction errors cannot be displayed by Android Auto, so keep all
            // successfully converted results.
            CarBrowsePolicy.search(it.relatedItems).mapNotNull(this::createInfoItemMediaItem)
        }
    }

    private fun searchMusicBySongTitle(query: String?): Single<SearchInfo> {
        val serviceId = ServiceHelper.getSelectedServiceId(context)
        return ExtractorHelper.searchFor(serviceId, query, listOf(), "")
    }
    //endregion

    private fun libraryItem(
        mediaId: String,
        metadata: MediaMetadata.Builder,
        browsable: Boolean
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            metadata
                .setIsBrowsable(browsable)
                .setIsPlayable(!browsable)
                .build()
        )
        .build()

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        val fromIndex = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        val toIndex = (fromIndex.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
        return ImmutableList.copyOf(items.subList(fromIndex, toIndex))
    }

    private fun <T> immediateResult(result: T): ListenableFuture<T> = Futures.immediateFuture(result)

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

    private fun resourceUri(@DrawableRes resourceId: Int): Uri {
        val resources = context.resources
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resourceId))
            .appendPath(resources.getResourceTypeName(resourceId))
            .appendPath(resources.getResourceEntryName(resourceId))
            .build()
    }
}
