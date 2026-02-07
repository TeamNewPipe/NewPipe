package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitSingle
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue
import org.schabi.newpipe.player.playqueue.LocalPlaylistPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.ui.components.menu.icons.BackgroundFromHere
import org.schabi.newpipe.ui.components.menu.icons.BackgroundShuffled
import org.schabi.newpipe.ui.components.menu.icons.PlayFromHere
import org.schabi.newpipe.ui.components.menu.icons.PlayShuffled
import org.schabi.newpipe.ui.components.menu.icons.PopupFromHere
import org.schabi.newpipe.ui.components.menu.icons.PopupShuffled
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.KoreUtils
import org.schabi.newpipe.util.external_communication.ShareUtils

data class LongPressAction(
    val type: Type,
    @MainThread
    val action: suspend (context: Context) -> Unit,
    val enabled: () -> Boolean = { true }
) {
    enum class Type(
        /**
         * A unique ID that allows saving and restoring a list of action types from settings.
         * MUST NOT CHANGE ACROSS APP VERSIONS!
         */
        val id: Int,
        @StringRes val label: Int,
        val icon: ImageVector
    ) {
        ShowDetails(0, R.string.play_queue_stream_detail, Icons.Default.Info),
        Enqueue(1, R.string.enqueue, Icons.Default.AddToQueue),
        EnqueueNext(2, R.string.enqueue_next_stream, Icons.Default.QueuePlayNext),
        Background(3, R.string.controls_background_title, Icons.Default.Headset),
        Popup(4, R.string.controls_popup_title, Icons.Default.PictureInPicture),
        Play(5, R.string.play, Icons.Default.PlayArrow),
        BackgroundFromHere(6, R.string.background_from_here, Icons.Default.BackgroundFromHere),
        PopupFromHere(7, R.string.popup_from_here, Icons.Default.PopupFromHere),
        PlayFromHere(8, R.string.play_from_here, Icons.Default.PlayFromHere),
        BackgroundShuffled(9, R.string.background_shuffled, Icons.Default.BackgroundShuffled),
        PopupShuffled(10, R.string.popup_shuffled, Icons.Default.PopupShuffled),
        PlayShuffled(11, R.string.play_shuffled, Icons.Default.PlayShuffled),
        PlayWithKodi(12, R.string.play_with_kodi_title, Icons.Default.Cast),
        Download(13, R.string.download, Icons.Default.Download),
        AddToPlaylist(14, R.string.add_to_playlist, Icons.AutoMirrored.Default.PlaylistAdd),
        Share(15, R.string.share, Icons.Default.Share),
        OpenInBrowser(16, R.string.open_in_browser, Icons.Default.OpenInBrowser),
        ShowChannelDetails(17, R.string.show_channel_details, Icons.Default.Person),
        MarkAsWatched(18, R.string.mark_as_watched, Icons.Default.Done),
        Rename(19, R.string.rename, Icons.Default.Edit),
        SetAsPlaylistThumbnail(20, R.string.set_as_playlist_thumbnail, Icons.Default.Image),
        UnsetPlaylistThumbnail(21, R.string.unset_playlist_thumbnail, Icons.Default.HideImage),
        Delete(22, R.string.delete, Icons.Default.Delete),
        Unsubscribe(23, R.string.unsubscribe, Icons.Default.Delete),
        Remove(24, R.string.play_queue_remove, Icons.Default.Delete);

        fun buildAction(
            enabled: () -> Boolean = { true },
            action: suspend (context: Context) -> Unit
        ) = LongPressAction(this, action, enabled)

        companion object {
            // ShowChannelDetails is not enabled by default, since navigating to channel details can
            // also be done by clicking on the uploader name in the long press menu header
            val DefaultEnabledActions: List<Type> = listOf(
                ShowDetails, Enqueue, EnqueueNext, Background, Popup, BackgroundFromHere,
                BackgroundShuffled, Download, AddToPlaylist, Share, OpenInBrowser, MarkAsWatched,
                Rename, SetAsPlaylistThumbnail, UnsetPlaylistThumbnail, Delete, Unsubscribe, Remove
            )
        }
    }

    companion object {
        private fun buildPlayerActionList(
            queue: suspend (Context) -> PlayQueue
        ): List<LongPressAction> {
            return listOf(
                // TODO once NewPlayer will be used, make it so that the enabled states of Enqueue
                //  and EnqueueNext are a State<> that changes realtime based on the actual evolving
                //  player state
                Type.Enqueue.buildAction(
                    enabled = { PlayerHolder.isPlayQueueReady }
                ) { context ->
                    NavigationHelper.enqueueOnPlayer(context, queue(context))
                },
                Type.EnqueueNext.buildAction(
                    enabled = {
                        PlayerHolder.isPlayQueueReady &&
                            (PlayerHolder.queuePosition < PlayerHolder.queueSize - 1)
                    }
                ) { context ->
                    NavigationHelper.enqueueNextOnPlayer(context, queue(context))
                },
                Type.Background.buildAction { context ->
                    NavigationHelper.playOnBackgroundPlayer(context, queue(context), true)
                },
                Type.Popup.buildAction { context ->
                    NavigationHelper.playOnPopupPlayer(context, queue(context), true)
                },
                Type.Play.buildAction { context ->
                    NavigationHelper.playOnMainPlayer(context, queue(context), false)
                }
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
                }
            )
        }

        private fun buildPlayerShuffledActionList(queue: suspend (Context) -> PlayQueue): List<LongPressAction> {
            val shuffledQueue: suspend (Context) -> PlayQueue = { context ->
                val q = queue(context)
                q.fetchAllAndShuffle()
                q
            }
            return listOf(
                Type.BackgroundShuffled.buildAction { context ->
                    NavigationHelper.playOnBackgroundPlayer(context, shuffledQueue(context), true)
                },
                Type.PopupShuffled.buildAction { context ->
                    NavigationHelper.playOnPopupPlayer(context, shuffledQueue(context), true)
                },
                Type.PlayShuffled.buildAction { context ->
                    NavigationHelper.playOnMainPlayer(context, shuffledQueue(context), false)
                }
            )
        }

        private fun buildShareActionList(item: InfoItem): List<LongPressAction> {
            return listOf(
                Type.Share.buildAction { context ->
                    ShareUtils.shareText(context, item.name, item.url, item.thumbnails)
                },
                Type.OpenInBrowser.buildAction { context ->
                    ShareUtils.openUrlInBrowser(context, item.url)
                }
            )
        }

        private fun buildShareActionList(name: String, url: String, thumbnailUrl: String?): List<LongPressAction> {
            return listOf(
                Type.Share.buildAction { context ->
                    ShareUtils.shareText(context, name, url, thumbnailUrl)
                },
                Type.OpenInBrowser.buildAction { context ->
                    ShareUtils.openUrlInBrowser(context, url)
                }
            )
        }

        private fun buildAdditionalStreamActionList(item: StreamInfoItem): List<LongPressAction> {
            return listOf(
                Type.Download.buildAction { context ->
                    val info = fetchStreamInfoAndSaveToDatabase(context, item.serviceId, item.url)
                    val downloadDialog = DownloadDialog(context, info)
                    val fragmentManager = context.findFragmentActivity()
                        .supportFragmentManager
                    downloadDialog.show(fragmentManager, "downloadDialog")
                },
                Type.AddToPlaylist.buildAction { context ->
                    LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                        .hasPlaylists()
                    val dialog = withContext(Dispatchers.IO) {
                        PlaylistDialog.createCorrespondingDialog(
                            context,
                            listOf(StreamEntity(item))
                        )
                            .awaitSingle()
                    }
                    val tag = if (dialog is PlaylistAppendDialog) "append" else "create"
                    dialog.show(
                        context.findFragmentActivity().supportFragmentManager,
                        "StreamDialogEntry@${tag}_playlist"
                    )
                },
                Type.ShowChannelDetails.buildAction { context ->
                    val uploaderUrl = fetchUploaderUrlIfSparse(
                        context,
                        item.serviceId,
                        item.url,
                        item.uploaderUrl
                    )
                    NavigationHelper.openChannelFragmentUsingIntent(
                        context,
                        item.serviceId,
                        uploaderUrl,
                        item.uploaderName
                    )
                },
                Type.MarkAsWatched.buildAction { context ->
                    withContext(Dispatchers.IO) {
                        HistoryRecordManager(context).markAsWatched(item).await()
                    }
                },
                Type.PlayWithKodi.buildAction { context ->
                    KoreUtils.playWithKore(context, item.url.toUri())
                }
            )
        }

        /**
         * @param queueFromHere returns a play queue for the list that contains [item], with the
         * queue index pointing to [item], used to build actions like "Play playlist from here".
         */
        @JvmStatic
        fun fromStreamInfoItem(
            item: StreamInfoItem,
            queueFromHere: (() -> PlayQueue)?
            /* TODO isKodiEnabled: Boolean, */
        ): List<LongPressAction> {
            return buildPlayerActionList { context -> fetchItemInfoIfSparse(context, item) } +
                (queueFromHere?.let { buildPlayerFromHereActionList(queueFromHere) } ?: listOf()) +
                buildShareActionList(item) +
                buildAdditionalStreamActionList(item)
        }

        @JvmStatic
        fun fromStreamEntity(
            item: StreamEntity,
            queueFromHere: (() -> PlayQueue)?
        ): List<LongPressAction> {
            // TODO decide if it's fine to just convert to StreamInfoItem here (it poses an
            //  unnecessary dependency on the extractor, when we want to just look at data; maybe
            //  using something like LongPressable would work)
            return fromStreamInfoItem(item.toStreamInfoItem(), queueFromHere)
        }

        @JvmStatic
        fun fromPlayQueueItem(
            item: PlayQueueItem,
            playQueueFromWhichToDelete: PlayQueue,
            showDetails: Boolean
        ): List<LongPressAction> {
            // TODO decide if it's fine to just convert to StreamInfoItem here (it poses an
            //  unnecessary dependency on the extractor, when we want to just look at data; maybe
            //  using something like LongPressable would work)
            val streamInfoItem = item.toStreamInfoItem()
            return buildShareActionList(streamInfoItem) +
                buildAdditionalStreamActionList(streamInfoItem) +
                if (showDetails) {
                    listOf(
                        Type.ShowDetails.buildAction { context ->
                            // playQueue is null since we don't want any queue change
                            NavigationHelper.openVideoDetail(
                                context,
                                item.serviceId,
                                item.url,
                                item.title,
                                null,
                                false
                            )
                        }
                    )
                } else {
                    listOf()
                } +
                listOf(
                    Type.Remove.buildAction {
                        val index = playQueueFromWhichToDelete.indexOf(item)
                        playQueueFromWhichToDelete.remove(index)
                    }
                )
        }

        @JvmStatic
        fun fromStreamStatisticsEntry(
            item: StreamStatisticsEntry,
            queueFromHere: (() -> PlayQueue)?
        ): List<LongPressAction> {
            return fromStreamEntity(item.streamEntity, queueFromHere) +
                listOf(
                    Type.Delete.buildAction { context ->
                        withContext(Dispatchers.IO) {
                            HistoryRecordManager(context)
                                .deleteStreamHistoryAndState(item.streamId)
                                .await()
                        }
                        Toast.makeText(context, R.string.one_item_deleted, Toast.LENGTH_SHORT)
                            .show()
                    }
                )
        }

        @JvmStatic
        fun fromPlaylistStreamEntry(
            item: PlaylistStreamEntry,
            queueFromHere: (() -> PlayQueue)?,
            // TODO possibly embed these two actions here
            onDelete: Runnable,
            onSetAsPlaylistThumbnail: Runnable
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
            unsetPlaylistThumbnail: Runnable?
        ): List<LongPressAction> {
            return buildPlayerActionList { LocalPlaylistPlayQueue(item) } +
                buildPlayerShuffledActionList { LocalPlaylistPlayQueue(item) } +
                listOf(
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
            onDelete: Runnable
        ): List<LongPressAction> {
            return buildPlayerActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildPlayerShuffledActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(
                    item.orderingName ?: "",
                    item.orderingName ?: "",
                    item.thumbnailUrl
                ) +
                listOf(
                    Type.Delete.buildAction { onDelete.run() }
                )
        }

        @JvmStatic
        fun fromChannelInfoItem(
            item: ChannelInfoItem,
            onUnsubscribe: Runnable?
        ): List<LongPressAction> {
            return buildPlayerActionList { ChannelTabPlayQueue(item.serviceId, item.url) } +
                buildPlayerShuffledActionList { ChannelTabPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item) +
                listOfNotNull(
                    Type.ShowChannelDetails.buildAction { context ->
                        NavigationHelper.openChannelFragmentUsingIntent(
                            context,
                            item.serviceId,
                            item.url,
                            item.name
                        )
                    },
                    onUnsubscribe?.let { r -> Type.Unsubscribe.buildAction { r.run() } }
                )
        }

        @JvmStatic
        fun fromPlaylistInfoItem(item: PlaylistInfoItem): List<LongPressAction> {
            return buildPlayerActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildPlayerShuffledActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item)
        }
    }
}
