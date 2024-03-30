package org.schabi.newpipe.info_list.dialog

import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry.StreamDialogEntryAction
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.List
import java.util.function.Consumer

/**
 *
 *
 * This enum provides entries that are accepted
 * by the [InfoItemDialog.Builder].
 *
 *
 *
 * These entries contain a String [.resource] which is displayed in the dialog and
 * a default [.action] that is executed
 * when the entry is selected (via `onClick()`).
 * <br></br>
 * They action can be overridden by using the Builder's
 * [InfoItemDialog.Builder.setAction]
 * method.
 *
 */
enum class StreamDialogDefaultEntry(@field:StringRes @param:StringRes val resource: Int,
                                    val action: StreamDialogEntryAction) {
    SHOW_CHANNEL_DETAILS(R.string.show_channel_details, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem ->
        SparseItemUtil.fetchUploaderUrlIfSparse(fragment.requireContext(), item.getServiceId(), item.getUrl(),
                item.getUploaderUrl(), Consumer({ url: String? -> NavigationHelper.openChannelFragment(fragment, item, url) }))
    })
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType.
     */
    ENQUEUE(R.string.enqueue_stream, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem -> SparseItemUtil.fetchItemInfoIfSparse(fragment.requireContext(), item, Consumer({ singlePlayQueue: SinglePlayQueue? -> NavigationHelper.enqueueOnPlayer(fragment.getContext(), singlePlayQueue) })) })
    ),

    /**
     * Enqueues the stream automatically to the current PlayerType
     * after the currently playing stream.
     */
    ENQUEUE_NEXT(R.string.enqueue_next_stream, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem -> SparseItemUtil.fetchItemInfoIfSparse(fragment.requireContext(), item, Consumer({ singlePlayQueue: SinglePlayQueue? -> NavigationHelper.enqueueNextOnPlayer(fragment.getContext(), singlePlayQueue) })) })
    ),
    START_HERE_ON_BACKGROUND(R.string.start_here_on_background, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem ->
        SparseItemUtil.fetchItemInfoIfSparse(fragment.requireContext(), item, Consumer({ singlePlayQueue: SinglePlayQueue? ->
            NavigationHelper.playOnBackgroundPlayer(
                    fragment.getContext(), singlePlayQueue, true)
        }))
    })),
    START_HERE_ON_POPUP(R.string.start_here_on_popup, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem -> SparseItemUtil.fetchItemInfoIfSparse(fragment.requireContext(), item, Consumer({ singlePlayQueue: SinglePlayQueue? -> NavigationHelper.playOnPopupPlayer(fragment.getContext(), singlePlayQueue, true) })) })),
    SET_AS_PLAYLIST_THUMBNAIL(R.string.set_as_playlist_thumbnail, StreamDialogEntryAction({ fragment: Fragment?, item: StreamInfoItem? ->
        throw UnsupportedOperationException(("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()"))
    })),
    DELETE(R.string.delete, StreamDialogEntryAction({ fragment: Fragment?, item: StreamInfoItem? ->
        throw UnsupportedOperationException(("This needs to be implemented manually "
                + "by using InfoItemDialog.Builder.setAction()"))
    })),

    /**
     * Opens a [PlaylistDialog] to either append the stream to a playlist
     * or create a new playlist if there are no local playlists.
     */
    APPEND_PLAYLIST(R.string.add_to_playlist, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem? ->
        PlaylistDialog.Companion.createCorrespondingDialog(
                fragment.getContext(),
                List.of<StreamEntity?>(StreamEntity((item)!!)),
                Consumer<PlaylistDialog>({ dialog: PlaylistDialog ->
                    dialog.show(
                            fragment.getParentFragmentManager(),
                            ("StreamDialogEntry@"
                                    + (if (dialog is PlaylistAppendDialog) "append" else "create")
                                    + "_playlist")
                    )
                })
        )
    })
    ),
    PLAY_WITH_KODI(R.string.play_with_kodi_title, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem -> KoreUtils.playWithKore(fragment.requireContext(), Uri.parse(item.getUrl())) })),
    SHARE(R.string.share, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem ->
        ShareUtils.shareText(fragment.requireContext(), item.getName(), item.getUrl(),
                item.getThumbnails())
    })),

    /**
     * Opens a [DownloadDialog] after fetching some stream info.
     * If the user quits the current fragment, it will not open a DownloadDialog.
     */
    DOWNLOAD(R.string.download, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem ->
        SparseItemUtil.fetchStreamInfoAndSaveToDatabase(fragment.requireContext(), item.getServiceId(),
                item.getUrl(), Consumer({ info: StreamInfo ->
            if (fragment.getContext() != null) {
                val downloadDialog: DownloadDialog = DownloadDialog(fragment.requireContext(), info)
                downloadDialog.show(fragment.getChildFragmentManager(),
                        "downloadDialog")
            }
        }))
    })
    ),
    OPEN_IN_BROWSER(R.string.open_in_browser, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem -> ShareUtils.openUrlInBrowser(fragment.requireContext(), item.getUrl()) })),
    MARK_AS_WATCHED(R.string.mark_as_watched, StreamDialogEntryAction({ fragment: Fragment, item: StreamInfoItem ->
        HistoryRecordManager(fragment.getContext())
                .markAsWatched(item)
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    })
    );

    fun toStreamDialogEntry(): StreamDialogEntry {
        return StreamDialogEntry(resource, action)
    }
}
