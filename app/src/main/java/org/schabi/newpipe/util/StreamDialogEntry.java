package org.schabi.newpipe.util;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistCreationDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.player.MainPlayer.PlayerType.AUDIO;
import static org.schabi.newpipe.player.MainPlayer.PlayerType.POPUP;

public enum StreamDialogEntry {
    //////////////////////////////////////
    // enum values with DEFAULT actions //
    //////////////////////////////////////

    show_channel_details(R.string.show_channel_details, (fragment, item) -> {
        if (isNullOrEmpty(item.getUploaderUrl())) {
            final int serviceId = item.getServiceId();
            final String url = item.getUrl();
            Toast.makeText(fragment.getContext(), R.string.loading_channel_details,
                    Toast.LENGTH_SHORT).show();
            ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        NewPipeDatabase.getInstance(fragment.getContext()).streamDAO()
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
    enqueue(R.string.enqueue_stream, (fragment, item) -> {
        final MainPlayer.PlayerType type = PlayerHolder.getInstance().getType();

        if (type == AUDIO) {
            NavigationHelper.enqueueOnBackgroundPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), false);
        } else if (type == POPUP) {
            NavigationHelper.enqueueOnPopupPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), false);
        } else /* type == VIDEO */ {
            NavigationHelper.enqueueOnVideoPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), false);
        }
    }),

    start_here_on_background(R.string.start_here_on_background, (fragment, item) ->
            NavigationHelper.playOnBackgroundPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), true)),

    start_here_on_popup(R.string.start_here_on_popup, (fragment, item) ->
            NavigationHelper.playOnPopupPlayer(fragment.getContext(),
                    new SinglePlayQueue(item), true)),

    set_as_playlist_thumbnail(R.string.set_as_playlist_thumbnail, (fragment, item) -> {
    }), // has to be set manually

    delete(R.string.delete, (fragment, item) -> {
    }), // has to be set manually

    append_playlist(R.string.append_playlist, (fragment, item) -> {
        final PlaylistAppendDialog d = PlaylistAppendDialog
                .fromStreamInfoItems(Collections.singletonList(item));

        PlaylistAppendDialog.onPlaylistFound(fragment.getContext(),
            () -> d.show(fragment.getParentFragmentManager(), "StreamDialogEntry@append_playlist"),
            () -> PlaylistCreationDialog.newInstance(d)
                    .show(fragment.getParentFragmentManager(), "StreamDialogEntry@create_playlist")
        );
    }),

    play_with_kodi(R.string.play_with_kodi_title, (fragment, item) -> {
        final Uri videoUrl = Uri.parse(item.getUrl());
        try {
            NavigationHelper.playWithKore(fragment.requireContext(), videoUrl);
        } catch (final Exception e) {
            KoreUtils.showInstallKoreDialog(fragment.getActivity());
        }
    }),

    share(R.string.share, (fragment, item) ->
            ShareUtils.shareText(fragment.getContext(), item.getName(), item.getUrl(),
                    item.getThumbnailUrl())),

    open_in_browser(R.string.open_in_browser, (fragment, item) ->
            ShareUtils.openUrlInBrowser(fragment.getContext(), item.getUrl())),


    mark_as_watched(R.string.mark_as_watched, (fragment, item) -> {
        new HistoryRecordManager(fragment.getContext())
                .markAsWatched(item)
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    });

    ///////////////
    // variables //
    ///////////////

    private static StreamDialogEntry[] enabledEntries;
    private final int resource;
    private final StreamDialogEntryAction defaultAction;
    private StreamDialogEntryAction customAction;

    StreamDialogEntry(final int resource, final StreamDialogEntryAction defaultAction) {
        this.resource = resource;
        this.defaultAction = defaultAction;
        this.customAction = null;
    }


    ///////////////////////////////////////////////////////
    // non-static methods to initialize and edit entries //
    ///////////////////////////////////////////////////////

    public static void setEnabledEntries(final List<StreamDialogEntry> entries) {
        setEnabledEntries(entries.toArray(new StreamDialogEntry[0]));
    }

    /**
     * To be called before using {@link #setCustomAction(StreamDialogEntryAction)}.
     *
     * @param entries the entries to be enabled
     */
    public static void setEnabledEntries(final StreamDialogEntry... entries) {
        // cleanup from last time StreamDialogEntry was used
        for (final StreamDialogEntry streamDialogEntry : values()) {
            streamDialogEntry.customAction = null;
        }

        enabledEntries = entries;
    }

    public static String[] getCommands(final Context context) {
        final String[] commands = new String[enabledEntries.length];
        for (int i = 0; i != enabledEntries.length; ++i) {
            commands[i] = context.getResources().getString(enabledEntries[i].resource);
        }

        return commands;
    }


    ////////////////////////////////////////////////
    // static methods that act on enabled entries //
    ////////////////////////////////////////////////

    public static void clickOn(final int which, final Fragment fragment,
                               final StreamInfoItem infoItem) {
        if (enabledEntries[which].customAction == null) {
            enabledEntries[which].defaultAction.onClick(fragment, infoItem);
        } else {
            enabledEntries[which].customAction.onClick(fragment, infoItem);
        }
    }

    /**
     * Can be used after {@link #setEnabledEntries(StreamDialogEntry...)} has been called.
     *
     * @param action the action to be set
     */
    public void setCustomAction(final StreamDialogEntryAction action) {
        this.customAction = action;
    }

    public interface StreamDialogEntryAction {
        void onClick(Fragment fragment, StreamInfoItem infoItem);
    }

    /////////////////////////////////////////////
    // private method to open channel fragment //
    /////////////////////////////////////////////

    private static void openChannelFragment(final Fragment fragment,
                                            final StreamInfoItem item,
                                            final String uploaderUrl) {
        // For some reason `getParentFragmentManager()` doesn't work, but this does.
        NavigationHelper.openChannelFragment(
                fragment.requireActivity().getSupportFragmentManager(),
                item.getServiceId(), uploaderUrl, item.getUploaderName());
    }
}
