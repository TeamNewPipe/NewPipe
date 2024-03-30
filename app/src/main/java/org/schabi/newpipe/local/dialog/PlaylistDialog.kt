package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.StateSaver.WriteRead
import java.util.Objects
import java.util.Queue
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream

abstract class PlaylistDialog() : DialogFragment(), WriteRead {
    /*//////////////////////////////////////////////////////////////////////////
    // Getter + Setter
    ////////////////////////////////////////////////////////////////////////// */ var onDismissListener: DialogInterface.OnDismissListener? = null
    var streamEntities: List<StreamEntity>? = null
        protected set
    private var savedState: org.schabi.newpipe.util.SavedState? = null

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedState = StateSaver.tryToRestore(savedInstanceState, this)
    }

    public override fun onDestroy() {
        super.onDestroy()
        StateSaver.onDestroy(savedState)
    }

    public override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog: Dialog = super.onCreateDialog(savedInstanceState)
        //remove title
        val window: Window? = dialog.getWindow()
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE)
        }
        return dialog
    }

    public override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (onDismissListener != null) {
            onDismissListener!!.onDismiss(dialog)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    ////////////////////////////////////////////////////////////////////////// */
    public override fun generateSuffix(): String? {
        val size: Int = if (streamEntities == null) 0 else streamEntities!!.size
        return "." + size + ".list"
    }

    public override fun writeTo(objectsToSave: Queue<Any?>) {
        objectsToSave.add(streamEntities)
    }

    public override fun readFrom(savedObjects: Queue<Any>) {
        streamEntities = savedObjects.poll() as List<StreamEntity>?
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (getActivity() != null) {
            savedState = StateSaver.tryToSave(getActivity()!!.isChangingConfigurations(),
                    savedState, outState, this)
        }
    }

    companion object {
        /*//////////////////////////////////////////////////////////////////////////
    // Dialog creation
    ////////////////////////////////////////////////////////////////////////// */
        /**
         * Creates a [PlaylistAppendDialog] when playlists exists,
         * otherwise a [PlaylistCreationDialog].
         *
         * @param context        context used for accessing the database
         * @param streamEntities used for crating the dialog
         * @param onExec         execution that should occur after a dialog got created, e.g. showing it
         * @return the disposable that was created
         */
        fun createCorrespondingDialog(
                context: Context?,
                streamEntities: List<StreamEntity?>?,
                onExec: java.util.function.Consumer<PlaylistDialog>): Disposable {
            return LocalPlaylistManager(NewPipeDatabase.getInstance((context)!!))
                    .hasPlaylists()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(io.reactivex.rxjava3.functions.Consumer<Boolean?>({ hasPlaylists: Boolean? -> onExec.accept(if ((hasPlaylists)!!) PlaylistAppendDialog.Companion.newInstance(streamEntities) else PlaylistCreationDialog.Companion.newInstance(streamEntities)) })
                    )
        }

        /**
         * Creates a [PlaylistAppendDialog] when playlists exists,
         * otherwise a [PlaylistCreationDialog]. If the player's play queue is null or empty, no
         * dialog will be created.
         *
         * @param player          the player from which to extract the context and the play queue
         * @param fragmentManager the fragment manager to use to show the dialog
         * @return the disposable that was created
         */
        fun showForPlayQueue(
                player: Player?,
                fragmentManager: FragmentManager): Disposable {
            val streamEntities: List<StreamEntity?> = Stream.of(player!!.getPlayQueue())
                    .filter(Predicate({ obj: PlayQueue? -> Objects.nonNull(obj) }))
                    .flatMap(Function<PlayQueue?, Stream<out PlayQueueItem?>>({ playQueue: PlayQueue? -> playQueue!!.getStreams().stream() }))
                    .map(Function({ item: PlayQueueItem? -> StreamEntity((item)!!) }))
                    .collect(Collectors.toList())
            if (streamEntities.isEmpty()) {
                return Disposable.empty()
            }
            return createCorrespondingDialog(player.getContext(), streamEntities,
                    java.util.function.Consumer({ dialog: PlaylistDialog -> dialog.show(fragmentManager, "PlaylistDialog") }))
        }
    }
}
