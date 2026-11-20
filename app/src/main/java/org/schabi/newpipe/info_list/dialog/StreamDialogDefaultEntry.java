package org.schabi.newpipe.info_list.dialog;

import static org.schabi.newpipe.util.NavigationHelper.openChannelFragment;
import static org.schabi.newpipe.util.SparseItemUtil.fetchItemInfoIfSparse;
import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase;
import static org.schabi.newpipe.util.SparseItemUtil.fetchUploaderUrlIfSparse;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException;
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.subscription.StreamSubscriptionActions;
import org.schabi.newpipe.learning.LearningContentManager;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.external_communication.TubeBeamUtils;
import org.schabi.newpipe.util.external_communication.YtFifoUtils;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * <p>
 *     This enum provides entries that are accepted
 *     by the {@link InfoItemDialog.Builder}.
 * </p>
 * <p>
 *     These entries contain a String {@link #resource} which is displayed in the dialog and
 *     a default {@link #action} that is executed
 *     when the entry is selected (via <code>onClick()</code>).
 *     <br/>
 *     They action can be overridden by using the Builder's
 *     {@link InfoItemDialog.Builder#setAction(
 *     StreamDialogDefaultEntry, StreamDialogEntry.StreamDialogEntryAction)}
 *     method.
 * </p>
 */
public enum StreamDialogDefaultEntry {
    OPEN_ALBUM(R.string.open_music_album, (fragment, item) ->
            org.schabi.newpipe.util.MusicAlbumNavigation.open(
                    fragment.requireContext(), item.getAlbumUrl())),

    SHOW_CHANNEL_DETAILS(R.string.show_channel_details, (fragment, item) ->
            fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                    item.getUploaderUrl(), url -> openChannelFragment(fragment, item, url))
    ),

    SUBSCRIBE_CHANNEL(R.string.subscribe_button_title, (fragment, item) ->
            fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                    item.getUploaderUrl(), url -> StreamSubscriptionActions.show(
                            fragment, item.getServiceId(), url, false))
    ),

    CHANNEL_GROUPS(R.string.feed_groups_header_title, (fragment, item) ->
            fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                    item.getUploaderUrl(), url -> StreamSubscriptionActions.show(
                            fragment, item.getServiceId(), url, true))
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType.
     */
    ENQUEUE(R.string.enqueue_stream, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                NavigationHelper.enqueueOnPlayer(ctx, singlePlayQueue));
    }),

    /**
     * Enqueues the stream automatically to the current PlayerType
     * after the currently playing stream.
     */
    ENQUEUE_NEXT(R.string.enqueue_next_stream, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                NavigationHelper.enqueueNextOnPlayer(ctx, singlePlayQueue));
    }),

    START_HERE_ON_BACKGROUND(R.string.start_here_on_background, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                NavigationHelper.playOnBackgroundPlayer(ctx, singlePlayQueue, true));
    }),

    START_HERE_ON_POPUP(R.string.start_here_on_popup, (fragment, item) -> {
            final Context ctx = fragment.requireContext().getApplicationContext();
            fetchItemInfoIfSparse(ctx, item, singlePlayQueue ->
                NavigationHelper.playOnPopupPlayer(ctx, singlePlayQueue, true));
    }),

    SET_AS_PLAYLIST_THUMBNAIL(R.string.set_as_playlist_thumbnail, (fragment, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    DELETE(R.string.delete, (fragment, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    /**
     * Opens a {@link PlaylistDialog} to either append the stream to a playlist
     * or create a new playlist if there are no local playlists.
     */
    APPEND_PLAYLIST(R.string.add_to_playlist, (fragment, item) ->
        PlaylistDialog.createCorrespondingDialog(
                fragment.getContext(),
                List.of(new StreamEntity(item)),
                dialog -> {
                    if (!fragment.isAdded()
                            || fragment.getParentFragmentManager().isStateSaved()) {
                        return;
                    }
                    dialog.show(
                            fragment.getParentFragmentManager(),
                            "StreamDialogEntry@"
                                    + (dialog instanceof PlaylistAppendDialog ? "append" : "create")
                                    + "_playlist"
                    );
                }
        )
    ),

    PLAY_WITH_KODI(R.string.play_with_kodi_title, (fragment, item) ->
            KoreUtils.playWithKore(fragment.requireContext(), Uri.parse(item.getUrl()))),

    /**
     * Sends the stream to a TV through the TubeBeam app, see
     * {@link TubeBeamUtils#sendToTv(android.content.Context, String)}.
     */
    SEND_TO_TV(R.string.send_to_tv_title, (fragment, item) ->
            TubeBeamUtils.sendToTv(fragment.requireContext(), item.getUrl())),

    /**
     * Plays the stream's audio on the home audio system through a yt-fifo server, see
     * {@link YtFifoUtils#sendToHomeAudio(android.content.Context, String, boolean)}.
     */
    PLAY_ON_HOME_AUDIO(R.string.play_on_home_audio_title, (fragment, item) ->
            YtFifoUtils.sendToHomeAudio(fragment.requireContext(), item.getUrl(), true)),

    QUEUE_ON_HOME_AUDIO(R.string.queue_on_home_audio_title, (fragment, item) ->
            YtFifoUtils.sendToHomeAudio(fragment.requireContext(), item.getUrl(), false)),

    COPY_TITLE(R.string.copy_video_title, (fragment, item) ->
            ShareUtils.copyToClipboard(fragment.requireContext(), item.getName())),

    SHARE(R.string.share, (fragment, item) ->
            ShareUtils.shareText(fragment.requireContext(), item.getName(), item.getUrl(),
                    ExtractorImageCompat.thumbnailImages(item))),

    /**
     * Opens a {@link DownloadDialog} after fetching some stream info.
     * If the user quits the current fragment, it will not open a DownloadDialog.
     */
    DOWNLOAD(R.string.download, (fragment, item) ->
            fetchStreamInfoAndSaveToDatabase(fragment.requireContext(), item.getServiceId(),
                    item.getUrl(), info -> {
                        // Ensure the fragment is attached and its state hasn't been saved to avoid
                        // showing dialog during lifecycle changes or when the activity is paused,
                        // e.g. by selecting the download option and opening a different fragment.
                        if (fragment.isAdded() && !fragment.isStateSaved()) {
                            final DownloadDialog downloadDialog =
                                    new DownloadDialog(fragment.requireContext(), info);
                            downloadDialog.show(fragment.getChildFragmentManager(),
                                    "downloadDialog");
                        }
                    })
    ),

    DOWNLOAD_EXTERNALLY(R.string.download_externally, (fragment, item) ->
            org.schabi.newpipe.download.ExternalDownloader.open(
                    fragment.requireContext(), item.getUrl())
    ),

    BLOCK_VIDEO(R.string.block_video, (fragment, item) -> {
        ContentBlockingHelper.blockVideo(fragment.requireContext(), item);
        Toast.makeText(fragment.requireContext(), R.string.video_blocked,
                Toast.LENGTH_SHORT).show();
    }),

    BLOCK_CHANNEL(R.string.block_channel, (fragment, item) ->
            fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                    item.getUploaderUrl(), url -> {
                        ContentBlockingHelper.blockChannel(fragment.requireContext(), url,
                                item.getUploaderName());
                        Toast.makeText(fragment.requireContext(), R.string.channel_blocked,
                                Toast.LENGTH_SHORT).show();
                    })),

    OPEN_IN_BROWSER(R.string.open_in_browser, (fragment, item) ->
            ShareUtils.openUrlInBrowser(fragment.requireContext(), item.getUrl())),

    MARK_AS_LEARNING(R.string.learning_mark_content, (fragment, item) ->
        LearningContentManager.getInstance(fragment.requireContext())
                .setStreamMarked(new StreamEntity(item), true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Toast.makeText(fragment.requireContext(),
                                R.string.learning_content_added, Toast.LENGTH_SHORT).show(),
                        error -> Toast.makeText(fragment.requireContext(),
                                R.string.learning_content_update_error,
                                Toast.LENGTH_SHORT).show()
                )
    ),

    REMOVE_FROM_LEARNING(R.string.learning_remove_content, (fragment, item) ->
        LearningContentManager.getInstance(fragment.requireContext())
                .setStreamMarked(new StreamEntity(item), false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Toast.makeText(fragment.requireContext(),
                                R.string.learning_content_removed, Toast.LENGTH_SHORT).show(),
                        error -> Toast.makeText(fragment.requireContext(),
                                R.string.learning_content_update_error,
                                Toast.LENGTH_SHORT).show()
                )
    ),


    MARK_AS_WATCHED(R.string.mark_as_watched, (fragment, item) ->
        new HistoryRecordManager(fragment.getContext())
                .markAsWatched(item)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    final Context context = fragment.getContext();
                    if (context != null) {
                        handleMarkAsWatchedError(context, error);
                    }
                })
                .onErrorComplete()
                .subscribe()
    ),

    MARK_AS_UNWATCHED(R.string.mark_as_unwatched, (fragment, item) ->
        new HistoryRecordManager(fragment.getContext())
                .markAsUnwatched(item)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    ErrorUtil.showSnackbar(
                        fragment.requireContext(),
                        new ErrorInfo(
                                error,
                                UserAction.OPEN_INFO_ITEM_DIALOG,
                                "Got an error when trying to mark as unwatched"
                        )
                    );
                })
                .onErrorComplete()
                .subscribe()
    );


    @StringRes
    public final int resource;
    @NonNull
    public final StreamDialogEntry.StreamDialogEntryAction action;

    StreamDialogDefaultEntry(@StringRes final int resource,
                             @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
        this.resource = resource;
        this.action = action;
    }

    /**
     * A scheduled stream has no duration yet. Keep its history untouched and explain why
     * marking it watched cannot complete instead of presenting an unexpected-error report.
     *
     * @param context attached fragment context
     * @param error failure returned by the history operation
     */
    static void handleMarkAsWatchedError(final Context context, final Throwable error) {
        final Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable cause = error;
        while (cause != null && visited.add(cause)) {
            if (cause instanceof LiveNotStartException
                    || cause instanceof VideoNotReleaseException) {
                Toast.makeText(context, R.string.mark_watched_not_started,
                        Toast.LENGTH_LONG).show();
                return;
            }
            cause = cause.getCause();
        }
        ErrorUtil.showSnackbar(context, new ErrorInfo(
                error,
                UserAction.OPEN_INFO_ITEM_DIALOG,
                "Got an error when trying to mark as watched"
        ));
    }

    @NonNull
    public StreamDialogEntry toStreamDialogEntry() {
        return new StreamDialogEntry(resource, action);
    }

}
