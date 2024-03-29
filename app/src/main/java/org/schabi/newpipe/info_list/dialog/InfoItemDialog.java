package org.schabi.newpipe.info_list.dialog;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.external_communication.KoreUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dialog for a {@link StreamInfoItem}.
 * The dialog's content are actions that can be performed on the {@link StreamInfoItem}.
 * This dialog is mostly used for longpress context menus.
 */
public final class InfoItemDialog {
    private static final String TAG = Build.class.getSimpleName();
    /**
     * Ideally, {@link InfoItemDialog} would extend {@link AlertDialog}.
     * However, extending {@link AlertDialog} requires many additional lines
     * and brings more complexity to this class, especially the constructor.
     * To circumvent this, an {@link AlertDialog.Builder} is used in the constructor.
     * Its result is stored in this class variable to allow access via the {@link #show()} method.
     */
    private final AlertDialog dialog;

    private InfoItemDialog(@NonNull final Activity activity,
                           @NonNull final Fragment fragment,
                           @NonNull final StreamInfoItem info,
                           @NonNull final List<StreamDialogEntry> entries) {

        // Create the dialog's title
        final View bannerView = View.inflate(activity, R.layout.dialog_title, null);
        bannerView.setSelected(true);

        final TextView titleView = bannerView.findViewById(R.id.itemTitleView);
        titleView.setText(info.getName());

        final TextView detailsView = bannerView.findViewById(R.id.itemAdditionalDetails);
        if (info.getUploaderName() != null) {
            detailsView.setText(info.getUploaderName());
            detailsView.setVisibility(View.VISIBLE);
        } else {
            detailsView.setVisibility(View.GONE);
        }

        // Get the entry's descriptions which are displayed in the dialog
        final String[] items = entries.stream()
                .map(entry -> entry.getString(activity)).toArray(String[]::new);

        // Call an entry's action / onClick method when the entry is selected.
        final DialogInterface.OnClickListener action = (d, index) ->
            entries.get(index).action.onClick(fragment, info);

        dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(bannerView)
                .setItems(items, action)
                .create();

    }

    public void show() {
        dialog.show();
    }

    /**
     * <p>Builder to generate a {@link InfoItemDialog} for a {@link StreamInfoItem}.</p>
     * Use {@link #addEntry(StreamDialogDefaultEntry)}
     * and {@link #addAllEntries(StreamDialogDefaultEntry...)} to add options to the dialog.
     * <br>
     * Custom actions for entries can be set using
     * {@link #setAction(StreamDialogDefaultEntry, StreamDialogEntry.StreamDialogEntryAction)}.
     */
    public static class Builder {
        @NonNull private final Activity activity;
        @NonNull private final Context context;
        @NonNull private final StreamInfoItem infoItem;
        @NonNull private final Fragment fragment;
        @NonNull private final List<StreamDialogEntry> entries = new ArrayList<>();
        private final boolean addDefaultEntriesAutomatically;

        /**
         * <p>Create a {@link Builder builder} instance for a {@link StreamInfoItem}
         * that automatically adds the some default entries
         * at the top and bottom of the dialog.</p>
         * The dialog has the following structure:
         * <pre>
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | ENQUEUE                                    |
         *     | ENQUEUE_NEXT                               |
         *     | START_ON_BACKGROUND                        |
         *     | START_ON_POPUP                             |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | entries added manually with                |
         *     | addEntry() and addAllEntries()             |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | APPEND_PLAYLIST                            |
         *     | SHARE                                      |
         *     | OPEN_IN_BROWSER                            |
         *     | PLAY_WITH_KODI                             |
         *     | MARK_AS_WATCHED                            |
         *     | SHOW_CHANNEL_DETAILS                       |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         * </pre>
         * Please note that some entries are not added depending on the user's preferences,
         * the item's {@link StreamType} and the current player state.
         *
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem the item for this dialog; all entries and their actions work with
         *                this {@link StreamInfoItem}
         * @throws IllegalArgumentException if <code>activity, context</code>
         *         or resources is <code>null</code>
         */
        public Builder(final Activity activity,
                       final Context context,
                       @NonNull final Fragment fragment,
                       @NonNull final StreamInfoItem infoItem) {
            this(activity, context, fragment, infoItem, true);
        }

        /**
         * <p>Create an instance of this {@link Builder} for a {@link StreamInfoItem}.</p>
         * <p>If {@code addDefaultEntriesAutomatically} is set to {@code true},
         * some default entries are added to the top and bottom of the dialog.</p>
         * The dialog has the following structure:
         * <pre>
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | ENQUEUE                                    |
         *     | ENQUEUE_NEXT                               |
         *     | START_ON_BACKGROUND                        |
         *     | START_ON_POPUP                             |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | entries added manually with                |
         *     | addEntry() and addAllEntries()             |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         *     | APPEND_PLAYLIST                            |
         *     | SHARE                                      |
         *     | OPEN_IN_BROWSER                            |
         *     | PLAY_WITH_KODI                             |
         *     | MARK_AS_WATCHED                            |
         *     | SHOW_CHANNEL_DETAILS                       |
         *     + - - - - - - - - - - - - - - - - - - - - - -+
         * </pre>
         * Please note that some entries are not added depending on the user's preferences,
         * the item's {@link StreamType} and the current player state.
         *
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem
         * @param addDefaultEntriesAutomatically
         *        whether default entries added with {@link #addDefaultBeginningEntries()}
         *        and {@link #addDefaultEndEntries()} are added automatically when generating
         *        the {@link InfoItemDialog}.
         *        <br/>
         *        Entries added with {@link #addEntry(StreamDialogDefaultEntry)} and
         *        {@link #addAllEntries(StreamDialogDefaultEntry...)} are added in between.
         * @throws IllegalArgumentException if <code>activity, context</code>
         * or resources is <code>null</code>
         */
        public Builder(final Activity activity,
                       final Context context,
                       @NonNull final Fragment fragment,
                       @NonNull final StreamInfoItem infoItem,
                       final boolean addDefaultEntriesAutomatically) {
            if (activity == null || context == null || context.getResources() == null) {
                if (DEBUG) {
                    Log.d(TAG, "activity, context or resources is null: activity = "
                            + activity + ", context = " + context);
                }
                throw new IllegalArgumentException("activity, context or resources is null");
            }
            this.activity = activity;
            this.context = context;
            this.fragment = fragment;
            this.infoItem = infoItem;
            this.addDefaultEntriesAutomatically = addDefaultEntriesAutomatically;
            if (addDefaultEntriesAutomatically) {
                addDefaultBeginningEntries();
            }
        }

        /**
         * Adds a new entry and appends it to the current entry list.
         * @param entry the entry to add
         * @return the current {@link Builder} instance
         */
        public Builder addEntry(@NonNull final StreamDialogDefaultEntry entry) {
            entries.add(entry.toStreamDialogEntry());
            return this;
        }

        /**
         * Adds new entries. These are appended to the current entry list.
         * @param newEntries the entries to add
         * @return the current {@link Builder} instance
         */
        public Builder addAllEntries(@NonNull final StreamDialogDefaultEntry... newEntries) {
            Stream.of(newEntries).forEach(this::addEntry);
            return this;
        }

        /**
         * <p>Change an entries' action that is called when the entry is selected.</p>
         * <p><strong>Warning:</strong> Only use this method when the entry has been already added.
         * Changing the action of an entry which has not been added to the Builder yet
         * does not have an effect.</p>
         * @param entry the entry to change
         * @param action the action to perform when the entry is selected
         * @return the current {@link Builder} instance
         */
        public Builder setAction(@NonNull final StreamDialogDefaultEntry entry,
                              @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).resource == entry.resource) {
                    entries.set(i, new StreamDialogEntry(entry.resource, action));
                    return this;
                }
            }
            return this;
        }

        /**
         * Adds {@link StreamDialogDefaultEntry#ENQUEUE} if the player is open and
         * {@link StreamDialogDefaultEntry#ENQUEUE_NEXT} if there are multiple streams
         * in the play queue.
         * @return the current {@link Builder} instance
         */
        public Builder addEnqueueEntriesIfNeeded() {
            final PlayerHolder holder = PlayerHolder.getInstance();
            if (holder.isPlayQueueReady()) {
                addEntry(StreamDialogDefaultEntry.ENQUEUE);

                if (holder.getQueuePosition() < holder.getQueueSize() - 1) {
                    addEntry(StreamDialogDefaultEntry.ENQUEUE_NEXT);
                }
            }
            return this;
        }

        /**
         * Adds the {@link StreamDialogDefaultEntry#START_HERE_ON_BACKGROUND}.
         * If the {@link #infoItem} is not a pure audio (live) stream,
         * {@link StreamDialogDefaultEntry#START_HERE_ON_POPUP} is added, too.
         * @return the current {@link Builder} instance
         */
        public Builder addStartHereEntries() {
            addEntry(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND);
            if (!StreamTypeUtil.isAudio(infoItem.getStreamType())) {
                addEntry(StreamDialogDefaultEntry.START_HERE_ON_POPUP);
            }
            return this;
        }

        /**
         * Adds {@link StreamDialogDefaultEntry#MARK_AS_WATCHED} if the watch history is enabled
         * and the stream is not a livestream.
         * @return the current {@link Builder} instance
         */
        public Builder addMarkAsWatchedEntryIfNeeded() {
            final boolean isWatchHistoryEnabled = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_watch_history_key), false);
            if (isWatchHistoryEnabled && !StreamTypeUtil.isLiveStream(infoItem.getStreamType())) {
                addEntry(StreamDialogDefaultEntry.MARK_AS_WATCHED);
            }
            return this;
        }

        /**
         * Adds the {@link StreamDialogDefaultEntry#PLAY_WITH_KODI} entry if it is needed.
         * @return the current {@link Builder} instance
         */
        public Builder addPlayWithKodiEntryIfNeeded() {
            if (KoreUtils.shouldShowPlayWithKodi(context, infoItem.getServiceId())) {
                addEntry(StreamDialogDefaultEntry.PLAY_WITH_KODI);
            }
            return this;
        }

        /**
         * Add the entries which are usually at the top of the action list.
         * <br/>
         * This method adds the "enqueue" (see {@link #addEnqueueEntriesIfNeeded()})
         * and "start here" (see {@link #addStartHereEntries()} entries.
         * @return the current {@link Builder} instance
         */
        public Builder addDefaultBeginningEntries() {
            addEnqueueEntriesIfNeeded();
            addStartHereEntries();
            return this;
        }

        /**
         * Add the entries which are usually at the bottom of the action list.
         * @return the current {@link Builder} instance
         */
        public Builder addDefaultEndEntries() {
            addAllEntries(
                    StreamDialogDefaultEntry.DOWNLOAD,
                    StreamDialogDefaultEntry.APPEND_PLAYLIST,
                    StreamDialogDefaultEntry.SHARE,
                    StreamDialogDefaultEntry.OPEN_IN_BROWSER
            );
            addPlayWithKodiEntryIfNeeded();
            addMarkAsWatchedEntryIfNeeded();
            addEntry(StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS);
            return this;
        }

        /**
         * Creates the {@link InfoItemDialog}.
         * @return a new instance of {@link InfoItemDialog}
         */
        public InfoItemDialog create() {
            if (addDefaultEntriesAutomatically) {
                addDefaultEndEntries();
            }
            return new InfoItemDialog(this.activity, this.fragment, this.infoItem, this.entries);
        }

        public static void reportErrorDuringInitialization(final Throwable throwable,
                                                           final InfoItem item) {
            ErrorUtil.showSnackbar(App.getApp().getBaseContext(), new ErrorInfo(
                    throwable,
                    UserAction.OPEN_INFO_ITEM_DIALOG,
                    "none",
                    item.getServiceId()));
        }
    }
}
