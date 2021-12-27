package org.schabi.newpipe.util;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.Collections;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public enum StreamDialogDefaultEntry {
    //////////////////////////////////////
    // enum values with DEFAULT actions //
    //////////////////////////////////////

    SHOW_CHANNEL_DETAILS(R.string.show_channel_details, (fragment, item) -> {
        if (isNullOrEmpty(item.getUploaderUrl())) {
            final int serviceId = item.getServiceId();
            final String url = item.getUrl();
            Toast.makeText(fragment.getContext(), R.string.loading_channel_details,
                    Toast.LENGTH_SHORT).show();
            ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        NewPipeDatabase.getInstance(fragment.requireContext()).streamDAO()
                                .setUploaderUrl(serviceId, url, result.getUploaderUrl())
                                .subscribeOn(Schedulers.io()).subscribe();
                        openChannelFragment(fragment, item, result.getUploaderUrl());
                    }, throwable -> Toast.makeText(
                            // TODO: Open the Error Activity
                            fragment.getContext(),
                            R.string.error_show_channel_details,
                            Toast.LENGTH_SHORT
                    ).show());
        } else {
            openChannelFragment(fragment, item, item.getUploaderUrl());
        }
    }),

    /**
     * Enqueues the stream automatically to the current PlayerType.<br>
     * <br>
     * Info: Add this entry within showStreamDialog.
     */
    ENQUEUE(R.string.enqueue_stream, (fragment, item) ->
        NavigationHelper.enqueueOnPlayer(fragment.getContext(), new SinglePlayQueue(item))
    ),

    ENQUEUE_NEXT(R.string.enqueue_next_stream, (fragment, item) ->
        NavigationHelper.enqueueNextOnPlayer(fragment.getContext(), new SinglePlayQueue(item))
    ),

    START_HERE_ON_BACKGROUND(R.string.start_here_on_background, (fragment, item) ->
            NavigationHelper.playOnBackgroundPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), true)),

    START_HERE_ON_POPUP(R.string.start_here_on_popup, (fragment, item) ->
            NavigationHelper.playOnPopupPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), true)),

    SET_AS_PLAYLIST_THUMBNAIL(R.string.set_as_playlist_thumbnail, (fragment, item) -> {
    }), // has to be set manually

    DELETE(R.string.delete, (fragment, item) -> {
    }), // has to be set manually

    APPEND_PLAYLIST(R.string.add_to_playlist, (fragment, item) ->
        PlaylistDialog.createCorrespondingDialog(
                fragment.getContext(),
                Collections.singletonList(new StreamEntity(item)),
                dialog -> dialog.show(
                        fragment.getParentFragmentManager(),
                        "StreamDialogEntry@"
                                + (dialog instanceof PlaylistAppendDialog ? "append" : "create")
                                + "_playlist"
                )
        )
    ),

    PLAY_WITH_KODI(R.string.play_with_kodi_title, (fragment, item) -> {
        final Uri videoUrl = Uri.parse(item.getUrl());
        try {
            NavigationHelper.playWithKore(fragment.requireContext(), videoUrl);
        } catch (final Exception e) {
            KoreUtils.showInstallKoreDialog(fragment.requireActivity());
        }
    }),

    SHARE(R.string.share, (fragment, item) ->
            ShareUtils.shareText(fragment.requireContext(), item.getName(), item.getUrl(),
                    item.getThumbnailUrl())),

    OPEN_IN_BROWSER(R.string.open_in_browser, (fragment, item) ->
            ShareUtils.openUrlInBrowser(fragment.requireContext(), item.getUrl())),


    MARK_AS_WATCHED(R.string.mark_as_watched, (fragment, item) ->
        new HistoryRecordManager(fragment.getContext())
                .markAsWatched(item)
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

    /////////////////////////////////////////////
    // private method to open channel fragment //
    /////////////////////////////////////////////

    private static void openChannelFragment(@NonNull final Fragment fragment,
                                            @NonNull final StreamInfoItem item,
                                            final String uploaderUrl) {
        // For some reason `getParentFragmentManager()` doesn't work, but this does.
        NavigationHelper.openChannelFragment(
                fragment.requireActivity().getSupportFragmentManager(),
                item.getServiceId(), uploaderUrl, item.getUploaderName());
    }
}
