package org.schabi.newpipe.info_list.dialog

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry.StreamDialogEntryAction
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.external_communication.KoreUtils
import java.lang.IllegalArgumentException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntFunction
import java.util.stream.Stream

/**
 * Dialog for a [StreamInfoItem].
 * The dialog's content are actions that can be performed on the [StreamInfoItem].
 * This dialog is mostly used for longpress context menus.
 */
class InfoItemDialog private constructor(activity: Activity,
                                         fragment: Fragment,
                                         info: StreamInfoItem,
                                         entries: List<StreamDialogEntry>) {
    /**
     * Ideally, [InfoItemDialog] would extend [AlertDialog].
     * However, extending [AlertDialog] requires many additional lines
     * and brings more complexity to this class, especially the constructor.
     * To circumvent this, an [AlertDialog.Builder] is used in the constructor.
     * Its result is stored in this class variable to allow access via the [.show] method.
     */
    private val dialog: AlertDialog

    init {

        // Create the dialog's title
        val bannerView: View = View.inflate(activity, R.layout.dialog_title, null)
        bannerView.setSelected(true)
        val titleView: TextView = bannerView.findViewById(R.id.itemTitleView)
        titleView.setText(info.getName())
        val detailsView: TextView = bannerView.findViewById(R.id.itemAdditionalDetails)
        if (info.getUploaderName() != null) {
            detailsView.setText(info.getUploaderName())
            detailsView.setVisibility(View.VISIBLE)
        } else {
            detailsView.setVisibility(View.GONE)
        }

        // Get the entry's descriptions which are displayed in the dialog
        val items: Array<String> = entries.stream()
                .map<String?>(Function<StreamDialogEntry, String?>({ entry: StreamDialogEntry -> entry.getString(activity) })).toArray<String>(IntFunction<Array<String>>({ _Dummy_.__Array__() }))

        // Call an entry's action / onClick method when the entry is selected.
        val action: DialogInterface.OnClickListener = DialogInterface.OnClickListener({ d: DialogInterface?, index: Int -> entries.get(index).action.onClick(fragment, info) })
        dialog = AlertDialog.Builder(activity)
                .setCustomTitle(bannerView)
                .setItems(items, action)
                .create()
    }

    fun show() {
        dialog.show()
    }

    /**
     *
     * Builder to generate a [InfoItemDialog] for a [StreamInfoItem].
     * Use [.addEntry]
     * and [.addAllEntries] to add options to the dialog.
     * <br></br>
     * Custom actions for entries can be set using
     * [.setAction].
     */
    class Builder @JvmOverloads constructor(activity: Activity,
                                            context: Context,
                                            fragment: Fragment,
                                            infoItem: StreamInfoItem,
                                            addDefaultEntriesAutomatically: Boolean = true) {
        private val activity: Activity
        private val context: Context
        private val infoItem: StreamInfoItem
        private val fragment: Fragment
        private val entries: MutableList<StreamDialogEntry> = ArrayList()
        private val addDefaultEntriesAutomatically: Boolean
        /**
         *
         * Create an instance of this [Builder] for a [StreamInfoItem].
         *
         * If `addDefaultEntriesAutomatically` is set to `true`,
         * some default entries are added to the top and bottom of the dialog.
         * The dialog has the following structure:
         * <pre>
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | ENQUEUE                                    |
         * | ENQUEUE_NEXT                               |
         * | START_ON_BACKGROUND                        |
         * | START_ON_POPUP                             |
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | entries added manually with                |
         * | addEntry() and addAllEntries()             |
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | APPEND_PLAYLIST                            |
         * | SHARE                                      |
         * | OPEN_IN_BROWSER                            |
         * | PLAY_WITH_KODI                             |
         * | MARK_AS_WATCHED                            |
         * | SHOW_CHANNEL_DETAILS                       |
         * + - - - - - - - - - - - - - - - - - - - - - -+
        </pre> *
         * Please note that some entries are not added depending on the user's preferences,
         * the item's [StreamType] and the current player state.
         *
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem
         * @param addDefaultEntriesAutomatically
         * whether default entries added with [.addDefaultBeginningEntries]
         * and [.addDefaultEndEntries] are added automatically when generating
         * the [InfoItemDialog].
         * <br></br>
         * Entries added with [.addEntry] and
         * [.addAllEntries] are added in between.
         * @throws IllegalArgumentException if `activity, context`
         * or resources is `null`
         */
        /**
         *
         * Create a [builder][Builder] instance for a [StreamInfoItem]
         * that automatically adds the some default entries
         * at the top and bottom of the dialog.
         * The dialog has the following structure:
         * <pre>
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | ENQUEUE                                    |
         * | ENQUEUE_NEXT                               |
         * | START_ON_BACKGROUND                        |
         * | START_ON_POPUP                             |
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | entries added manually with                |
         * | addEntry() and addAllEntries()             |
         * + - - - - - - - - - - - - - - - - - - - - - -+
         * | APPEND_PLAYLIST                            |
         * | SHARE                                      |
         * | OPEN_IN_BROWSER                            |
         * | PLAY_WITH_KODI                             |
         * | MARK_AS_WATCHED                            |
         * | SHOW_CHANNEL_DETAILS                       |
         * + - - - - - - - - - - - - - - - - - - - - - -+
        </pre> *
         * Please note that some entries are not added depending on the user's preferences,
         * the item's [StreamType] and the current player state.
         *
         * @param activity
         * @param context
         * @param fragment
         * @param infoItem the item for this dialog; all entries and their actions work with
         * this [StreamInfoItem]
         * @throws IllegalArgumentException if `activity, context`
         * or resources is `null`
         */
        init {
            if ((activity == null) || (context == null) || (context.getResources() == null)) {
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, ("activity, context or resources is null: activity = "
                            + activity + ", context = " + context))
                }
                throw IllegalArgumentException("activity, context or resources is null")
            }
            this.activity = activity
            this.context = context
            this.fragment = fragment
            this.infoItem = infoItem
            this.addDefaultEntriesAutomatically = addDefaultEntriesAutomatically
            if (addDefaultEntriesAutomatically) {
                addDefaultBeginningEntries()
            }
        }

        /**
         * Adds a new entry and appends it to the current entry list.
         * @param entry the entry to add
         * @return the current [Builder] instance
         */
        fun addEntry(entry: StreamDialogDefaultEntry): Builder {
            entries.add(entry.toStreamDialogEntry())
            return this
        }

        /**
         * Adds new entries. These are appended to the current entry list.
         * @param newEntries the entries to add
         * @return the current [Builder] instance
         */
        fun addAllEntries(vararg newEntries: StreamDialogDefaultEntry): Builder {
            Stream.of(*newEntries).forEach(Consumer({ entry: StreamDialogDefaultEntry -> addEntry(entry) }))
            return this
        }

        /**
         *
         * Change an entries' action that is called when the entry is selected.
         *
         * **Warning:** Only use this method when the entry has been already added.
         * Changing the action of an entry which has not been added to the Builder yet
         * does not have an effect.
         * @param entry the entry to change
         * @param action the action to perform when the entry is selected
         * @return the current [Builder] instance
         */
        fun setAction(entry: StreamDialogDefaultEntry,
                      action: StreamDialogEntryAction): Builder {
            for (i in entries.indices) {
                if (entries.get(i).resource == entry.resource) {
                    entries.set(i, StreamDialogEntry(entry.resource, action))
                    return this
                }
            }
            return this
        }

        /**
         * Adds [StreamDialogDefaultEntry.ENQUEUE] if the player is open and
         * [StreamDialogDefaultEntry.ENQUEUE_NEXT] if there are multiple streams
         * in the play queue.
         * @return the current [Builder] instance
         */
        fun addEnqueueEntriesIfNeeded(): Builder {
            val holder: PlayerHolder? = PlayerHolder.Companion.getInstance()
            if (holder!!.isPlayQueueReady()) {
                addEntry(StreamDialogDefaultEntry.ENQUEUE)
                if (holder.getQueuePosition() < holder.getQueueSize() - 1) {
                    addEntry(StreamDialogDefaultEntry.ENQUEUE_NEXT)
                }
            }
            return this
        }

        /**
         * Adds the [StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND].
         * If the [.infoItem] is not a pure audio (live) stream,
         * [StreamDialogDefaultEntry.START_HERE_ON_POPUP] is added, too.
         * @return the current [Builder] instance
         */
        fun addStartHereEntries(): Builder {
            addEntry(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND)
            if (!StreamTypeUtil.isAudio(infoItem.getStreamType())) {
                addEntry(StreamDialogDefaultEntry.START_HERE_ON_POPUP)
            }
            return this
        }

        /**
         * Adds [StreamDialogDefaultEntry.MARK_AS_WATCHED] if the watch history is enabled
         * and the stream is not a livestream.
         * @return the current [Builder] instance
         */
        fun addMarkAsWatchedEntryIfNeeded(): Builder {
            val isWatchHistoryEnabled: Boolean = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_watch_history_key), false)
            if (isWatchHistoryEnabled && !StreamTypeUtil.isLiveStream(infoItem.getStreamType())) {
                addEntry(StreamDialogDefaultEntry.MARK_AS_WATCHED)
            }
            return this
        }

        /**
         * Adds the [StreamDialogDefaultEntry.PLAY_WITH_KODI] entry if it is needed.
         * @return the current [Builder] instance
         */
        fun addPlayWithKodiEntryIfNeeded(): Builder {
            if (KoreUtils.shouldShowPlayWithKodi(context, infoItem.getServiceId())) {
                addEntry(StreamDialogDefaultEntry.PLAY_WITH_KODI)
            }
            return this
        }

        /**
         * Add the entries which are usually at the top of the action list.
         * <br></br>
         * This method adds the "enqueue" (see [.addEnqueueEntriesIfNeeded])
         * and "start here" (see [.addStartHereEntries] entries.
         * @return the current [Builder] instance
         */
        fun addDefaultBeginningEntries(): Builder {
            addEnqueueEntriesIfNeeded()
            addStartHereEntries()
            return this
        }

        /**
         * Add the entries which are usually at the bottom of the action list.
         * @return the current [Builder] instance
         */
        fun addDefaultEndEntries(): Builder {
            addAllEntries(
                    StreamDialogDefaultEntry.DOWNLOAD,
                    StreamDialogDefaultEntry.APPEND_PLAYLIST,
                    StreamDialogDefaultEntry.SHARE,
                    StreamDialogDefaultEntry.OPEN_IN_BROWSER
            )
            addPlayWithKodiEntryIfNeeded()
            addMarkAsWatchedEntryIfNeeded()
            addEntry(StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS)
            return this
        }

        /**
         * Creates the [InfoItemDialog].
         * @return a new instance of [InfoItemDialog]
         */
        fun create(): InfoItemDialog {
            if (addDefaultEntriesAutomatically) {
                addDefaultEndEntries()
            }
            return InfoItemDialog(activity, fragment, infoItem, entries)
        }

        companion object {
            fun reportErrorDuringInitialization(throwable: Throwable?,
                                                item: InfoItem) {
                showSnackbar(App.Companion.getApp().getBaseContext(), ErrorInfo(
                        (throwable)!!,
                        UserAction.OPEN_INFO_ITEM_DIALOG,
                        "none",
                        item.getServiceId()))
            }
        }
    }

    companion object {
        private val TAG: String = Build::class.java.getSimpleName()
    }
}
