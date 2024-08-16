package org.schabi.newpipe.player.mediabrowser;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.utils.MediaConstants;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MediaBrowserConnector implements MediaSessionConnector.PlaybackPreparer {

    private static final String TAG = MediaBrowserConnector.class.getSimpleName();

    @NonNull
    private final PlayerService playerService;
    @NonNull
    private final MediaSessionConnector sessionConnector;
    @NonNull
    private final MediaSessionCompat mediaSession;

    private AppDatabase database;
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;
    private Disposable prepareOrPlayDisposable;
    private Disposable searchDisposable;

    public MediaBrowserConnector(@NonNull final PlayerService playerService) {
        this.playerService = playerService;
        mediaSession = new MediaSessionCompat(playerService, TAG);
        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setMetadataDeduplicationEnabled(true);
        sessionConnector.setPlaybackPreparer(this);
        playerService.setSessionToken(mediaSession.getSessionToken());

        setupBookmarksNotifications();
    }

    @NonNull
    public MediaSessionConnector getSessionConnector() {
        return sessionConnector;
    }

    public void release() {
        disposePrepareOrPlayCommands();
        disposeBookmarksNotifications();
        mediaSession.release();
    }

    @NonNull
    private static final String ID_AUTHORITY = BuildConfig.APPLICATION_ID;
    @NonNull
    private static final String ID_ROOT = "//" + ID_AUTHORITY;
    @NonNull
    private static final String ID_BOOKMARKS = "playlists";
    @NonNull
    private static final String ID_HISTORY = "history";
    @NonNull
    private static final String ID_INFO_ITEM = "item";

    @NonNull
    private static final String ID_LOCAL = "local";
    @NonNull
    private static final String ID_REMOTE = "remote";
    @NonNull
    private static final String ID_URL = "url";
    @NonNull
    private static final String ID_STREAM = "stream";
    @NonNull
    private static final String ID_PLAYLIST = "playlist";
    @NonNull
    private static final String ID_CHANNEL = "channel";

    @NonNull
    private MediaItem createRootMediaItem(@Nullable final String mediaId,
                                          final String folderName,
                                          @DrawableRes final int iconResId) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(mediaId);
        builder.setTitle(folderName);
        final Resources resources = playerService.getResources();
        builder.setIconUri(new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(iconResId))
                .appendPath(resources.getResourceTypeName(iconResId))
                .appendPath(resources.getResourceEntryName(iconResId))
                .build());

        final Bundle extras = new Bundle();
        extras.putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                playerService.getString(R.string.app_name));
        builder.setExtras(extras);
        return new MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE);
    }

    @NonNull
    private MediaItem createPlaylistMediaItem(@NonNull final PlaylistLocalItem playlist) {
        final var builder = new MediaDescriptionCompat.Builder();
        final boolean remote = playlist instanceof PlaylistRemoteEntity;
        builder.setMediaId(createMediaIdForInfoItem(remote, playlist.getUid()))
                .setTitle(playlist.getOrderingName())
                .setIconUri(Uri.parse(playlist.getThumbnailUrl()));

        final Bundle extras = new Bundle();
        extras.putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                playerService.getResources().getString(R.string.tab_bookmarks));
        builder.setExtras(extras);
        return new MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE);
    }

    private MediaItem createInfoItemMediaItem(@NonNull final InfoItem item) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(createMediaIdForInfoItem(item))
                .setTitle(item.getName());

        switch (item.getInfoType()) {
            case STREAM:
                builder.setSubtitle(((StreamInfoItem) item).getUploaderName());
                break;
            case PLAYLIST:
                builder.setSubtitle(((PlaylistInfoItem) item).getUploaderName());
                break;
            case CHANNEL:
                builder.setSubtitle(((ChannelInfoItem) item).getDescription());
                break;
            default:
                break;
        }
        final var thumbnails = item.getThumbnails();
        if (!thumbnails.isEmpty()) {
            builder.setIconUri(Uri.parse(thumbnails.get(0).getUrl()));
        }
        return new MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE);
    }

    @NonNull
    private Uri.Builder buildMediaId() {
        return new Uri.Builder().authority(ID_AUTHORITY);
    }

    @NonNull
    private Uri.Builder buildPlaylistMediaId(final String playlistType) {
        return buildMediaId()
                .appendPath(ID_BOOKMARKS)
                .appendPath(playlistType);
    }

    @NonNull
    private Uri.Builder buildLocalPlaylistItemMediaId(final boolean remote, final long playlistId) {
        return buildPlaylistMediaId(remote ? ID_REMOTE : ID_LOCAL)
                .appendPath(Long.toString(playlistId));
    }

    private static String infoItemTypeToString(final InfoItem.InfoType type) {
        return switch (type) {
            case STREAM -> ID_STREAM;
            case PLAYLIST -> ID_PLAYLIST;
            case CHANNEL -> ID_CHANNEL;
            default ->
                throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static InfoItem.InfoType infoItemTypeFromString(final String type) {
        return switch (type) {
            case ID_STREAM -> InfoItem.InfoType.STREAM;
            case ID_PLAYLIST -> InfoItem.InfoType.PLAYLIST;
            case ID_CHANNEL -> InfoItem.InfoType.CHANNEL;
            default ->
                    throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @NonNull
    private Uri.Builder buildInfoItemMediaId(@NonNull final InfoItem item) {
        return buildMediaId()
                .appendPath(ID_INFO_ITEM)
                .appendPath(infoItemTypeToString(item.getInfoType()))
                .appendPath(Integer.toString(item.getServiceId()))
                .appendQueryParameter(ID_URL, item.getUrl());
    }

    @NonNull
    private String createMediaIdForInfoItem(final boolean remote, final long playlistId) {
        return buildLocalPlaylistItemMediaId(remote, playlistId)
                .build().toString();
    }

    @NonNull
    private MediaItem createLocalPlaylistStreamMediaItem(final long playlistId,
                                                         @NonNull final PlaylistStreamEntry item,
                                                         final int index) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(createMediaIdForPlaylistIndex(false, playlistId, index))
                .setTitle(item.getStreamEntity().getTitle())
                .setSubtitle(item.getStreamEntity().getUploader())
                .setIconUri(Uri.parse(item.getStreamEntity().getThumbnailUrl()));

         return new MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE);
    }

    @NonNull
    private MediaItem createRemotePlaylistStreamMediaItem(final long playlistId,
                                                          @NonNull final StreamInfoItem item,
                                                          final int index) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(createMediaIdForPlaylistIndex(true, playlistId, index))
                .setTitle(item.getName())
                .setSubtitle(item.getUploaderName());
        final var thumbnails = item.getThumbnails();
        if (!thumbnails.isEmpty()) {
            builder.setIconUri(Uri.parse(thumbnails.get(0).getUrl()));
        }

        return new MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE);
    }

    @NonNull
    private String createMediaIdForPlaylistIndex(final boolean remote, final long playlistId,
                                                 final int index) {
        return buildLocalPlaylistItemMediaId(remote, playlistId)
                .appendPath(Integer.toString(index))
                .build().toString();
    }

    @NonNull
    private String createMediaIdForInfoItem(@NonNull final InfoItem item) {
        return buildInfoItemMediaId(item).build().toString();
    }

    @Nullable
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                                           final int clientUid,
                                                           @Nullable final Bundle rootHints) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onGetRoot(%s, %s, %s)",
                  clientPackageName, clientUid, rootHints));
        }

        final Bundle extras = new Bundle();
        extras.putBoolean(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true);
        return new MediaBrowserServiceCompat.BrowserRoot(ID_ROOT, extras);
    }

    public Single<List<MediaItem>> onLoadChildren(@NonNull final String parentId) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId));
        }


        try {
            final Uri parentIdUri = Uri.parse(parentId);
            final List<String> path = new ArrayList<>(parentIdUri.getPathSegments());

            if (path.isEmpty()) {
                final List<MediaItem> mediaItems = new ArrayList<>();
                mediaItems.add(
                        createRootMediaItem(ID_BOOKMARKS,
                                playerService.getResources().getString(
                                        R.string.tab_bookmarks_short),
                                R.drawable.ic_bookmark_white));
                mediaItems.add(
                        createRootMediaItem(ID_HISTORY,
                                playerService.getResources().getString(R.string.action_history),
                                R.drawable.ic_history_white));
                return Single.just(mediaItems);
            }

            final String uriType = path.get(0);
            path.remove(0);

            switch (uriType) {
                case ID_BOOKMARKS:
                    if (path.isEmpty()) {
                        return populateBookmarks();
                    }
                    if (path.size() == 2) {
                        final String localOrRemote = path.get(0);
                        final long playlistId = Long.parseLong(path.get(1));
                        if (localOrRemote.equals(ID_LOCAL)) {
                            return populateLocalPlaylist(playlistId);
                        } else if (localOrRemote.equals(ID_REMOTE)) {
                            return populateRemotePlaylist(playlistId);
                        }
                    }
                    Log.w(TAG, "Unknown playlist URI: " + parentId);
                    throw parseError();
                case ID_HISTORY:
                    return populateHistory();
                default:
                    throw parseError();
            }
        } catch (final ContentNotAvailableException e) {
            return Single.error(e);
        }
    }

    private Single<List<MediaItem>> populateHistory() {
        final StreamHistoryDAO streamHistory = getDatabase().streamHistoryDAO();
        final var history = streamHistory.getHistory().firstOrError();
        return history.map(items -> items.stream()
                .map(this::createHistoryMediaItem)
                .collect(Collectors.toList()));
    }

    @NonNull
    private MediaItem createHistoryMediaItem(@NonNull final StreamHistoryEntry streamHistoryEntry) {
        final var builder = new MediaDescriptionCompat.Builder();
        final var mediaId = buildMediaId()
                .appendPath(ID_HISTORY)
                .appendPath(Long.toString(streamHistoryEntry.getStreamId()))
                .build().toString();
        builder.setMediaId(mediaId)
                .setTitle(streamHistoryEntry.getStreamEntity().getTitle())
                .setSubtitle(streamHistoryEntry.getStreamEntity().getUploader())
                .setIconUri(Uri.parse(streamHistoryEntry.getStreamEntity().getThumbnailUrl()));

        return new MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE);
    }

    private AppDatabase getDatabase() {
        if (database == null) {
            database = NewPipeDatabase.getInstance(playerService);
        }
        return database;
    }

    private Flowable<List<PlaylistLocalItem>> getPlaylists() {
        if (localPlaylistManager == null) {
            localPlaylistManager = new LocalPlaylistManager(getDatabase());
        }
        if (remotePlaylistManager == null) {
            remotePlaylistManager = new RemotePlaylistManager(getDatabase());
        }
        return MergedPlaylistManager.getMergedOrderedPlaylists(localPlaylistManager,
            remotePlaylistManager);
    }

    @Nullable Disposable bookmarksNotificationsDisposable;

    private void setupBookmarksNotifications() {
        bookmarksNotificationsDisposable = getPlaylists().subscribe(
                playlistMetadataEntries -> playerService.notifyChildrenChanged(ID_BOOKMARKS));
    }

    private void disposeBookmarksNotifications() {
        if (bookmarksNotificationsDisposable != null) {
            bookmarksNotificationsDisposable.dispose();
            bookmarksNotificationsDisposable = null;
        }
    }

    // Suppress Sonar warning replace list collection by Stream.toList call, as this method is only
    // available in Android API 34 and not currently available with desugaring
    @SuppressWarnings("squid:S6204")
    private Single<List<MediaItem>> populateBookmarks() {
        final var playlists = getPlaylists().firstOrError();
        return playlists.map(playlist -> playlist.stream()
                .map(this::createPlaylistMediaItem)
                .collect(Collectors.toList()));
    }

    private Single<List<MediaItem>> populateLocalPlaylist(final long playlistId) {
        final var playlist = localPlaylistManager.getPlaylistStreams(playlistId).firstOrError();
        return playlist.map(items -> {
            final List<MediaItem> results = new ArrayList<>();
            int index = 0;
            for (final PlaylistStreamEntry item : items) {
                results.add(createLocalPlaylistStreamMediaItem(playlistId, item, index));
                ++index;
            }
            return results;
        });
    }

    private Single<List<Pair<StreamInfoItem, Integer>>> getRemotePlaylist(final long playlistId) {
        final var playlistFlow = remotePlaylistManager.getPlaylist(playlistId).firstOrError();
        return playlistFlow.flatMap(item -> {
            final var playlist = item.get(0);
            final var playlistInfo = ExtractorHelper.getPlaylistInfo(playlist.getServiceId(),
                                                                     playlist.getUrl(), false);
            return playlistInfo.flatMap(info -> {
                final var infoItemsPage = info.getRelatedItems();

                if (!info.getErrors().isEmpty()) {
                    final List<Throwable> errors = new ArrayList<>(info.getErrors());

                    errors.removeIf(ContentNotSupportedException.class::isInstance);

                    if (!errors.isEmpty()) {
                        return Single.error(errors.get(0));
                    }
                }

                return Single.just(IntStream.range(0, infoItemsPage.size())
                                            .mapToObj(i -> Pair.create(infoItemsPage.get(i), i))
                                            .toList());
            });
        });
    }

    private Single<List<MediaItem>> populateRemotePlaylist(final long playlistId) {
        return getRemotePlaylist(playlistId).map(items ->
            items.stream().map(pair ->
                createRemotePlaylistStreamMediaItem(playlistId, pair.first, pair.second)
            ).toList()
        );
    }

    private void playbackError(@StringRes final int resId, final int code) {
        playerService.stopForImmediateReusing();
        sessionConnector.setCustomErrorMessage(playerService.getString(resId), code);
    }

    private void playbackError(@NonNull final ErrorInfo errorInfo) {
        playbackError(errorInfo.getMessageStringId(), PlaybackStateCompat.ERROR_CODE_APP_ERROR);
    }

    private Single<PlayQueue> extractLocalPlayQueue(final long playlistId, final int index) {
        return localPlaylistManager.getPlaylistStreams(playlistId)
                .firstOrError()
                .map(items -> {
                    final List<StreamInfoItem> infoItems = items.stream()
                            .map(PlaylistStreamEntry::toStreamInfoItem)
                            .collect(Collectors.toList());
                    return new SinglePlayQueue(infoItems, index);
                });
    }

    private Single<PlayQueue> extractRemotePlayQueue(final long playlistId, final int index) {
        return getRemotePlaylist(playlistId).map(items -> {
            final var infoItems = items.stream().map(pair -> pair.first).toList();
            return new SinglePlayQueue(infoItems, index);
        });
    }

    private static ContentNotAvailableException parseError() {
        return new ContentNotAvailableException("Failed to parse media ID");
    }

    private Single<PlayQueue> extractPlayQueueFromMediaId(final String mediaId) {
        try {
            final Uri mediaIdUri = Uri.parse(mediaId);
            final List<String> path = new ArrayList<>(mediaIdUri.getPathSegments());

            if (path.isEmpty()) {
                throw parseError();
            }

            final String uriType = path.get(0);
            path.remove(0);

            return switch (uriType) {
                case ID_BOOKMARKS -> extractPlayQueueFromPlaylistMediaId(path,
                        mediaIdUri.getQueryParameter(ID_URL));
                case ID_HISTORY -> extractPlayQueueFromHistoryMediaId(path);
                case ID_INFO_ITEM -> extractPlayQueueFromInfoItemMediaId(path,
                        mediaIdUri.getQueryParameter(ID_URL));
                default -> throw parseError();
            };
        } catch (final ContentNotAvailableException e) {
            return Single.error(e);
        }
    }

    private Single<PlayQueue>
    extractPlayQueueFromPlaylistMediaId(
            @NonNull final List<String> path,
            @Nullable final String url) throws ContentNotAvailableException {
        if (path.isEmpty()) {
            throw parseError();
        }

        final String playlistType = path.get(0);
        path.remove(0);

        switch (playlistType) {
            case ID_LOCAL, ID_REMOTE:
                if (path.size() != 2) {
                    throw parseError();
                }
                final long playlistId = Long.parseLong(path.get(0));
                final int index = Integer.parseInt(path.get(1));
                return playlistType.equals(ID_LOCAL)
                        ? extractLocalPlayQueue(playlistId, index)
                        : extractRemotePlayQueue(playlistId, index);
            case ID_URL:
                if (path.size() != 1) {
                    throw parseError();
                }

                final int serviceId = Integer.parseInt(path.get(0));
                return ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                        .map(PlaylistPlayQueue::new);
            default:
                throw parseError();
        }
    }

    private Single<PlayQueue> extractPlayQueueFromHistoryMediaId(
            final List<String> path) throws ContentNotAvailableException {
        if (path.size() != 1) {
            throw parseError();
        }

        final long streamId = Long.parseLong(path.get(0));
        return getDatabase().streamHistoryDAO().getHistory()
                .firstOrError()
                .map(items -> {
                    final List<StreamInfoItem> infoItems = items.stream()
                            .filter(it -> it.getStreamId() == streamId)
                            .map(StreamHistoryEntry::toStreamInfoItem)
                            .collect(Collectors.toList());
                    return new SinglePlayQueue(infoItems, 0);
                });
    }

    private static Single<PlayQueue> extractPlayQueueFromInfoItemMediaId(
            final List<String> path, final String url) throws ContentNotAvailableException {
        if (path.size() != 2) {
            throw parseError();
        }
        final var infoItemType = infoItemTypeFromString(path.get(0));
        final int serviceId = Integer.parseInt(path.get(1));
        return switch (infoItemType) {
            case STREAM -> ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .map(SinglePlayQueue::new);
            case PLAYLIST -> ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                    .map(PlaylistPlayQueue::new);
            case CHANNEL -> ExtractorHelper.getChannelInfo(serviceId, url, false)
                    .map(info -> {
                        final Optional<ListLinkHandler> playableTab = info.getTabs()
                                .stream()
                                .filter(ChannelTabHelper::isStreamsTab)
                                .findFirst();

                        if (playableTab.isPresent()) {
                            return new ChannelTabPlayQueue(serviceId,
                                    new ListLinkHandler(playableTab.get()));
                        } else {
                            throw new ContentNotAvailableException("No streams tab found");
                        }
                    });
            default -> throw parseError();
        };
    }

    @Override
    public long getSupportedPrepareActions() {
        return PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
    }

    private void disposePrepareOrPlayCommands() {
        if (prepareOrPlayDisposable != null) {
            prepareOrPlayDisposable.dispose();
            prepareOrPlayDisposable = null;
        }
    }

    @Override
    public void onPrepare(final boolean playWhenReady) {
        disposePrepareOrPlayCommands();
        // No need to prepare
    }

    @Override
    public void onPrepareFromMediaId(@NonNull final String mediaId,
                                     final boolean playWhenReady,
                                     @Nullable final Bundle extras) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserConnector.onPrepareFromMediaId(%s, %s, %s)",
                  mediaId, playWhenReady, extras));
        }

        disposePrepareOrPlayCommands();
        prepareOrPlayDisposable = extractPlayQueueFromMediaId(mediaId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    playQueue -> {
                        sessionConnector.setCustomErrorMessage(null);
                        NavigationHelper.playOnBackgroundPlayer(playerService, playQueue,
                                playWhenReady);
                    },
                    throwable -> playbackError(new ErrorInfo(throwable, UserAction.PLAY_STREAM,
                            "Failed playback of media ID [" + mediaId + "]: "))
                );
    }

    @Override
    public void onPrepareFromSearch(@NonNull final String query,
                                    final boolean playWhenReady,
                                    @Nullable final Bundle extras) {
         disposePrepareOrPlayCommands();
         playbackError(R.string.content_not_supported,
                       PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED);
    }

    private @NonNull Single<SearchInfo> searchMusicBySongTitle(final String query) {
        final var serviceId = ServiceHelper.getSelectedServiceId(playerService);
        return ExtractorHelper.searchFor(serviceId, query,
                new ArrayList<>(), "");
    }

    private @NonNull SingleSource<List<MediaItem>>
    mediaItemsFromInfoItemList(final ListInfo<InfoItem> result) {
        final List<Throwable> exceptions = result.getErrors();
        if (!exceptions.isEmpty()
                && !(exceptions.size() == 1
                && exceptions.get(0) instanceof SearchExtractor.NothingFoundException)) {
            return Single.error(exceptions.get(0));
        }

        final List<InfoItem> items = result.getRelatedItems();
        if (items.isEmpty()) {
            return Single.error(new NullPointerException("Got no search results."));
        }
        try {
            final List<MediaItem> results = items.stream()
                    .filter(item ->
                            item.getInfoType() == InfoItem.InfoType.STREAM
                                    || item.getInfoType() == InfoItem.InfoType.PLAYLIST
                                    || item.getInfoType() == InfoItem.InfoType.CHANNEL)
                    .map(this::createInfoItemMediaItem).toList();
            return Single.just(results);
        } catch (final Exception e) {
            return Single.error(e);
        }
    }

    private void handleSearchError(final Throwable throwable) {
        Log.e(TAG, "Search error: " + throwable);
        disposePrepareOrPlayCommands();
        playbackError(R.string.content_not_supported, PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED);
    }

    @Override
    public void onPrepareFromUri(@NonNull final Uri uri,
                                 final boolean playWhenReady,
                                 @Nullable final Bundle extras) {
        disposePrepareOrPlayCommands();
        playbackError(R.string.content_not_supported, PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED);
    }

    @Override
    public boolean onCommand(@NonNull final Player player,
                             @NonNull final String command,
                             @Nullable final Bundle extras,
                             @Nullable final ResultReceiver cb) {
        return false;
    }

    public void onSearch(@NonNull final String query,
                         @NonNull final MediaBrowserServiceCompat.Result<List<MediaItem>> result) {
        result.detach();
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        searchDisposable = searchMusicBySongTitle(query)
                .flatMap(this::mediaItemsFromInfoItemList)
                .subscribeOn(Schedulers.io())
                .subscribe(result::sendResult,
                        this::handleSearchError);
    }
}
