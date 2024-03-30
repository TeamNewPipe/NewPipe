package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
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
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.download.DownloadActivity
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.local.subscription.SubscriptionsImportFragment
import org.schabi.newpipe.player.PlayQueueActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.external_communication.ShareUtils

object NavigationHelper {
    val MAIN_FRAGMENT_TAG: String = "main_fragment_tag"
    val SEARCH_FRAGMENT_TAG: String = "search_fragment_tag"
    private val TAG: String = NavigationHelper::class.java.getSimpleName()

    /*//////////////////////////////////////////////////////////////////////////
    // Players
    ////////////////////////////////////////////////////////////////////////// */
    /* INTENT */
    fun <T> getPlayerIntent(context: Context,
                            targetClazz: Class<T>,
                            playQueue: PlayQueue?,
                            resumePlayback: Boolean): Intent {
        val intent: Intent = Intent(context, targetClazz)
        if (playQueue != null) {
            val cacheKey: String? = SerializedCache.Companion.getInstance().put<PlayQueue>(playQueue, PlayQueue::class.java)
            if (cacheKey != null) {
                intent.putExtra(Player.Companion.PLAY_QUEUE_KEY, cacheKey)
            }
        }
        intent.putExtra(Player.Companion.PLAYER_TYPE, PlayerType.MAIN.valueForIntent())
        intent.putExtra(Player.Companion.RESUME_PLAYBACK, resumePlayback)
        return intent
    }

    fun <T> getPlayerIntent(context: Context,
                            targetClazz: Class<T>,
                            playQueue: PlayQueue?,
                            resumePlayback: Boolean,
                            playWhenReady: Boolean): Intent {
        return getPlayerIntent<T>(context, targetClazz, playQueue, resumePlayback)
                .putExtra(Player.Companion.PLAY_WHEN_READY, playWhenReady)
    }

    fun <T> getPlayerEnqueueIntent(context: Context,
                                   targetClazz: Class<T>,
                                   playQueue: PlayQueue?): Intent {
        // when enqueueing `resumePlayback` is always `false` since:
        // - if there is a video already playing, the value of `resumePlayback` just doesn't make
        //   any difference.
        // - if there is nothing already playing, it is useful for the enqueue action to have a
        //   slightly different behaviour than the normal play action: the latter resumes playback,
        //   the former doesn't. (note that enqueue can be triggered when nothing is playing only
        //   by long pressing the video detail fragment, playlist or channel controls
        return getPlayerIntent<T>(context, targetClazz, playQueue, false)
                .putExtra(Player.Companion.ENQUEUE, true)
    }

    fun <T> getPlayerEnqueueNextIntent(context: Context,
                                       targetClazz: Class<T>,
                                       playQueue: PlayQueue?): Intent {
        // see comment in `getPlayerEnqueueIntent` as to why `resumePlayback` is false
        return getPlayerIntent<T>(context, targetClazz, playQueue, false)
                .putExtra(Player.Companion.ENQUEUE_NEXT, true)
    }

    /* PLAY */
    fun playOnMainPlayer(activity: AppCompatActivity,
                         playQueue: PlayQueue) {
        val item: PlayQueueItem? = playQueue.getItem()
        if (item != null) {
            openVideoDetailFragment(activity, activity.getSupportFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getTitle(), playQueue,
                    false)
        }
    }

    fun playOnMainPlayer(context: Context,
                         playQueue: PlayQueue,
                         switchingPlayers: Boolean) {
        val item: PlayQueueItem? = playQueue.getItem()
        if (item != null) {
            openVideoDetail(context,
                    item.getServiceId(), item.getUrl(), item.getTitle(), playQueue,
                    switchingPlayers)
        }
    }

    fun playOnPopupPlayer(context: Context?,
                          queue: PlayQueue?,
                          resumePlayback: Boolean) {
        if (!PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }
        Toast.makeText(context, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show()
        val intent: Intent = getPlayerIntent((context)!!, PlayerService::class.java, queue, resumePlayback)
        intent.putExtra(Player.Companion.PLAYER_TYPE, PlayerType.POPUP.valueForIntent())
        ContextCompat.startForegroundService((context), intent)
    }

    fun playOnBackgroundPlayer(context: Context?,
                               queue: PlayQueue?,
                               resumePlayback: Boolean) {
        Toast.makeText(context, R.string.background_player_playing_toast, Toast.LENGTH_SHORT)
                .show()
        val intent: Intent = getPlayerIntent((context)!!, PlayerService::class.java, queue, resumePlayback)
        intent.putExtra(Player.Companion.PLAYER_TYPE, PlayerType.AUDIO.valueForIntent())
        ContextCompat.startForegroundService((context), intent)
    }

    /* ENQUEUE */
    fun enqueueOnPlayer(context: Context,
                        queue: PlayQueue?,
                        playerType: PlayerType) {
        if (playerType == PlayerType.POPUP && !PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }
        Toast.makeText(context, R.string.enqueued, Toast.LENGTH_SHORT).show()
        val intent: Intent = getPlayerEnqueueIntent(context, PlayerService::class.java, queue)
        intent.putExtra(Player.Companion.PLAYER_TYPE, playerType.valueForIntent())
        ContextCompat.startForegroundService(context, intent)
    }

    fun enqueueOnPlayer(context: Context?, queue: PlayQueue?) {
        var playerType: PlayerType? = PlayerHolder.Companion.getInstance().getType()
        if (playerType == null) {
            Log.e(TAG, "Enqueueing but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }
        enqueueOnPlayer((context)!!, queue, playerType)
    }

    /* ENQUEUE NEXT */
    fun enqueueNextOnPlayer(context: Context?, queue: PlayQueue?) {
        var playerType: PlayerType? = PlayerHolder.Companion.getInstance().getType()
        if (playerType == null) {
            Log.e(TAG, "Enqueueing next but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }
        Toast.makeText(context, R.string.enqueued_next, Toast.LENGTH_SHORT).show()
        val intent: Intent = getPlayerEnqueueNextIntent((context)!!, PlayerService::class.java, queue)
        intent.putExtra(Player.Companion.PLAYER_TYPE, playerType.valueForIntent())
        ContextCompat.startForegroundService((context), intent)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // External Players
    ////////////////////////////////////////////////////////////////////////// */
    fun playOnExternalAudioPlayer(context: Context,
                                  info: StreamInfo) {
        val audioStreams: List<AudioStream>? = info.getAudioStreams()
        if (audioStreams == null || audioStreams.isEmpty()) {
            Toast.makeText(context, R.string.audio_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val audioStreamsForExternalPlayers: List<AudioStream?> = ListHelper.getUrlAndNonTorrentStreams(audioStreams)
        if (audioStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(context, R.string.no_audio_streams_available_for_external_players,
                    Toast.LENGTH_SHORT).show()
            return
        }
        val index: Int = ListHelper.getDefaultAudioFormat(context, audioStreamsForExternalPlayers)
        val audioStream: AudioStream? = audioStreamsForExternalPlayers.get(index)
        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), (audioStream)!!)
    }

    fun playOnExternalVideoPlayer(context: Context,
                                  info: StreamInfo) {
        val videoStreams: List<VideoStream>? = info.getVideoStreams()
        if (videoStreams == null || videoStreams.isEmpty()) {
            Toast.makeText(context, R.string.video_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val videoStreamsForExternalPlayers: List<VideoStream?> = ListHelper.getSortedStreamVideosList(context,
                ListHelper.getUrlAndNonTorrentStreams(videoStreams), null, false, false)
        if (videoStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(context, R.string.no_video_streams_available_for_external_players,
                    Toast.LENGTH_SHORT).show()
            return
        }
        val index: Int = ListHelper.getDefaultResolutionIndex(context,
                videoStreamsForExternalPlayers)
        val videoStream: VideoStream? = videoStreamsForExternalPlayers.get(index)
        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), (videoStream)!!)
    }

    fun playOnExternalPlayer(context: Context,
                             name: String?,
                             artist: String?,
                             stream: Stream) {
        val deliveryMethod: DeliveryMethod = stream.getDeliveryMethod()
        val mimeType: String
        if (!stream.isUrl() || deliveryMethod == DeliveryMethod.TORRENT) {
            Toast.makeText(context, R.string.selected_stream_external_player_not_supported,
                    Toast.LENGTH_SHORT).show()
            return
        }
        when (deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> if (stream.getFormat() == null) {
                if (stream is AudioStream) {
                    mimeType = "audio/*"
                } else if (stream is VideoStream) {
                    mimeType = "video/*"
                } else {
                    // This should never be reached, because subtitles are not opened in
                    // external players
                    return
                }
            } else {
                mimeType = stream.getFormat()!!.getMimeType()
            }

            DeliveryMethod.HLS -> mimeType = "application/x-mpegURL"
            DeliveryMethod.DASH -> mimeType = "application/dash+xml"
            DeliveryMethod.SS -> mimeType = "application/vnd.ms-sstr+xml"
            else ->                 // Torrent streams are not exposed to external players
                mimeType = ""
        }
        val intent: Intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(stream.getContent()), mimeType)
        intent.putExtra(Intent.EXTRA_TITLE, name)
        intent.putExtra("title", name)
        intent.putExtra("artist", artist)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        resolveActivityOrAskToInstall(context, intent)
    }

    fun resolveActivityOrAskToInstall(context: Context,
                                      intent: Intent) {
        if (!ShareUtils.tryOpenIntentInApp(context, intent)) {
            if (context is Activity) {
                AlertDialog.Builder(context)
                        .setMessage(R.string.no_player_found)
                        .setPositiveButton(R.string.install, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                            ShareUtils.installApp(context,
                                    context.getString(R.string.vlc_package))
                        }))
                        .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> Log.i("NavigationHelper", "You unlocked a secret unicorn.") }))
                        .show()
            } else {
                Toast.makeText(context, R.string.no_player_found_toast, Toast.LENGTH_LONG).show()
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through FragmentManager
    ////////////////////////////////////////////////////////////////////////// */
    @SuppressLint("CommitTransaction")
    private fun defaultTransaction(fragmentManager: FragmentManager?): FragmentTransaction {
        return fragmentManager!!.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out,
                        R.animator.custom_fade_in, R.animator.custom_fade_out)
    }

    fun gotoMainFragment(fragmentManager: FragmentManager?) {
        val popped: Boolean = fragmentManager!!.popBackStackImmediate(MAIN_FRAGMENT_TAG, 0)
        if (!popped) {
            openMainFragment(fragmentManager)
        }
    }

    fun openMainFragment(fragmentManager: FragmentManager?) {
        InfoCache.Companion.getInstance().trimCache()
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, MainFragment())
                .addToBackStack(MAIN_FRAGMENT_TAG)
                .commit()
    }

    fun tryGotoSearchFragment(fragmentManager: FragmentManager): Boolean {
        if (MainActivity.Companion.DEBUG) {
            for (i in 0 until fragmentManager.getBackStackEntryCount()) {
                Log.d("NavigationHelper", ("tryGoToSearchFragment() [" + i + "]"
                        + " = [" + fragmentManager.getBackStackEntryAt(i) + "]"))
            }
        }
        return fragmentManager.popBackStackImmediate(SEARCH_FRAGMENT_TAG, 0)
    }

    fun openSearchFragment(fragmentManager: FragmentManager?,
                           serviceId: Int, searchString: String?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, SearchFragment.Companion.getInstance(serviceId, searchString))
                .addToBackStack(SEARCH_FRAGMENT_TAG)
                .commit()
    }

    fun expandMainPlayer(context: Context) {
        context.sendBroadcast(Intent(VideoDetailFragment.Companion.ACTION_SHOW_MAIN_PLAYER))
    }

    fun sendPlayerStartedEvent(context: Context) {
        context.sendBroadcast(Intent(VideoDetailFragment.Companion.ACTION_PLAYER_STARTED))
    }

    fun showMiniPlayer(fragmentManager: FragmentManager?) {
        val instance: VideoDetailFragment = VideoDetailFragment.Companion.getInstanceInCollapsedState()
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_player_holder, instance)
                .runOnCommit(Runnable({ sendPlayerStartedEvent(instance.requireActivity()) }))
                .commitAllowingStateLoss()
    }

    fun openVideoDetailFragment(context: Context,
                                fragmentManager: FragmentManager,
                                serviceId: Int,
                                url: String?,
                                title: String,
                                playQueue: PlayQueue?,
                                switchingPlayers: Boolean) {
        val autoPlay: Boolean
        val playerType: PlayerType? = PlayerHolder.Companion.getInstance().getType()
        if (playerType == null) {
            // no player open
            autoPlay = PlayerHelper.isAutoplayAllowedByUser(context)
        } else if (switchingPlayers) {
            // switching player to main player
            autoPlay = PlayerHolder.Companion.getInstance().isPlaying() // keep play/pause state
        } else if (playerType == PlayerType.MAIN) {
            // opening new stream while already playing in main player
            autoPlay = PlayerHelper.isAutoplayAllowedByUser(context)
        } else {
            // opening new stream while already playing in another player
            autoPlay = false
        }
        val onVideoDetailFragmentReady: RunnableWithVideoDetailFragment = RunnableWithVideoDetailFragment({ detailFragment: VideoDetailFragment ->
            expandMainPlayer(detailFragment.requireActivity())
            detailFragment.setAutoPlay(autoPlay)
            if (switchingPlayers) {
                // Situation when user switches from players to main player. All needed data is
                // here, we can start watching (assuming newQueue equals playQueue).
                // Starting directly in fullscreen if the previous player type was popup.
                detailFragment.openVideoPlayer((playerType == PlayerType.POPUP
                        || PlayerHelper.isStartMainPlayerFullscreenEnabled(context)))
            } else {
                detailFragment.selectAndLoadVideo(serviceId, url, title, playQueue)
            }
            detailFragment.scrollToTop()
        })
        val fragment: Fragment? = fragmentManager.findFragmentById(R.id.fragment_player_holder)
        if (fragment is VideoDetailFragment && fragment.isVisible()) {
            onVideoDetailFragmentReady.run(fragment as VideoDetailFragment?)
        } else {
            val instance: VideoDetailFragment = VideoDetailFragment.Companion.getInstance(serviceId, url, title, playQueue)
            instance.setAutoPlay(autoPlay)
            defaultTransaction(fragmentManager)
                    .replace(R.id.fragment_player_holder, instance)
                    .runOnCommit(Runnable({ onVideoDetailFragmentReady.run(instance) }))
                    .commit()
        }
    }

    fun openChannelFragment(fragmentManager: FragmentManager?,
                            serviceId: Int, url: String?,
                            name: String) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, ChannelFragment.Companion.getInstance(serviceId, url, name))
                .addToBackStack(null)
                .commit()
    }

    fun openChannelFragment(fragment: Fragment,
                            item: StreamInfoItem,
                            uploaderUrl: String?) {
        // For some reason `getParentFragmentManager()` doesn't work, but this does.
        openChannelFragment(
                fragment.requireActivity().getSupportFragmentManager(),
                item.getServiceId(), uploaderUrl, item.getUploaderName())
    }

    /**
     * Opens the comment author channel fragment, if the [CommentsInfoItem.getUploaderUrl]
     * of `comment` is non-null. Shows a UI-error snackbar if something goes wrong.
     *
     * @param activity the activity with the fragment manager and in which to show the snackbar
     * @param comment the comment whose uploader/author will be opened
     */
    fun openCommentAuthorIfPresent(activity: FragmentActivity,
                                   comment: CommentsInfoItem) {
        if (TextUtils.isEmpty(comment.getUploaderUrl())) {
            return
        }
        try {
            openChannelFragment(activity.getSupportFragmentManager(), comment.getServiceId(),
                    comment.getUploaderUrl(), comment.getUploaderName())
        } catch (e: Exception) {
            showUiErrorSnackbar(activity, "Opening channel fragment", e)
        }
    }

    fun openCommentRepliesFragment(activity: FragmentActivity,
                                   comment: CommentsInfoItem) {
        defaultTransaction(activity.getSupportFragmentManager())
                .replace(R.id.fragment_holder, CommentRepliesFragment(comment),
                        CommentRepliesFragment.Companion.TAG)
                .addToBackStack(CommentRepliesFragment.Companion.TAG)
                .commit()
    }

    fun openPlaylistFragment(fragmentManager: FragmentManager?,
                             serviceId: Int, url: String?,
                             name: String) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, PlaylistFragment.Companion.getInstance(serviceId, url, name))
                .addToBackStack(null)
                .commit()
    }

    @JvmOverloads
    fun openFeedFragment(fragmentManager: FragmentManager?, groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
                         groupName: String? = null) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, newInstance(groupId, groupName))
                .addToBackStack(null)
                .commit()
    }

    fun openBookmarksFragment(fragmentManager: FragmentManager?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, BookmarkFragment())
                .addToBackStack(null)
                .commit()
    }

    fun openSubscriptionFragment(fragmentManager: FragmentManager?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, SubscriptionFragment())
                .addToBackStack(null)
                .commit()
    }

    @Throws(ExtractionException::class)
    fun openKioskFragment(fragmentManager: FragmentManager?, serviceId: Int,
                          kioskId: String?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, KioskFragment.Companion.getInstance(serviceId, kioskId))
                .addToBackStack(null)
                .commit()
    }

    fun openLocalPlaylistFragment(fragmentManager: FragmentManager?,
                                  playlistId: Long, name: String?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, LocalPlaylistFragment.Companion.getInstance(playlistId,
                        if (name == null) "" else name))
                .addToBackStack(null)
                .commit()
    }

    fun openStatisticFragment(fragmentManager: FragmentManager?) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, StatisticsPlaylistFragment())
                .addToBackStack(null)
                .commit()
    }

    fun openSubscriptionsImportFragment(fragmentManager: FragmentManager?,
                                        serviceId: Int) {
        defaultTransaction(fragmentManager)
                .replace(R.id.fragment_holder, SubscriptionsImportFragment.Companion.getInstance(serviceId))
                .addToBackStack(null)
                .commit()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through Intents
    ////////////////////////////////////////////////////////////////////////// */
    fun openSearch(context: Context, serviceId: Int,
                   searchString: String?) {
        val mIntent: Intent = Intent(context, MainActivity::class.java)
        mIntent.putExtra(KEY_SERVICE_ID, serviceId)
        mIntent.putExtra(KEY_SEARCH_STRING, searchString)
        mIntent.putExtra(KEY_OPEN_SEARCH, true)
        context.startActivity(mIntent)
    }

    fun openVideoDetail(context: Context,
                        serviceId: Int,
                        url: String?,
                        title: String,
                        playQueue: PlayQueue?,
                        switchingPlayers: Boolean) {
        val intent: Intent = getStreamIntent(context, serviceId, url, title)
                .putExtra(VideoDetailFragment.Companion.KEY_SWITCHING_PLAYERS, switchingPlayers)
        if (playQueue != null) {
            val cacheKey: String? = SerializedCache.Companion.getInstance().put<PlayQueue>(playQueue, PlayQueue::class.java)
            if (cacheKey != null) {
                intent.putExtra(Player.Companion.PLAY_QUEUE_KEY, cacheKey)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Opens [ChannelFragment].
     * Use this instead of [.openChannelFragment]
     * when no fragments are used / no FragmentManager is available.
     * @param context
     * @param serviceId
     * @param url
     * @param title
     */
    fun openChannelFragmentUsingIntent(context: Context,
                                       serviceId: Int,
                                       url: String?,
                                       title: String) {
        val intent: Intent = getOpenIntent(context, url, serviceId,
                LinkType.CHANNEL)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(KEY_TITLE, title)
        context.startActivity(intent)
    }

    fun openMainActivity(context: Context) {
        val mIntent: Intent = Intent(context, MainActivity::class.java)
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(mIntent)
    }

    fun openRouterActivity(context: Context, url: String?) {
        val mIntent: Intent = Intent(context, RouterActivity::class.java)
        mIntent.setData(Uri.parse(url))
        context.startActivity(mIntent)
    }

    fun openAbout(context: Context) {
        val intent: Intent = Intent(context, AboutActivity::class.java)
        context.startActivity(intent)
    }

    fun openSettings(context: Context) {
        val intent: Intent = Intent(context, SettingsActivity::class.java)
        context.startActivity(intent)
    }

    fun openDownloads(activity: Activity) {
        if (PermissionHelper.checkStoragePermissions(
                        activity, PermissionHelper.DOWNLOADS_REQUEST_CODE)) {
            val intent: Intent = Intent(activity, DownloadActivity::class.java)
            activity.startActivity(intent)
        }
    }

    fun getPlayQueueActivityIntent(context: Context?): Intent {
        val intent: Intent = Intent(context, PlayQueueActivity::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun openPlayQueue(context: Context) {
        val intent: Intent = Intent(context, PlayQueueActivity::class.java)
        context.startActivity(intent)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Link handling
    ////////////////////////////////////////////////////////////////////////// */
    private fun getOpenIntent(context: Context, url: String?,
                              serviceId: Int, type: LinkType): Intent {
        val mIntent: Intent = Intent(context, MainActivity::class.java)
        mIntent.putExtra(KEY_SERVICE_ID, serviceId)
        mIntent.putExtra(KEY_URL, url)
        mIntent.putExtra(KEY_LINK_TYPE, type)
        return mIntent
    }

    @Throws(ExtractionException::class)
    fun getIntentByLink(context: Context, url: String?): Intent {
        return getIntentByLink(context, NewPipe.getServiceByUrl(url), url)
    }

    @Throws(ExtractionException::class)
    fun getIntentByLink(context: Context,
                        service: StreamingService,
                        url: String?): Intent {
        val linkType: LinkType = service.getLinkTypeByUrl(url)
        if (linkType == LinkType.NONE) {
            throw ExtractionException(("Url not known to service. service=" + service
                    + " url=" + url))
        }
        return getOpenIntent(context, url, service.getServiceId(), linkType)
    }

    fun getChannelIntent(context: Context,
                         serviceId: Int,
                         url: String?): Intent {
        return getOpenIntent(context, url, serviceId, LinkType.CHANNEL)
    }

    fun getStreamIntent(context: Context,
                        serviceId: Int,
                        url: String?,
                        title: String?): Intent {
        return getOpenIntent(context, url, serviceId, LinkType.STREAM)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(KEY_TITLE, title)
    }

    /**
     * Finish this `Activity` as well as all `Activities` running below it
     * and then start `MainActivity`.
     *
     * @param activity the activity to finish
     */
    fun restartApp(activity: Activity?) {
        NewPipeDatabase.close()
        ProcessPhoenix.triggerRebirth(activity!!.getApplicationContext())
    }

    private open interface RunnableWithVideoDetailFragment {
        fun run(detailFragment: VideoDetailFragment?)
    }
}
