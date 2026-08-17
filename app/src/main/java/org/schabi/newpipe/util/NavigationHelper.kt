package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.jakewharton.processphoenix.ProcessPhoenix
import net.newpipe.app.extensions.navigateTo
import net.newpipe.app.navigation.Destination
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.download.DownloadActivity
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
// import org.schabi.newpipe.fragments.MainFragment
// import org.schabi.newpipe.fragments.detail.VideoDetailFragment
// import org.schabi.newpipe.fragments.list.channel.ChannelFragment
// import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
// import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
// import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
// import org.schabi.newpipe.fragments.list.search.SearchFragment
// import org.schabi.newpipe.local.bookmark.BookmarkFragment
// import org.schabi.newpipe.local.feed.FeedFragment
// import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
// import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
// import org.schabi.newpipe.local.subscription.SubscriptionFragment
// import org.schabi.newpipe.local.subscription.SubscriptionsImportFragment
import org.schabi.newpipe.player.PlayQueueActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.TimestampChangeData
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.external_communication.ShareUtils

object NavigationHelper {
    const val MAIN_FRAGMENT_TAG = "main_fragment_tag"
    const val SEARCH_FRAGMENT_TAG = "search_fragment_tag"
    private val TAG = NavigationHelper::class.java.simpleName

    // -------------------------------------------------------------------------
    // Players
    // -------------------------------------------------------------------------
    /* INTENT */
    @JvmStatic
    fun <T> getPlayerIntent(
        context: Context,
        targetClazz: Class<T>,
        playQueue: PlayQueue?,
        playerIntentType: PlayerIntentType
    ): Intent {
        val cacheKey = playQueue?.let {
            SerializedCache.getInstance().put(it, PlayQueue::class.java)
        }
        return Intent(context, targetClazz)
            .putExtra(Player.PLAY_QUEUE_KEY, cacheKey)
            .putExtra(Player.PLAYER_TYPE, PlayerType.MAIN)
            .putExtra(PlayerService.SHOULD_START_FOREGROUND_EXTRA, true)
            .putExtra(Player.PLAYER_INTENT_TYPE, playerIntentType)
    }

    @JvmStatic
    fun getPlayerTimestampIntent(context: Context, data: TimestampChangeData): Intent {
        return Intent(context, PlayerService::class.java)
            .putExtra(Player.PLAYER_INTENT_TYPE, PlayerIntentType.TimestampChange)
            .putExtra(Player.PLAYER_INTENT_DATA, data as java.io.Serializable)
    }

    @JvmStatic
    fun <T> getPlayerEnqueueNextIntent(
        context: Context,
        targetClazz: Class<T>,
        playQueue: PlayQueue?
    ): Intent {
        return getPlayerIntent(context, targetClazz, playQueue, PlayerIntentType.EnqueueNext)
            .putExtra(Player.RESUME_PLAYBACK, false)
    }

    /* PLAY */
    @JvmStatic
    fun playOnMainPlayer(activity: AppCompatActivity, playQueue: PlayQueue) {
        val item = playQueue.item
        if (item != null) {
            // openVideoDetailFragment(
            //    activity, activity.supportFragmentManager,
            //    item.serviceId, item.url, item.title, playQueue,
            //    false
            // )
        }
    }

    @JvmStatic
    fun playOnMainPlayer(context: Context, playQueue: PlayQueue, switchingPlayers: Boolean) {
        val item = playQueue.item
        if (item != null) {
            openVideoDetail(
                context,
                item.serviceId, item.url, item.title, playQueue,
                switchingPlayers
            )
        }
    }

    @JvmStatic
    fun playOnPopupPlayer(context: Context, queue: PlayQueue?, resumePlayback: Boolean) {
        if (!PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }

        Toast.makeText(context, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show()

        val intent = getPlayerIntent(context, PlayerService::class.java, queue, PlayerIntentType.AllOthers)
            .putExtra(Player.PLAYER_TYPE, PlayerType.POPUP)
            .putExtra(Player.RESUME_PLAYBACK, resumePlayback)
        ContextCompat.startForegroundService(context, intent)
    }

    @JvmStatic
    fun playOnBackgroundPlayer(context: Context, queue: PlayQueue?, resumePlayback: Boolean) {
        Toast.makeText(context, R.string.background_player_playing_toast, Toast.LENGTH_SHORT).show()

        val intent = getPlayerIntent(context, PlayerService::class.java, queue, PlayerIntentType.AllOthers)
            .putExtra(Player.PLAYER_TYPE, PlayerType.AUDIO)
            .putExtra(Player.RESUME_PLAYBACK, resumePlayback)
        ContextCompat.startForegroundService(context, intent)
    }

    /* ENQUEUE */
    @JvmStatic
    fun enqueueOnPlayer(context: Context, queue: PlayQueue, playerType: PlayerType) {
        if (playerType == PlayerType.POPUP && !PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }

        Toast.makeText(context, R.string.enqueued, Toast.LENGTH_SHORT).show()

        val intent = getPlayerIntent(context, PlayerService::class.java, queue, PlayerIntentType.Enqueue)
            .putExtra(Player.RESUME_PLAYBACK, false)
            .putExtra(Player.PLAYER_TYPE, playerType)
        ContextCompat.startForegroundService(context, intent)
    }

    @JvmStatic
    fun enqueueOnPlayer(context: Context, queue: PlayQueue) {
        var playerType = PlayerHolder.type
        if (playerType == null) {
            Log.e(TAG, "Enqueueing but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }

        enqueueOnPlayer(context, queue, playerType)
    }

    /* ENQUEUE NEXT */
    @JvmStatic
    fun enqueueNextOnPlayer(context: Context, queue: PlayQueue) {
        var playerType = PlayerHolder.type
        if (playerType == null) {
            Log.e(TAG, "Enqueueing next but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }
        Toast.makeText(context, R.string.enqueued_next, Toast.LENGTH_SHORT).show()
        val intent = getPlayerEnqueueNextIntent(context, PlayerService::class.java, queue)
            .putExtra(Player.PLAYER_TYPE, playerType as java.io.Serializable?)
        ContextCompat.startForegroundService(context, intent)
    }

    // -------------------------------------------------------------------------
    // External Players
    // -------------------------------------------------------------------------

    @JvmStatic
    fun playOnExternalAudioPlayer(context: Context, info: StreamInfo) {
        val audioStreams = info.audioStreams
        if (audioStreams.isNullOrEmpty()) {
            Toast.makeText(context, R.string.audio_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val audioStreamsForExternalPlayers = ListHelper.getUrlAndNonTorrentStreams(audioStreams)
        if (audioStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(context, R.string.no_audio_streams_available_for_external_players, Toast.LENGTH_SHORT).show()
            return
        }

        val index = ListHelper.getDefaultAudioFormat(context, audioStreamsForExternalPlayers)
        val audioStream = audioStreamsForExternalPlayers[index]

        playOnExternalPlayer(context, info.name, info.uploaderName, audioStream)
    }

    @JvmStatic
    fun playOnExternalVideoPlayer(context: Context, info: StreamInfo) {
        val videoStreams = info.videoStreams
        if (videoStreams.isNullOrEmpty()) {
            Toast.makeText(context, R.string.video_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val videoStreamsForExternalPlayers = ListHelper.getSortedStreamVideosList(
            context,
            ListHelper.getUrlAndNonTorrentStreams(videoStreams), null, false, false
        )
        if (videoStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(context, R.string.no_video_streams_available_for_external_players, Toast.LENGTH_SHORT).show()
            return
        }

        val index = ListHelper.getDefaultResolutionIndex(context, videoStreamsForExternalPlayers)
        val videoStream = videoStreamsForExternalPlayers[index]
        playOnExternalPlayer(context, info.name, info.uploaderName, videoStream)
    }

    @JvmStatic
    fun playOnExternalPlayer(context: Context, name: String?, artist: String?, stream: Stream) {
        val deliveryMethod = stream.deliveryMethod
        val mimeType = if (!stream.isUrl || deliveryMethod == DeliveryMethod.TORRENT) {
            Toast.makeText(context, R.string.selected_stream_external_player_not_supported, Toast.LENGTH_SHORT).show()
            return
        } else {
            when (deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> {
                    if (stream.format == null) {
                        when (stream) {
                            is AudioStream -> "audio/*"
                            is VideoStream -> "video/*"
                            else -> return
                        }
                    } else {
                        stream.format!!.mimeType
                    }
                }
                DeliveryMethod.HLS -> "application/x-mpegURL"
                DeliveryMethod.DASH -> "application/dash+xml"
                DeliveryMethod.SS -> "application/vnd.ms-sstr+xml"
                else -> ""
            }
        }

        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.parse(stream.content), mimeType)
            putExtra(Intent.EXTRA_TITLE, name)
            putExtra("title", name)
            putExtra("artist", artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        resolveActivityOrAskToInstall(context, intent)
    }

    @JvmStatic
    fun resolveActivityOrAskToInstall(context: Context, intent: Intent) {
        if (!ShareUtils.tryOpenIntentInApp(context, intent)) {
            if (context is Activity) {
                AlertDialog.Builder(context)
                    .setMessage(R.string.no_player_found)
                    .setPositiveButton(R.string.install) { _, _ ->
                        ShareUtils.installApp(context, context.getString(R.string.vlc_package))
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        Log.i("NavigationHelper", "You unlocked a secret unicorn.")
                    }
                    .show()
            } else {
                Toast.makeText(context, R.string.no_player_found_toast, Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Through FragmentManager
    // -------------------------------------------------------------------------

    @SuppressLint("CommitTransaction")
    private fun defaultTransaction(fragmentManager: FragmentManager): FragmentTransaction {
        return fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.animator.custom_fade_in, R.animator.custom_fade_out,
                R.animator.custom_fade_in, R.animator.custom_fade_out
            )
    }

    @JvmStatic
    fun gotoMainFragment(fragmentManager: FragmentManager) {
        val popped = fragmentManager.popBackStackImmediate(MAIN_FRAGMENT_TAG, 0)
        if (!popped) {
            openMainFragment(fragmentManager)
        }
    }

    @JvmStatic
    fun openMainFragment(fragmentManager: FragmentManager) {
        InfoCache.trimCache()

        // fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, MainFragment())
        //    .addToBackStack(MAIN_FRAGMENT_TAG)
        //    .commit()
    }

    @JvmStatic
    fun tryGotoSearchFragment(fragmentManager: FragmentManager): Boolean {
        if (DebugConstants.DEBUG) {
            for (i in 0 until fragmentManager.backStackEntryCount) {
                Log.d("NavigationHelper", "tryGoToSearchFragment() [$i] = [${fragmentManager.getBackStackEntryAt(i)}]")
            }
        }

        return fragmentManager.popBackStackImmediate(SEARCH_FRAGMENT_TAG, 0)
    }

    @JvmStatic
    fun openSearchFragment(fragmentManager: FragmentManager, serviceId: Int, searchString: String) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, SearchFragment.getInstance(serviceId, searchString))
        //    .addToBackStack(SEARCH_FRAGMENT_TAG)
        //    .commit()
    }

    @JvmStatic
    fun expandMainPlayer(context: Context) {
        // context.sendBroadcast(Intent(VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER))
    }

    @JvmStatic
    fun sendPlayerStartedEvent(context: Context) {
        // context.sendBroadcast(Intent(VideoDetailFragment.ACTION_PLAYER_STARTED))
    }

    @JvmStatic
    fun showMiniPlayer(fragmentManager: FragmentManager) {
        // val instance = VideoDetailFragment.getInstanceInCollapsedState()
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_player_holder, instance)
        //    .runOnCommit { sendPlayerStartedEvent(instance.requireActivity()) }
        //    .commitAllowingStateLoss()
    }

    @JvmStatic
    fun openVideoDetailFragment(fragmentManager: androidx.fragment.app.FragmentManager, serviceId: Int, url: String, title: String?, autoPlay: Boolean = false) { }
//         context: Context,
//         fragmentManager: FragmentManager,
//         serviceId: Int,
//         url: String?,
//         title: String,
//         playQueue: PlayQueue?,
//         switchingPlayers: Boolean
//     ) {
//         val playerType = PlayerHolder.type
//         val autoPlay = when {
//             playerType == null -> PlayerHelper.isAutoplayAllowedByUser(context)
//             switchingPlayers -> PlayerHolder.isPlaying
//             playerType == PlayerType.MAIN -> PlayerHelper.isAutoplayAllowedByUser(context)
//             else -> false
//         }
// 
//         val onVideoDetailFragmentReady = { detailFragment: VideoDetailFragment ->
//             expandMainPlayer(detailFragment.requireActivity())
//             detailFragment.setAutoPlay(autoPlay)
//             if (switchingPlayers && detailFragment.url == url) {
//                 detailFragment.openVideoPlayer(
//                     playerType == PlayerType.POPUP || PlayerHelper.isStartMainPlayerFullscreenEnabled(context)
//                 )
//             } else {
//                 if (switchingPlayers && playerType == PlayerType.POPUP) {
//                     detailFragment.setForceFullscreen(true)
//                 }
//                 detailFragment.selectAndLoadVideo(serviceId, url, title, playQueue)
//             }
//             detailFragment.scrollToTop()
//         }
// 
//         val fragment = fragmentManager.findFragmentById(R.id.fragment_player_holder)
//         if (fragment is VideoDetailFragment && fragment.isVisible) {
//             onVideoDetailFragmentReady(fragment)
//         } else {
//             val instance = VideoDetailFragment.getInstance(serviceId, null, title, playQueue)
//             instance.setAutoPlay(autoPlay)
// 
//             defaultTransaction(fragmentManager)
//                 .replace(R.id.fragment_player_holder, instance)
//                 .runOnCommit { onVideoDetailFragmentReady(instance) }
//                 .commit()
//         }
//     }

    @JvmStatic
    fun openChannelFragment(fragmentManager: FragmentManager, serviceId: Int, url: String, name: String) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, ChannelFragment.getInstance(serviceId, url, name))
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openChannelFragment(fragment: Fragment, item: StreamInfoItem, uploaderUrl: String) {
        openChannelFragment(
            fragment.requireActivity().supportFragmentManager,
            item.serviceId, uploaderUrl, item.uploaderName
        )
    }

    @JvmStatic
    fun openCommentAuthorIfPresent(activity: FragmentActivity, comment: CommentsInfoItem) {
        if (comment.uploaderUrl.isNullOrEmpty()) {
            return
        }
        try {
            openChannelFragment(
                activity.supportFragmentManager, comment.serviceId,
                comment.uploaderUrl, comment.uploaderName
            )
        } catch (e: Exception) {
            ErrorUtil.showUiErrorSnackbar(activity, "Opening channel fragment", e)
        }
    }

    @JvmStatic
    fun openCommentRepliesFragment(activity: FragmentActivity, comment: CommentsInfoItem) {
        closeCommentRepliesFragments(activity)
        // defaultTransaction(activity.supportFragmentManager)
        //    .replace(R.id.fragment_holder, CommentRepliesFragment(comment), CommentRepliesFragment.TAG)
        //    .addToBackStack(CommentRepliesFragment.TAG)
        //    .commit()
    }

    @JvmStatic
    fun closeCommentRepliesFragments(activity: FragmentActivity) {
        val fm = activity.supportFragmentManager
        // val tx = defaultTransaction(fm)
        var removed = false
        for (fragment in fm.fragments) {
            // if (fragment != null && CommentRepliesFragment.TAG == fragment.tag) {
            //     tx.remove(fragment)
            //     removed = true
            // }
        }
        if (removed) {
            // tx.commit()
        }

        // while (fm.backStackEntryCount > 0 &&
        //     CommentRepliesFragment.TAG == fm.getBackStackEntryAt(fm.backStackEntryCount - 1).name
        // ) {
        //     fm.popBackStackImmediate(CommentRepliesFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // }
    }

    @JvmStatic
    fun openPlaylistFragment(fragmentManager: FragmentManager, serviceId: Int, url: String, name: String) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, PlaylistFragment.getInstance(serviceId, url, name))
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openFeedFragment(fragmentManager: FragmentManager) {
        openFeedFragment(fragmentManager, FeedGroupEntity.GROUP_ALL_ID, null)
    }

    @JvmStatic
    fun openFeedFragment(fragmentManager: FragmentManager, groupId: Long, groupName: String?) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, FeedFragment.newInstance(groupId, groupName))
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openBookmarksFragment(fragmentManager: FragmentManager) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, BookmarkFragment())
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openSubscriptionFragment(fragmentManager: FragmentManager) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, SubscriptionFragment())
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun openKioskFragment(fragmentManager: FragmentManager, serviceId: Int, kioskId: String) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, KioskFragment.getInstance(serviceId, kioskId))
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openLocalPlaylistFragment(fragmentManager: FragmentManager, playlistId: Long, name: String?) {
        // defaultTransaction(fragmentManager)
        //    .replace(R.id.fragment_holder, LocalPlaylistFragment.getInstance(playlistId, name ?: ""))
        //    .addToBackStack(null)
        //    .commit()
    }

    @JvmStatic
    fun openStatisticFragment(fragmentManager: androidx.fragment.app.FragmentManager) { }
//         defaultTransaction(fragmentManager)
//             .replace(R.id.fragment_holder, StatisticsPlaylistFragment())
//             .addToBackStack(null)
//             .commit()
//     }

    @JvmStatic
    fun openSubscriptionsImportFragment(fragmentManager: androidx.fragment.app.FragmentManager, serviceId: Int) { }
//         defaultTransaction(fragmentManager)
//             .replace(R.id.fragment_holder, SubscriptionsImportFragment.getInstance(serviceId))
//             .addToBackStack(null)
//             .commit()
//     }

    // -------------------------------------------------------------------------
    // Through Intents
    // -------------------------------------------------------------------------

    @JvmStatic
    fun openSearch(context: Context, serviceId: Int, searchString: String) {
        val mIntent = Intent(context, MainActivity::class.java).apply {
//             putExtra(Constants.KEY_SERVICE_ID, serviceId)
//             putExtra(Constants.KEY_SEARCH_STRING, searchString)
//             putExtra(Constants.KEY_OPEN_SEARCH, true)
        }
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openVideoDetail(
        context: Context,
        serviceId: Int,
        url: String,
        title: String,
        playQueue: PlayQueue?,
        switchingPlayers: Boolean
    ) {
        val intent = getStreamIntent(context, serviceId, url, title)
            // .putExtra(VideoDetailFragment.KEY_SWITCHING_PLAYERS, switchingPlayers)

        playQueue?.let {
            val cacheKey = SerializedCache.getInstance().put(it, PlayQueue::class.java)
            if (cacheKey != null) {
                intent.putExtra(Player.PLAY_QUEUE_KEY, cacheKey)
            }
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun openChannelFragmentUsingIntent(context: Context, serviceId: Int, url: String, title: String) {
        val intent = getOpenIntent(context, url, serviceId, StreamingService.LinkType.CHANNEL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//             putExtra(Constants.KEY_TITLE, title)
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun openMainActivity(context: Context) {
        val mIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openRouterActivity(context: Context, url: String) {
        val mIntent = Intent(context, RouterActivity::class.java).apply {
            data = Uri.parse(url)
        }
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openAbout(context: Context) {
        context.navigateTo(Destination.About)
    }

    @JvmStatic
    fun openSettings(context: Context) {
        val intent = Intent(context, SettingsActivity::class.java)
        context.startActivity(intent)
    }

    @JvmStatic
    fun openDownloads(activity: Activity) {
        if (PermissionHelper.checkStoragePermissions(activity, PermissionHelper.DOWNLOADS_REQUEST_CODE)) {
            val intent = Intent(activity, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_DOWNLOADS
                putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_DOWNLOADS)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            activity.startActivity(intent)
        }
    }

    @JvmStatic
    fun getPlayQueueActivityIntent(context: Context): Intent {
        val intent = Intent(context, PlayQueueActivity::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return intent
    }

    @JvmStatic
    fun openPlayQueue(context: Context) {
        val intent = Intent(context, PlayQueueActivity::class.java)
        context.startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Link handling
    // -------------------------------------------------------------------------

    private fun getOpenIntent(
        context: Context,
        url: String,
        serviceId: Int,
        type: StreamingService.LinkType
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
//             putExtra(Constants.KEY_SERVICE_ID, serviceId)
//             putExtra(Constants.KEY_URL, url)
//             putExtra(Constants.KEY_LINK_TYPE, type)
        }
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun getIntentByLink(context: Context, url: String): Intent {
        return getIntentByLink(context, NewPipe.getServiceByUrl(url), url)
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun getIntentByLink(context: Context, service: StreamingService, url: String): Intent {
        val linkType = service.getLinkTypeByUrl(url)
        if (linkType == StreamingService.LinkType.NONE) {
            throw ExtractionException("Url not known to service. service=$service url=$url")
        }
        return getOpenIntent(context, url, service.serviceId, linkType)
    }

    @JvmStatic
    fun getChannelIntent(context: Context, serviceId: Int, url: String): Intent {
        return getOpenIntent(context, url, serviceId, StreamingService.LinkType.CHANNEL)
    }

    @JvmStatic
    fun getStreamIntent(context: Context, serviceId: Int, url: String, title: String?): Intent {
        return getOpenIntent(context, url, serviceId, StreamingService.LinkType.STREAM)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//             .putExtra(Constants.KEY_TITLE, title)
    }

    @JvmStatic
    fun restartApp(activity: Activity) {
        NewPipeDatabase.close()
        ProcessPhoenix.triggerRebirth(activity.applicationContext)
    }
}
