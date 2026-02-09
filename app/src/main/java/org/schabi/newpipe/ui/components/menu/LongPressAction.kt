package org.schabi.newpipe.ui.components.menu

import android.content.Context
import android.text.InputType
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddCircle
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
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirst
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
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.DialogEditTextBinding
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
import org.schabi.newpipe.local.subscription.SubscriptionManager
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
        Subscribe(22, R.string.subscribe_button_title, Icons.Default.AddCircle),
        Unsubscribe(23, R.string.unsubscribe, Icons.Default.RemoveCircle),
        Delete(24, R.string.delete, Icons.Default.Delete),
        Remove(25, R.string.play_queue_remove, Icons.Default.Delete);

        fun buildAction(
            enabled: () -> Boolean = { true },
            action: suspend (context: Context) -> Unit
        ) = LongPressAction(this, action, enabled)
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

        /**
         * Instead of queueFromHere, this function could possibly take a
         *  `() -> List<StreamInfoItem/StreamEntity/...>` plus the `StreamInfoItem/StreamEntity/...`
         *  that was long-pressed, and take care of searching through the list to find the item
         *  index, and finally take care of building the queue. It would deduplicate some code in
         *  fragments, but it's probably not possible to do because of all the different types of
         *  the items involved. But this should be reconsidered if the types will be unified.
         */
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
                withContext(Dispatchers.IO) {
                    q.fetchAllAndShuffle()
                }
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
                }
            ) +
                if (KoreUtils.isServiceSupportedByKore(item.serviceId)) {
                    listOf(
                        Type.PlayWithKodi.buildAction(
                            enabled = { KoreUtils.isServiceSupportedByKore(item.serviceId) }
                        ) { context -> KoreUtils.playWithKore(context, item.url.toUri()) }
                    )
                } else {
                    listOf()
                }
        }

        /**
         * @param queueFromHere returns a play queue for the list that contains [item], with the
         * queue index pointing to [item], used to build actions like "Play playlist from here".
         */
        @JvmStatic
        fun fromStreamInfoItem(
            item: StreamInfoItem,
            queueFromHere: (() -> PlayQueue)?
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
            return fromStreamInfoItem(item.streamEntity.toStreamInfoItem(), queueFromHere) +
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

        /**
         * TODO [onDelete] is still passed externally to allow the calling fragment to debounce
         *  many deletions into a single database transaction, improving performance. This is
         *  however a bad pattern (which has already led to many bugs in NewPipe). Once we migrate
         *  the playlist fragment to Compose, we should make the database updates immediately, and
         *  use `collectAsLazyPagingItems()` to load data in chunks and thus avoid slowdowns.
         */
        @JvmStatic
        fun fromPlaylistStreamEntry(
            item: PlaylistStreamEntry,
            queueFromHere: (() -> PlayQueue)?,
            playlistId: Long,
            onDelete: Runnable
        ): List<LongPressAction> {
            return fromStreamInfoItem(item.streamEntity.toStreamInfoItem(), queueFromHere) +
                listOf(
                    Type.SetAsPlaylistThumbnail.buildAction { context ->
                        withContext(Dispatchers.IO) {
                            LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                                .changePlaylistThumbnail(playlistId, item.streamEntity.uid, true)
                                .awaitSingle()
                        }
                        Toast.makeText(
                            context,
                            R.string.playlist_thumbnail_change_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    Type.Delete.buildAction { onDelete.run() }
                )
        }

        /**
         * TODO see [fromPlaylistStreamEntry] for why [onDelete] is here and why it's bad
         */
        @JvmStatic
        fun fromPlaylistMetadataEntry(
            item: PlaylistMetadataEntry,
            isThumbnailPermanent: Boolean,
            onDelete: Runnable
        ): List<LongPressAction> {
            return buildPlayerActionList { LocalPlaylistPlayQueue(item) } +
                buildPlayerShuffledActionList { LocalPlaylistPlayQueue(item) } +
                listOf(
                    Type.Rename.buildAction { context ->
                        // open the dialog and wait for its completion in the coroutine
                        val newName = suspendCoroutine<String?> { continuation ->
                            val dialogBinding = DialogEditTextBinding.inflate(
                                context.findFragmentActivity().layoutInflater
                            )
                            dialogBinding.dialogEditText.setHint(R.string.name)
                            dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT)
                            dialogBinding.dialogEditText.setText(item.orderingName)
                            AlertDialog.Builder(context)
                                .setView(dialogBinding.getRoot())
                                .setPositiveButton(R.string.rename_playlist) { _, _ ->
                                    continuation.resume(dialogBinding.dialogEditText.getText().toString())
                                }
                                .setNegativeButton(R.string.cancel) { _, _ ->
                                    continuation.resume(null)
                                }
                                .setOnCancelListener {
                                    continuation.resume(null)
                                }
                                .show()
                        } ?: return@buildAction

                        withContext(Dispatchers.IO) {
                            LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                                .renamePlaylist(item.uid, newName)
                                .awaitSingle()
                        }
                    },
                    Type.UnsetPlaylistThumbnail.buildAction(
                        enabled = { isThumbnailPermanent }
                    ) { context ->
                        withContext(Dispatchers.IO) {
                            val localPlaylistManager =
                                LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                            val thumbnailStreamId = localPlaylistManager
                                .getAutomaticPlaylistThumbnailStreamId(item.uid)
                                .awaitFirst()
                            localPlaylistManager
                                .changePlaylistThumbnail(item.uid, thumbnailStreamId, false)
                                .awaitSingle()
                        }
                    },
                    Type.Delete.buildAction { onDelete.run() }
                )
        }

        /**
         * TODO see [fromPlaylistStreamEntry] for why [onDelete] is here and why it's bad
         */
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
            isSubscribed: Boolean
        ): List<LongPressAction> {
            return buildPlayerActionList { ChannelTabPlayQueue(item.serviceId, item.url) } +
                buildPlayerShuffledActionList { ChannelTabPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item) +
                listOf(
                    Type.ShowChannelDetails.buildAction { context ->
                        NavigationHelper.openChannelFragmentUsingIntent(
                            context,
                            item.serviceId,
                            item.url,
                            item.name
                        )
                    }
                ) +
                if (isSubscribed) {
                    listOf(
                        Type.Unsubscribe.buildAction { context ->
                            withContext(Dispatchers.IO) {
                                SubscriptionManager(context)
                                    .deleteSubscription(item.serviceId, item.url)
                                    .await()
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.channel_unsubscribed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    listOf(
                        Type.Subscribe.buildAction { context ->
                            withContext(Dispatchers.IO) {
                                SubscriptionManager(context)
                                    .insertSubscription(SubscriptionEntity.from(item))
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.subscribed_button_title),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
        }

        @JvmStatic
        fun fromPlaylistInfoItem(item: PlaylistInfoItem): List<LongPressAction> {
            return buildPlayerActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildPlayerShuffledActionList { PlaylistPlayQueue(item.serviceId, item.url) } +
                buildShareActionList(item)
        }
    }
}
