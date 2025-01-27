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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.utils.MediaConstants;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

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
    private Disposable prepareOrPlayDisposable;

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
    private static final String ID_ROOT = "//${BuildConfig.APPLICATION_ID}/r";
    @NonNull
    private static final String ID_BOOKMARKS = ID_ROOT + "/playlists";
    @NonNull
    private static final String ID_HISTORY = ID_ROOT + "/history";
    @NonNull
    private static final String ID_STREAM = ID_ROOT + "/stream";

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
    private MediaItem createPlaylistMediaItem(@NonNull final PlaylistMetadataEntry playlist) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(createMediaIdForPlaylist(playlist.getUid()))
                .setTitle(playlist.name)
                .setIconUri(Uri.parse(playlist.thumbnailUrl));

        final Bundle extras = new Bundle();
        extras.putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                playerService.getResources().getString(R.string.tab_bookmarks));
        builder.setExtras(extras);
        return new MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE);
    }

    @NonNull
    private String createMediaIdForPlaylist(final long playlistId) {
        return ID_BOOKMARKS + '/' + playlistId;
    }

    @NonNull
    private MediaItem createPlaylistStreamMediaItem(final long playlistId,
                                                    @NonNull final PlaylistStreamEntry item,
                                                    final int index) {
        final var builder = new MediaDescriptionCompat.Builder();
        builder.setMediaId(createMediaIdForPlaylistIndex(playlistId, index))
                .setTitle(item.getStreamEntity().getTitle())
                .setSubtitle(item.getStreamEntity().getUploader())
                .setIconUri(Uri.parse(item.getStreamEntity().getThumbnailUrl()));

         return new MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE);
    }

    @NonNull
    private String createMediaIdForPlaylistIndex(final long playlistId, final int index) {
        return createMediaIdForPlaylist(playlistId) + '/' + index;
    }

    @Nullable
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                                           final int clientUid,
                                                           @Nullable final Bundle rootHints) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onGetRoot(%s, %s, %s)",
                  clientPackageName, clientUid, rootHints));
        }

        return new MediaBrowserServiceCompat.BrowserRoot(ID_ROOT, null);
    }

    public Single<List<MediaItem>> onLoadChildren(@NonNull final String parentId) {
        if (DEBUG) {
            Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId));
        }

        final List<MediaItem> mediaItems = new ArrayList<>();

        if (parentId.equals(ID_ROOT)) {
            mediaItems.add(
                    createRootMediaItem(ID_BOOKMARKS,
                            playerService.getResources().getString(R.string.tab_bookmarks),
                            R.drawable.ic_bookmark));
            mediaItems.add(
                    createRootMediaItem(ID_HISTORY,
                            playerService.getResources().getString(R.string.action_history),
                            R.drawable.ic_history));
        } else if (parentId.startsWith(ID_BOOKMARKS)) {
            final Uri parentIdUri = Uri.parse(parentId);
            final List<String> path = parentIdUri.getPathSegments();
            if (path.size() == 2) {
                return populateBookmarks();
            } else if (path.size() == 3) {
                final long playlistId = Long.parseLong(path.get(2));
                return populatePlaylist(playlistId);
            } else {
                Log.w(TAG, "Unknown playlist URI: " + parentId);
            }
        } else if (parentId.equals(ID_HISTORY)) {
            return populateHistory();
        }
        return Single.just(mediaItems);
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
        builder.setMediaId(ID_STREAM + '/' + streamHistoryEntry.getStreamId())
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

    private LocalPlaylistManager getPlaylistManager() {
        if (localPlaylistManager == null) {
            localPlaylistManager = new LocalPlaylistManager(getDatabase());
        }
        return localPlaylistManager;
    }

    @Nullable Disposable bookmarksNotificationsDisposable;

    private void setupBookmarksNotifications() {
        bookmarksNotificationsDisposable = getPlaylistManager().getPlaylists().subscribe(
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
        final var playlists = getPlaylistManager().getPlaylists().firstOrError();
        return playlists.map(playlist -> playlist.stream()
                .map(this::createPlaylistMediaItem)
                .collect(Collectors.toList()));
    }

    private Single<List<MediaItem>> populatePlaylist(final long playlistId) {
        final var playlist = getPlaylistManager().getPlaylistStreams(playlistId).firstOrError();
        return playlist.map(items -> {
            final List<MediaItem> results = new ArrayList<>();
            int index = 0;
            for (final PlaylistStreamEntry item : items) {
                results.add(createPlaylistStreamMediaItem(playlistId, item, index));
                ++index;
            }
            return results;
        });
    }

    private void playbackError(@StringRes final int resId, final int code) {
        playerService.stopForImmediateReusing();
        sessionConnector.setCustomErrorMessage(playerService.getString(resId), code);
    }

    private void playbackError(@NonNull final ErrorInfo errorInfo) {
        playbackError(errorInfo.getMessageStringId(), PlaybackStateCompat.ERROR_CODE_APP_ERROR);
    }

    private Single<PlayQueue> extractPlayQueueFromMediaId(final String mediaId) {
        final Uri mediaIdUri = Uri.parse(mediaId);
        if (mediaIdUri == null) {
            return Single.error(new ContentNotAvailableException("Media ID cannot be parsed"));
        }

        final List<String> path = mediaIdUri.getPathSegments();

        if (mediaId.startsWith(ID_BOOKMARKS) && path.size() == 4) {
            final long playlistId = Long.parseLong(path.get(2));
            final int index = Integer.parseInt(path.get(3));

            return getPlaylistManager()
                    .getPlaylistStreams(playlistId)
                    .firstOrError()
                    .map(items -> {
                        final List<StreamInfoItem> infoItems = items.stream()
                                .map(PlaylistStreamEntry::toStreamInfoItem)
                                .collect(Collectors.toList());
                        return new SinglePlayQueue(infoItems, index);
                    });
        } else if (mediaId.startsWith(ID_STREAM) && path.size() == 3) {
            final long streamId = Long.parseLong(path.get(2));
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

        return Single.error(new ContentNotAvailableException("Media ID cannot be parsed"));
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
}
