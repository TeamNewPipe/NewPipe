package org.schabi.newpipe.info_list.dialog;

import static org.schabi.newpipe.util.NavigationHelper.openChannelFragment;
import static org.schabi.newpipe.util.SparseItemUtil.fetchItemInfoIfSparse;
import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabase;
import static org.schabi.newpipe.util.SparseItemUtil.fetchUploaderUrlIfSparse;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.List;

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
    SHOW_CHANNEL_DETAILS(R.string.show_channel_details, (fragmentActivity, item) ->
        fetchUploaderUrlIfSparse(fragmentActivity, item.getServiceId(), item.getUrl(),
                item.getUploaderUrl(), url -> openChannelFragment(fragmentActivity, item, url))
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType.
     */
    ENQUEUE(R.string.enqueue_stream, (fragmentActivity, item) ->
            fetchItemInfoIfSparse(fragmentActivity, item, singlePlayQueue ->
                NavigationHelper.enqueueOnPlayer(fragmentActivity, singlePlayQueue))
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType
     * after the currently playing stream.
     */
    ENQUEUE_NEXT(R.string.enqueue_next_stream, (fragmentActivity, item) ->
            fetchItemInfoIfSparse(fragmentActivity, item, singlePlayQueue ->
                NavigationHelper.enqueueNextOnPlayer(fragmentActivity, singlePlayQueue))
    ),

    START_HERE_ON_BACKGROUND(R.string.start_here_on_background, (fragmentActivity, item) ->
            fetchItemInfoIfSparse(fragmentActivity, item, singlePlayQueue ->
                NavigationHelper.playOnBackgroundPlayer(
                        fragmentActivity, singlePlayQueue, true))),

    START_HERE_ON_POPUP(R.string.start_here_on_popup, (fragmentActivity, item) ->
            fetchItemInfoIfSparse(fragmentActivity, item, singlePlayQueue ->
                NavigationHelper.playOnPopupPlayer(fragmentActivity, singlePlayQueue, true))),

    SET_AS_PLAYLIST_THUMBNAIL(R.string.set_as_playlist_thumbnail, (fragmentActivity, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    DELETE(R.string.delete, (fragmentActivity, item) -> {
        throw new UnsupportedOperationException("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()");
    }),

    /**
     * Opens a {@link PlaylistDialog} to either append the stream to a playlist
     * or create a new playlist if there are no local playlists.
     */
    APPEND_PLAYLIST(R.string.add_to_playlist, (fragmentActivity, item) ->
        PlaylistDialog.createCorrespondingDialog(
                fragmentActivity,
                List.of(new StreamEntity(item)),
                dialog -> dialog.show(
                        fragmentActivity.getSupportFragmentManager(),
                        "StreamDialogEntry@"
                                + (dialog instanceof PlaylistAppendDialog ? "append" : "create")
                                + "_playlist"
                )
        )
    ),

    PLAY_WITH_KODI(R.string.play_with_kodi_title, (fragmentActivity, item) ->
            KoreUtils.playWithKore(fragmentActivity, Uri.parse(item.getUrl()))),

    SHARE(R.string.share, (fragmentActivity, item) ->
            ShareUtils.shareText(fragmentActivity, item.getName(), item.getUrl(),
                    item.getThumbnails())),

    /**
     * Opens a {@link DownloadDialog} after fetching some stream info.
     * If the user quits the current fragmentActivity, it will not open a DownloadDialog.
     */
    DOWNLOAD(R.string.download, (fragmentActivity, item) ->
            fetchStreamInfoAndSaveToDatabase(fragmentActivity, item.getServiceId(),
                    item.getUrl(), info -> {
                        // Ensure the fragment in the activity is attached
                        // and its state hasn't been saved to avoid
                        // showing dialog during lifecycle changes or when the activity is paused,
                        // e.g. by selecting the download option and opening a different fragment.
                        final FragmentManager fm = fragmentActivity.getSupportFragmentManager();
                        if (!fm.isStateSaved()) {
                            final DownloadDialog downloadDialog =
                                    new DownloadDialog(fragmentActivity, info);
                            downloadDialog.show(fm, "downloadDialog");
                        }
                    })
    ),

    OPEN_IN_BROWSER(R.string.open_in_browser, (fragmentActivity, item) ->
            ShareUtils.openUrlInBrowser(fragmentActivity, item.getUrl())),


    MARK_AS_WATCHED(R.string.mark_as_watched, (fragmentActivity, item) ->
        new HistoryRecordManager(fragmentActivity)
                .markAsWatched(item)
                .doOnError(error ->
                    ErrorUtil.showSnackbar(
                            fragmentActivity,
                            new ErrorInfo(
                                    error,
                                    UserAction.OPEN_INFO_ITEM_DIALOG,
                                    "Got an error when trying to mark as watched"
                            )
                    )
                )
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
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

    @NonNull
    public StreamDialogEntry toStreamDialogEntry() {
        return new StreamDialogEntry(resource, action);
    }

}
