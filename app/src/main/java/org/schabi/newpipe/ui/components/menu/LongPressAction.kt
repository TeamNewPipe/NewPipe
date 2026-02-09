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
import org.schabi.newpipe.ktx.findFragmentManager
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

typealias ActionList = MutableList<LongPressAction>

/**
 * An action that the user can perform in the long press menu of an item. What matters are lists of
 * [LongPressAction], i.e. [ActionList]s, which represent a set of actions that are *applicable* for
 * an item.
 *
 * If an action is present in an [ActionList] it does not necessarily imply that it will be shown to
 * the user in the long press menu, because the user may decide which actions to show with the
 * `LongPressMenuEditor`.
 *
 * Also, an [ActionList] may contain actions that are temporarily unavailable (e.g. enqueueing when
 * no player is running; see [enabled]), but **should not** contain actions that are not
 * *applicable* for an item (i.e. they wouldn't make sense). That's why you will see some actions
 * being marked as not [enabled] and some not being included at all in the [ActionList] builders.
 *
 * @param type the [Type] of the action, describing how to identify it and represent it visually
 * @param enabled a lambda that the UI layer can call at any time to check if the action is
 * temporarily unavailable (e.g. the enqueue action is available only if the player is running)
 * @param action will be called **at most once** to actually perform the action upon selection by
 * the user; will be run on [Dispatchers.Main] (i.e. the UI/main thread) and should perform any
 * I/O-heavy computation using `withContext(Dispatchers.IO)`; since this is a `suspend` function, it
 * is ok if it takes a while to complete, and a loading spinner will be shown in the meantime
 */
data class LongPressAction(
    val type: Type,
    val enabled: () -> Boolean = { true },
    @MainThread
    val action: suspend (context: Context) -> Unit
) {
    /**
     * @param id a unique ID that allows saving and restoring a list of action types from settings.
     * **MUST NOT CHANGE ACROSS APP VERSIONS!**
     * @param label a string label to show in the action's button
     * @param icon an icon to show in the action's button
     */
    enum class Type(
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
        Remove(25, R.string.play_queue_remove, Icons.Default.Delete)
    }

    companion object {

        /**
         * Builds and adds a [LongPressAction] to the list.
         */
        private fun ActionList.addAction(
            type: Type,
            enabled: () -> Boolean = { true },
            action: suspend (context: Context) -> Unit
        ): ActionList {
            this.add(LongPressAction(type, enabled, action))
            return this
        }

        /**
         * Builds and adds a [LongPressAction] to the list, but **only if [condition] is `true`**.
         * The difference between [condition] and [enabled] is explained in [LongPressAction].
         */
        private fun ActionList.addActionIf(
            condition: Boolean,
            type: Type,
            enabled: () -> Boolean = { true },
            action: suspend (context: Context) -> Unit
        ): ActionList {
            if (condition) {
                addAction(type, enabled, action)
            }
            return this
        }

        /**
         * Add the typical player actions that can be performed on any [queue] of streams:
         * enqueueing on an existing player and starting one among the three player types.
         */
        private fun ActionList.addPlayerActions(queue: suspend (Context) -> PlayQueue): ActionList {
            // TODO once NewPlayer will be used, make it so that the enabled states of Enqueue
            //  and EnqueueNext are a State<> that changes in real time based on the actual evolving
            //  player state
            addAction(Type.Enqueue, enabled = { PlayerHolder.isPlayQueueReady }) { context ->
                NavigationHelper.enqueueOnPlayer(context, queue(context))
            }
            addAction(Type.EnqueueNext, enabled = {
                PlayerHolder.isPlayQueueReady &&
                    (PlayerHolder.queuePosition < PlayerHolder.queueSize - 1)
            }) { context ->
                NavigationHelper.enqueueNextOnPlayer(context, queue(context))
            }
            addAction(Type.Background) { context ->
                NavigationHelper.playOnBackgroundPlayer(context, queue(context), true)
            }
            addAction(Type.Popup) { context ->
                NavigationHelper.playOnPopupPlayer(context, queue(context), true)
            }
            addAction(Type.Play) { context ->
                NavigationHelper.playOnMainPlayer(context, queue(context), false)
            }
            return this
        }

        /**
         * Add player actions that can be performed when the item (the one that the actions refer
         * to), is also part of a list which can be played starting from said item, i.e. "play list
         * starting from here" actions.
         *
         * *Note: instead of [queueFromHere], this function could possibly take a
         * `() -> List<StreamInfoItem/StreamEntity/...>` plus the `StreamInfoItem/StreamEntity/...`
         * that was long-pressed, and take care of searching through the list to find the item
         * index, and finally take care of building the queue. It would deduplicate some code in
         * fragments, but it's probably not possible to do because of all the different types of
         * the items involved. But this should be reconsidered if the types will be unified.*
         *
         * @param queueFromHere if `null`, this will not modify the list
         */
        private fun ActionList.addPlayerFromHereActions(
            queueFromHere: (() -> PlayQueue)?
        ): ActionList {
            if (queueFromHere == null) {
                return this
            }
            addAction(Type.BackgroundFromHere) { context ->
                NavigationHelper.playOnBackgroundPlayer(context, queueFromHere(), true)
            }
            addAction(Type.PopupFromHere) { context ->
                NavigationHelper.playOnPopupPlayer(context, queueFromHere(), true)
            }
            addAction(Type.PlayFromHere) { context ->
                NavigationHelper.playOnMainPlayer(context, queueFromHere(), false)
            }
            return this
        }

        /**
         * Add player actions that make sense only when [queue] (generally) contains multiple
         * streams (e.g. playlists, channels), i.e. "play item's streams shuffled" actions.
         */
        private fun ActionList.addPlayerShuffledActions(
            queue: suspend (Context) -> PlayQueue
        ): ActionList {
            val shuffledQueue: suspend (Context) -> PlayQueue = { context ->
                val q = queue(context)
                withContext(Dispatchers.IO) {
                    q.fetchAllAndShuffle()
                }
                q
            }
            addAction(Type.BackgroundShuffled) { context ->
                NavigationHelper.playOnBackgroundPlayer(context, shuffledQueue(context), true)
            }
            addAction(Type.PopupShuffled) { context ->
                NavigationHelper.playOnPopupPlayer(context, shuffledQueue(context), true)
            }
            addAction(Type.PlayShuffled) { context ->
                NavigationHelper.playOnMainPlayer(context, shuffledQueue(context), false)
            }
            return this
        }

        /**
         * Add actions that allow sharing an [InfoItem] externally.
         * Also see the other overload for a more generic version.
         */
        private fun ActionList.addShareActions(item: InfoItem): ActionList {
            addAction(Type.Share) { context ->
                ShareUtils.shareText(context, item.name, item.url, item.thumbnails)
            }
            addAction(Type.OpenInBrowser) { context ->
                ShareUtils.openUrlInBrowser(context, item.url)
            }
            return this
        }

        /**
         * Add actions that allow sharing externally an item with [name], [url] and optionally
         * [thumbnailUrl]. Also see the other overload for an [InfoItem]-specific version.
         */
        private fun ActionList.addShareActions(
            name: String,
            url: String,
            thumbnailUrl: String?
        ): ActionList {
            addAction(Type.Share) { context ->
                ShareUtils.shareText(context, name, url, thumbnailUrl)
            }
            addAction(Type.OpenInBrowser) { context ->
                ShareUtils.openUrlInBrowser(context, url)
            }
            return this
        }

        /**
         * Add actions that can be performed on any stream item, be it a remote stream item or a
         * stream item stored in history.
         */
        private fun ActionList.addAdditionalStreamActions(item: StreamInfoItem): ActionList {
            addAction(Type.Download) { context ->
                val info = fetchStreamInfoAndSaveToDatabase(context, item.serviceId, item.url)
                val downloadDialog = DownloadDialog(context, info)
                downloadDialog.show(context.findFragmentManager(), "downloadDialog")
            }
            addAction(Type.AddToPlaylist) { context ->
                LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                    .hasPlaylists()
                val dialog = withContext(Dispatchers.IO) {
                    PlaylistDialog.createCorrespondingDialog(context, listOf(StreamEntity(item)))
                        .awaitSingle()
                }
                dialog.show(context.findFragmentManager(), "addToPlaylistDialog")
            }
            addAction(Type.ShowChannelDetails) { context ->
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
            }
            addAction(Type.MarkAsWatched) { context ->
                withContext(Dispatchers.IO) {
                    HistoryRecordManager(context).markAsWatched(item).await()
                }
            }
            if (KoreUtils.isServiceSupportedByKore(item.serviceId)) {
                // offer the option to play with Kodi only if Kore supports the item service
                addAction(
                    Type.PlayWithKodi,
                    enabled = { KoreUtils.isServiceSupportedByKore(item.serviceId) }
                ) { context -> KoreUtils.playWithKore(context, item.url.toUri()) }
            }
            return this
        }

        /**
         * *Note: if and when stream item representations will be unified, this should be removed in
         * favor a single unified `fromStreamItem` option.*
         *
         * @param item the remote stream item for which to create a list of possible actions
         * @param queueFromHere returns a play queue containing all of the stream items in the list
         * that contains [item], with the queue index pointing to [item]; if `null`, no "start
         * playing from here" options will be included
         */
        @JvmStatic
        fun fromStreamInfoItem(
            item: StreamInfoItem,
            queueFromHere: (() -> PlayQueue)?
        ): ActionList {
            return ArrayList<LongPressAction>()
                .addPlayerActions { context -> fetchItemInfoIfSparse(context, item) }
                .addPlayerFromHereActions(queueFromHere)
                .addShareActions(item)
                .addAdditionalStreamActions(item)
        }

        /**
         * *Note: if and when stream item representations will be unified, this should be removed in
         * favor a single unified `fromStreamItem` option.*
         *
         * @param item the local stream item for which to create a list of possible actions
         * @param queueFromHere returns a play queue containing all of the stream items in the list
         * that contains [item], with the queue index pointing to [item]; if `null`, no "start
         * playing from here" options will be included
         */
        @JvmStatic
        fun fromStreamEntity(
            item: StreamEntity,
            queueFromHere: (() -> PlayQueue)?
        ): ActionList {
            return fromStreamInfoItem(item.toStreamInfoItem(), queueFromHere)
        }

        /**
         * *Note: if and when stream item representations will be unified, this should be removed in
         * favor a single unified `fromStreamItem` option.*
         *
         * @param item the history stream item for which to create a list of possible actions
         * @param queueFromHere returns a play queue containing all of the stream items in the list
         * that contains [item], with the queue index pointing to [item]; if `null`, no "start
         * playing from here" options will be included
         */
        @JvmStatic
        fun fromStreamStatisticsEntry(
            item: StreamStatisticsEntry,
            queueFromHere: (() -> PlayQueue)?
        ): ActionList {
            return fromStreamInfoItem(item.streamEntity.toStreamInfoItem(), queueFromHere)
                .addAction(Type.Delete) { context ->
                    withContext(Dispatchers.IO) {
                        HistoryRecordManager(context)
                            .deleteStreamHistoryAndState(item.streamId)
                            .await()
                    }
                    Toast.makeText(context, R.string.one_item_deleted, Toast.LENGTH_SHORT)
                        .show()
                }
        }

        /**
         * *Note: if and when stream item representations will be unified, this should be removed in
         * favor a single unified `fromStreamItem` option.*
         *
         * *Note: [onDelete] is still passed externally to allow the calling fragment to debounce
         * many deletions into a single database transaction, improving performance. This is
         * however a bad pattern (which has already led to many bugs in NewPipe). Once we migrate
         * the playlist fragment to Compose, we should make the database updates immediately, and
         * use `collectAsLazyPagingItems()` to load data in chunks and thus avoid slowdowns.*
         *
         * @param item the playlist stream item for which to create a list of possible actions
         * @param queueFromHere returns a play queue containing all of the stream items in the list
         * that contains [item], with the queue index pointing to [item]; if `null`, no "start
         * playing from here" options will be included
         * @param playlistId the playlist this stream belongs to, allows setting this item's
         * thumbnail as the playlist thumbnail
         * @param onDelete the action to run when the user presses on [Type.Delete], see above for
         * why it is here and why it is bad
         */
        @JvmStatic
        fun fromPlaylistStreamEntry(
            item: PlaylistStreamEntry,
            queueFromHere: (() -> PlayQueue)?,
            playlistId: Long,
            onDelete: Runnable
        ): ActionList {
            return fromStreamInfoItem(item.streamEntity.toStreamInfoItem(), queueFromHere)
                .addAction(Type.SetAsPlaylistThumbnail) { context ->
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
                }
                .addAction(Type.Delete) { onDelete.run() }
        }

        /**
         * *Note: if and when stream item representations will be unified, this should be removed in
         * favor a single unified `fromStreamItem` option.*
         *
         * @param item the play queue stream item for which to create a list of possible actions
         * @param playQueueFromWhichToDelete the play queue containing [item], and from which [item]
         * should be removed in case the user presses the [Type.Remove] action.
         * @param showDetails whether to include the option to show stream details, which only makes
         * sense if the user is not already on that stream's details page
         */
        @JvmStatic
        fun fromPlayQueueItem(
            item: PlayQueueItem,
            playQueueFromWhichToDelete: PlayQueue,
            showDetails: Boolean
        ): ActionList {
            val streamInfoItem = item.toStreamInfoItem()
            return ArrayList<LongPressAction>()
                .addShareActions(streamInfoItem)
                .addAdditionalStreamActions(streamInfoItem)
                .addActionIf(showDetails, Type.ShowDetails) { context ->
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
                .addAction(Type.Remove) {
                    val index = playQueueFromWhichToDelete.indexOf(item)
                    playQueueFromWhichToDelete.remove(index)
                }
        }

        /**
         * @param item the remote playlist item (e.g. appearing in searches or channel tabs, not the
         * remote playlists in bookmarks) for which to create a list of possible actions
         */
        @JvmStatic
        fun fromPlaylistInfoItem(item: PlaylistInfoItem): ActionList {
            return ArrayList<LongPressAction>()
                .addPlayerActions { PlaylistPlayQueue(item.serviceId, item.url) }
                .addPlayerShuffledActions { PlaylistPlayQueue(item.serviceId, item.url) }
                .addShareActions(item)
        }

        /**
         * @param item the local playlist item for which to create a list of possible actions
         * @param isThumbnailPermanent if true, the playlist's thumbnail was set by the user, and
         * can thus also be unset by the user
         * @param onDelete the action to run when the user presses on [Type.Delete], see
         * [fromPlaylistStreamEntry] for why it is here and why it is bad
         */
        @JvmStatic
        fun fromPlaylistMetadataEntry(
            item: PlaylistMetadataEntry,
            isThumbnailPermanent: Boolean,
            onDelete: Runnable
        ): ActionList {
            return ArrayList<LongPressAction>()
                .addPlayerActions { LocalPlaylistPlayQueue(item) }
                .addPlayerShuffledActions { LocalPlaylistPlayQueue(item) }
                .addAction(Type.Rename) { context ->
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
                    } ?: return@addAction

                    withContext(Dispatchers.IO) {
                        LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                            .renamePlaylist(item.uid, newName)
                            .awaitSingle()
                    }
                }
                .addAction(
                    Type.UnsetPlaylistThumbnail,
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
                }
                .addAction(Type.Delete) { onDelete.run() }
        }

        /**
         * @param item the remote bookmarked playlist item for which to create a list of possible
         * actions
         * @param onDelete the action to run when the user presses on [Type.Delete], see
         * [fromPlaylistStreamEntry] for why it is here and why it is bad
         */
        @JvmStatic
        fun fromPlaylistRemoteEntity(
            item: PlaylistRemoteEntity,
            onDelete: Runnable
        ): ActionList {
            return ArrayList<LongPressAction>()
                .addPlayerActions { PlaylistPlayQueue(item.serviceId, item.url) }
                .addPlayerShuffledActions { PlaylistPlayQueue(item.serviceId, item.url) }
                .addShareActions(item.orderingName ?: "", item.url ?: "", item.thumbnailUrl)
                .addAction(Type.Delete) { onDelete.run() }
        }

        /**
         * @param item the remote channel item for which to create a list of possible actions
         * @param isSubscribed used to decide whether to show the [Type.Subscribe] or
         * [Type.Unsubscribe] button
         */
        @JvmStatic
        fun fromChannelInfoItem(
            item: ChannelInfoItem,
            isSubscribed: Boolean
        ): ActionList {
            return ArrayList<LongPressAction>()
                .addPlayerActions { ChannelTabPlayQueue(item.serviceId, item.url) }
                .addPlayerShuffledActions { ChannelTabPlayQueue(item.serviceId, item.url) }
                .addShareActions(item)
                .addAction(Type.ShowChannelDetails) { context ->
                    NavigationHelper.openChannelFragmentUsingIntent(
                        context,
                        item.serviceId,
                        item.url,
                        item.name
                    )
                }
                .addActionIf(isSubscribed, Type.Unsubscribe) { context ->
                    withContext(Dispatchers.IO) {
                        SubscriptionManager(context)
                            .deleteSubscription(item.serviceId, item.url)
                            .await()
                    }
                    Toast.makeText(context, R.string.channel_unsubscribed, Toast.LENGTH_SHORT)
                        .show()
                }
                .addActionIf(!isSubscribed, Type.Subscribe) { context ->
                    withContext(Dispatchers.IO) {
                        SubscriptionManager(context)
                            .insertSubscription(SubscriptionEntity.from(item))
                    }
                    Toast.makeText(context, R.string.subscribed_button_title, Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }
}
