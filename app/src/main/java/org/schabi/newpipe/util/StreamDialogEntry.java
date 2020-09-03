package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.Collections;

public enum StreamDialogEntry {
    //////////////////////////////////////
    // enum values with DEFAULT actions //
    //////////////////////////////////////

    enqueue_on_background(R.string.enqueue_on_background, (context, item) ->
            NavigationHelper.enqueueOnBackgroundPlayer(context,
                    new SinglePlayQueue(item), false)),

    enqueue_on_popup(R.string.enqueue_on_popup, (context, item) ->
            NavigationHelper.enqueueOnPopupPlayer(context,
                    new SinglePlayQueue(item), false)),

    start_here_on_background(R.string.start_here_on_background, (context, item) ->
            NavigationHelper.playOnBackgroundPlayer(context,
                    new SinglePlayQueue(item), true)),

    start_here_on_popup(R.string.start_here_on_popup, (context, item) ->
            NavigationHelper.playOnPopupPlayer(context,
                    new SinglePlayQueue(item), true)),

    set_as_playlist_thumbnail(R.string.set_as_playlist_thumbnail, (context, item) -> {
    }), // has to be set manually

    delete(R.string.delete, (context, item) -> {
    }), // has to be set manually
    append_playlist(R.string.append_playlist, (fragment, item) -> {
            PlaylistAppendDialog.fromStreamInfoItems(Collections.singletonList(item))
                    .show("StreamDialogEntry@append_playlist");
    }),

    share(R.string.share, (context, item) ->
            ShareUtils.shareUrl(context, item.getName(), item.getUrl()));


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

    public static void clickOn(final int which, final Context context,
                               final StreamInfoItem infoItem) {
        if (enabledEntries[which].customAction == null) {
            enabledEntries[which].defaultAction.onClick(context, infoItem);
        } else {
            enabledEntries[which].customAction.onClick(context, infoItem);
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
        void onClick(Context context, StreamInfoItem infoItem);
    }
}
