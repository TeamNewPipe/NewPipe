package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.ui.components.menu.icons.BackgroundFromHere
import org.schabi.newpipe.ui.components.menu.icons.PlayFromHere
import org.schabi.newpipe.ui.components.menu.icons.PopupFromHere
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.ShareUtils

data class LongPressAction(
    val type: Type,
    val action: (context: Context) -> Unit,
    val enabled: (isPlayerRunning: Boolean) -> Boolean = { true },
) {
    enum class Type(
        @StringRes val label: Int,
        val icon: ImageVector,
    ) {
        Enqueue(R.string.enqueue, Icons.Default.AddToQueue),
        EnqueueNext(R.string.enqueue_next_stream, Icons.Default.QueuePlayNext),
        Background(R.string.controls_background_title, Icons.Default.Headset),
        Popup(R.string.controls_popup_title, Icons.Default.PictureInPicture),
        Play(R.string.play, Icons.Default.PlayArrow),
        BackgroundFromHere(R.string.background_from_here, Icons.Default.BackgroundFromHere),
        PopupFromHere(R.string.popup_from_here, Icons.Default.PopupFromHere),
        PlayFromHere(R.string.play_from_here, Icons.Default.PlayFromHere),
        PlayWithKodi(R.string.play_with_kodi_title, Icons.Default.Cast),
        Download(R.string.download, Icons.Default.Download),
        AddToPlaylist(R.string.add_to_playlist, Icons.AutoMirrored.Default.PlaylistAdd),
        Share(R.string.share, Icons.Default.Share),
        OpenInBrowser(R.string.open_in_browser, Icons.Default.OpenInBrowser),
        ShowChannelDetails(R.string.show_channel_details, Icons.Default.Person),
        MarkAsWatched(R.string.mark_as_watched, Icons.Default.Done),
        Delete(R.string.delete, Icons.Default.Delete),
        Rename(R.string.rename, Icons.Default.Edit),
        SetAsPlaylistThumbnail(R.string.set_as_playlist_thumbnail, Icons.Default.Image),
        UnsetPlaylistThumbnail(R.string.unset_playlist_thumbnail, Icons.Default.HideImage),
        Unsubscribe(R.string.unsubscribe, Icons.Default.Delete),
        ;

        // TODO allow actions to return disposables
        // TODO add actions that use the whole list the item belongs to (see wholeListQueue)

        fun buildAction(
            enabled: (isPlayerRunning: Boolean) -> Boolean = { true },
            action: (context: Context) -> Unit,
        ) = LongPressAction(this, action, enabled)

        companion object {
            // ShowChannelDetails is not enabled by default, since navigating to channel details can
            // also be done by clicking on the uploader name in the long press menu header
            val DefaultEnabledActions: Array<Type> = arrayOf(
                Enqueue, EnqueueNext, Background, Popup, BackgroundFromHere, Download,
                AddToPlaylist, Share, OpenInBrowser, MarkAsWatched, Delete,
                Rename, SetAsPlaylistThumbnail, UnsetPlaylistThumbnail, Unsubscribe
            )
        }
    }

    companion object {
        private fun buildPlayerActionList(queue: () -> PlayQueue): List<LongPressAction> {
            return listOf(
                Type.Enqueue.buildAction({ isPlayerRunning -> isPlayerRunning }) { context ->
                    NavigationHelper.enqueueOnPlayer(context, queue())
                },
                Type.EnqueueNext.buildAction({ isPlayerRunning -> isPlayerRunning }) { context ->
                    NavigationHelper.enqueueNextOnPlayer(context, queue())
                },
                Type.Background.buildAction { context ->
                    NavigationHelper.playOnBackgroundPlayer(context, queue(), true)
                },
                Type.Popup.buildAction { context ->
                    NavigationHelper.playOnPopupPlayer(context, queue(), true)
                },
                Type.Play.buildAction { context ->
                    NavigationHelper.playOnMainPlayer(context, queue(), false)
                },
            )
        }

        private fun buildPlayerFromHereActionList(queueFromHere: () -> PlayQueue): List<LongPressAction> {
            return listOf(
                Type.BackgroundFromHere.buildAction { context ->
                    NavigationHelper.playOnBackgroundPlayer(context, queueFromHere(), true)
                },
                Type.PopupFromHere.buildAction { context ->
                    NavigationHelper.playOnPopupPlayer(context, queueFromHere(), true)
                },
                Type.PlayFromHere.buildAction { context ->
                    NavigationHelper.playOnMainPlayer(context, queueFromHere(), false)
                },
            )
        }

        private fun buildShareActionList(item: InfoItem): List<LongPressAction> {
            return listOf(
                Type.Share.buildAction { context ->
                    ShareUtils.shareText(context, item.name, item.url, item.thumbnails)
                },
                Type.OpenInBrowser.buildAction { context ->
                    ShareUtils.openUrlInBrowser(context, item.url)
                },
            )
        }

        private fun buildShareActionList(name: String, url: String, thumbnailUrl: String?): List<LongPressAction> {
            return listOf(
                Type.Share.buildAction { context ->
                    ShareUtils.shareText(context, name, url, thumbnailUrl)
                },
                Type.OpenInBrowser.buildAction { context ->
                    ShareUtils.openUrlInBrowser(context, url)
                },
            )
        }

        /**
         * @param queueFromHere returns a play queue for the list that contains [item], with the
         * queue index pointing to [item], used to build actions like "Play playlist from here".
         */
        @JvmStatic
        fun fromStreamInfoItem(
            item: StreamInfoItem,
            queueFromHere: (() -> PlayQueue)?,
            /* TODO isKodiEnabled: Boolean, */
        ): List<LongPressAction> {
            return buildPlayerActionList { SinglePlayQueue(item) } +
                (queueFromHere?.let { buildPlayerFromHereActionList(queueFromHere) } ?: listOf()) +
                buildShareActionList(item) +
                listOf(
                    Type.Download.buildAction { context ->
                        SparseItemUtil.fetchStreamInfoAndSaveToDatabase(
                            context, item.serviceId, item.url
                        ) { info ->
                            val downloadDialog = DownloadDialog(context, info)
                            val fragmentManager = context.findFragmentActivity()
                                .supportFragmentManager
                            downloadDialog.show(fragmentManager, "downloadDialog")
                        }
                    },
                    Type.AddToPlaylist.buildAction { context ->
                        PlaylistDialog.createCorrespondingDialog(
                            context,
                            listOf(StreamEntity(item))
                        ) { dialog: PlaylistDialog ->
                            val tag = if (dialog is PlaylistAppendDialog) "append" else "create"
                            dialog.show(
                                context.findFragmentActivity().supportFragmentManager,
                                "StreamDialogEntry@${tag}_playlist"
                            )
                        }
                    },
                    Type.ShowChannelDetails.buildAction { context ->
                        SparseItemUtil.fetchUploaderUrlIfSparse(
                            context, item.serviceId, item.url, item.uploaderUrl
                        ) { url: String ->
                            NavigationHelper.openChannelFragment(
                                context.findFragmentActivity().supportFragmentManager,
                                item.serviceId,
                                url,
                                item.uploaderName,
                            )
                        }
                    },
                    Type.MarkAsWatched.buildAction { context ->
                        HistoryRecordManager(context)
                            .markAsWatched(item)
                            .doOnError { error ->
                                ErrorUtil.showSnackbar(
                                        context,
                                        ErrorInfo(
                                            error,
                                            UserAction.OPEN_INFO_ITEM_DIALOG,
                                            "Got an error when trying to mark as watched"
                                        )
                                )
                            }
                            .onErrorComplete()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe()
                    },
                )
            /* TODO handle kodi
            + if (isKodiEnabled) listOf(
                Type.PlayWithKodi.buildAction { context ->
                    KoreUtils.playWithKore(context, Uri.parse(item.url))
                },
            ) else listOf()*/
        }

        @JvmStatic
        fun fromStreamEntity(
            item: StreamEntity,
            queueFromHere: (() -> PlayQueue)?,
        ): List<LongPressAction> {
            // TODO decide if it's fine to just convert to StreamInfoItem here (it poses an
            //  unnecessary dependency on the extractor, when we want to just look at data; maybe
            //  using something like LongPressable would work)
            return fromStreamInfoItem(item.toStreamInfoItem(), queueFromHere)
        }

        @JvmStatic
        fun fromStreamStatisticsEntry(
            item: StreamStatisticsEntry,
            queueFromHere: (() -> PlayQueue)?,
        ): List<LongPressAction> {
            return fromStreamEntity(item.streamEntity, queueFromHere) +
                listOf(
                    Type.Delete.buildAction { context ->
                        HistoryRecordManager(context)
                            .deleteStreamHistoryAndState(item.streamId)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                Toast.makeText(
                                    context,
                                    R.string.one_item_deleted,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                )
        }

        @JvmStatic
        fun fromPlaylistStreamEntry(
            item: PlaylistStreamEntry,
            queueFromHere: (() -> PlayQueue)?,
            // TODO possibly embed these two actions here
            onDelete: Runnable,
            onSetAsPlaylistThumbnail: Runnable,
        ): List<LongPressAction> {
            return fromStreamEntity(item.streamEntity, queueFromHere) +
                listOf(
                    Type.Delete.buildAction { onDelete.run() },
                    Type.SetAsPlaylistThumbnail.buildAction { onSetAsPlaylistThumbnail.run() }
                )
        }

        @JvmStatic
        fun fromPlaylistMetadataEntry(
            item: PlaylistMetadataEntry,
            onRename: Runnable,
            onDelete: Runnable,
            unsetPlaylistThumbnail: Runnable?,
        ): List<LongPressAction> {
            return listOf(
                Type.Rename.buildAction { onRename.run() },
                Type.Delete.buildAction { onDelete.run() },
                Type.UnsetPlaylistThumbnail.buildAction(
                    enabled = { unsetPlaylistThumbnail != null }
                ) { unsetPlaylistThumbnail?.run() }
            )
        }

        @JvmStatic
        fun fromPlaylistRemoteEntity(
            item: PlaylistRemoteEntity,
            onDelete: Runnable,
        ): List<LongPressAction> {
            return buildPlayerActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(
                    item.orderingName ?: "",
                    item.orderingName ?: "",
                    item.thumbnailUrl
                ) +
                listOf(
                    Type.Delete.buildAction { onDelete.run() },
                )
        }

        @JvmStatic
        fun fromChannelInfoItem(
            item: ChannelInfoItem,
            onUnsubscribe: Runnable?,
        ): List<LongPressAction> {
            return buildPlayerActionList { ChannelTabPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item) +
                listOfNotNull(
                    Type.ShowChannelDetails.buildAction { context ->
                        NavigationHelper.openChannelFragment(
                            context.findFragmentActivity().supportFragmentManager,
                            item.serviceId,
                            item.url,
                            item.name,
                        )
                    },
                    onUnsubscribe?.let { r -> Type.Unsubscribe.buildAction { r.run() } }
                )
        }

        @JvmStatic
        fun fromPlaylistInfoItem(item: PlaylistInfoItem): List<LongPressAction> {
            return buildPlayerActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item)
        }
    }
}
