package org.schabi.newpipe.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.PlaybackParameters
import org.schabi.newpipe.QueueItemMenuUtil
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityPlayerQueueControlBinding
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.player.PlayerService.LocalBinder
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PermissionHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs
import kotlin.math.min

class PlayQueueActivity() : AppCompatActivity(), PlayerEventListener, OnSeekBarChangeListener, View.OnClickListener, PlaybackParameterDialog.Callback {
    private var player: Player? = null
    private var serviceBound: Boolean = false
    private var serviceConnection: ServiceConnection? = null
    private var seeking: Boolean = false

    ////////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////////
    private var queueControlBinding: ActivityPlayerQueueControlBinding? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var menu: Menu? = null

    ////////////////////////////////////////////////////////////////////////////
    // Activity Lifecycle
    ////////////////////////////////////////////////////////////////////////////
    override fun onCreate(savedInstanceState: Bundle?) {
        Localization.assureCorrectAppLanguage(this)
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
        queueControlBinding = ActivityPlayerQueueControlBinding.inflate(getLayoutInflater())
        setContentView(queueControlBinding!!.getRoot())
        setSupportActionBar(queueControlBinding!!.toolbar)
        if (getSupportActionBar() != null) {
            getSupportActionBar()!!.setDisplayHomeAsUpEnabled(true)
            getSupportActionBar()!!.setTitle(R.string.title_activity_play_queue)
        }
        serviceConnection = getServiceConnection()
        bind()
    }

    public override fun onCreateOptionsMenu(m: Menu): Boolean {
        menu = m
        getMenuInflater().inflate(R.menu.menu_play_queue, m)
        getMenuInflater().inflate(R.menu.menu_play_queue_bg, m)
        buildAudioTrackMenu()
        onMaybeMuteChanged()
        // to avoid null reference
        if (player != null) {
            onPlaybackParameterChanged(player!!.getPlaybackParameters())
        }
        return true
    }

    // Allow to setup visibility of menuItems
    public override fun onPrepareOptionsMenu(m: Menu): Boolean {
        if (player != null) {
            menu!!.findItem(R.id.action_switch_popup)
                    .setVisible(!player!!.popupPlayerSelected())
            menu!!.findItem(R.id.action_switch_background)
                    .setVisible(!player!!.audioPlayerSelected())
        }
        return super.onPrepareOptionsMenu(m)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_settings -> {
                NavigationHelper.openSettings(this)
                return true
            }

            R.id.action_append_playlist -> {
                PlaylistDialog.Companion.showForPlayQueue(player, getSupportFragmentManager())
                return true
            }

            R.id.action_playback_speed -> {
                openPlaybackParameterDialog()
                return true
            }

            R.id.action_mute -> {
                player!!.toggleMute()
                return true
            }

            R.id.action_system_audio -> {
                startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                return true
            }

            R.id.action_switch_main -> {
                player!!.setRecovery()
                NavigationHelper.playOnMainPlayer(this, (player!!.getPlayQueue())!!, true)
                return true
            }

            R.id.action_switch_popup -> {
                if (PermissionHelper.isPopupEnabledElseAsk(this)) {
                    player!!.setRecovery()
                    NavigationHelper.playOnPopupPlayer(this, player!!.getPlayQueue(), true)
                }
                return true
            }

            R.id.action_switch_background -> {
                player!!.setRecovery()
                NavigationHelper.playOnBackgroundPlayer(this, player!!.getPlayQueue(), true)
                return true
            }
        }
        if (item.getGroupId() == MENU_ID_AUDIO_TRACK) {
            onAudioTrackClick(item.getItemId())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbind()
    }

    ////////////////////////////////////////////////////////////////////////////
    // Service Connection
    ////////////////////////////////////////////////////////////////////////////
    private fun bind() {
        val bindIntent: Intent = Intent(this, PlayerService::class.java)
        val success: Boolean = bindService(bindIntent, (serviceConnection)!!, BIND_AUTO_CREATE)
        if (!success) {
            unbindService((serviceConnection)!!)
        }
        serviceBound = success
    }

    private fun unbind() {
        if (serviceBound) {
            unbindService((serviceConnection)!!)
            serviceBound = false
            if (player != null) {
                player!!.removeActivityListener(this)
            }
            onQueueUpdate(null)
            if (itemTouchHelper != null) {
                itemTouchHelper!!.attachToRecyclerView(null)
            }
            itemTouchHelper = null
            player = null
        }
    }

    private fun getServiceConnection(): ServiceConnection {
        return object : ServiceConnection {
            public override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "Player service is disconnected")
            }

            public override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.d(TAG, "Player service is connected")
                if (service is LocalBinder) {
                    player = service.getPlayer()
                }
                if ((player == null) || (player!!.getPlayQueue() == null) || player!!.exoPlayerIsNull()) {
                    unbind()
                } else {
                    onQueueUpdate(player!!.getPlayQueue())
                    buildComponents()
                    if (player != null) {
                        player!!.setActivityListener(this@PlayQueueActivity)
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Building
    ////////////////////////////////////////////////////////////////////////////
    private fun buildComponents() {
        buildQueue()
        buildMetadata()
        buildSeekBar()
        buildControls()
    }

    private fun buildQueue() {
        queueControlBinding!!.playQueue.setLayoutManager(LinearLayoutManager(this))
        queueControlBinding!!.playQueue.setClickable(true)
        queueControlBinding!!.playQueue.setLongClickable(true)
        queueControlBinding!!.playQueue.clearOnScrollListeners()
        queueControlBinding!!.playQueue.addOnScrollListener(getQueueScrollListener())
        itemTouchHelper = ItemTouchHelper(getItemTouchCallback())
        itemTouchHelper!!.attachToRecyclerView(queueControlBinding!!.playQueue)
    }

    private fun buildMetadata() {
        queueControlBinding!!.metadata.setOnClickListener(this)
        queueControlBinding!!.songName.setSelected(true)
        queueControlBinding!!.artistName.setSelected(true)
    }

    private fun buildSeekBar() {
        queueControlBinding!!.seekBar.setOnSeekBarChangeListener(this)
        queueControlBinding!!.liveSync.setOnClickListener(this)
    }

    private fun buildControls() {
        queueControlBinding!!.controlRepeat.setOnClickListener(this)
        queueControlBinding!!.controlBackward.setOnClickListener(this)
        queueControlBinding!!.controlFastRewind.setOnClickListener(this)
        queueControlBinding!!.controlPlayPause.setOnClickListener(this)
        queueControlBinding!!.controlFastForward.setOnClickListener(this)
        queueControlBinding!!.controlForward.setOnClickListener(this)
        queueControlBinding!!.controlShuffle.setOnClickListener(this)
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Helpers
    ////////////////////////////////////////////////////////////////////////////
    private fun getQueueScrollListener(): OnScrollBelowItemsListener {
        return object : OnScrollBelowItemsListener() {
            public override fun onScrolledDown(recyclerView: RecyclerView?) {
                if ((player != null) && (player!!.getPlayQueue() != null
                                ) && !player!!.getPlayQueue()!!.isComplete()) {
                    player!!.getPlayQueue()!!.fetch()
                } else {
                    queueControlBinding!!.playQueue.clearOnScrollListeners()
                }
            }
        }
    }

    private fun getItemTouchCallback(): ItemTouchHelper.SimpleCallback {
        return object : PlayQueueItemTouchCallback() {
            public override fun onMove(sourceIndex: Int, targetIndex: Int) {
                if (player != null) {
                    player!!.getPlayQueue()!!.move(sourceIndex, targetIndex)
                }
            }

            public override fun onSwiped(index: Int) {
                if (index != -1) {
                    player!!.getPlayQueue()!!.remove(index)
                }
            }
        }
    }

    private fun getOnSelectedListener(): PlayQueueItemBuilder.OnSelectedListener {
        return object : PlayQueueItemBuilder.OnSelectedListener {
            public override fun selected(item: PlayQueueItem?, view: View?) {
                if (player != null) {
                    player!!.selectQueueItem(item)
                }
            }

            public override fun held(item: PlayQueueItem, view: View?) {
                if (player != null && player!!.getPlayQueue()!!.indexOf(item) != -1) {
                    QueueItemMenuUtil.openPopupMenu(player!!.getPlayQueue(), item, view, false,
                            getSupportFragmentManager(), this@PlayQueueActivity)
                }
            }

            public override fun onStartDrag(viewHolder: PlayQueueItemHolder?) {
                if (itemTouchHelper != null) {
                    itemTouchHelper!!.startDrag((viewHolder)!!)
                }
            }
        }
    }

    private fun scrollToSelected() {
        if (player == null) {
            return
        }
        val currentPlayingIndex: Int = player!!.getPlayQueue().getIndex()
        val currentVisibleIndex: Int
        if (queueControlBinding!!.playQueue.getLayoutManager() is LinearLayoutManager) {
            val layout: LinearLayoutManager? = queueControlBinding!!.playQueue.getLayoutManager() as LinearLayoutManager?
            currentVisibleIndex = layout!!.findFirstVisibleItemPosition()
        } else {
            currentVisibleIndex = 0
        }
        val distance: Int = abs((currentPlayingIndex - currentVisibleIndex).toDouble()).toInt()
        if (distance < SMOOTH_SCROLL_MAXIMUM_DISTANCE) {
            queueControlBinding!!.playQueue.smoothScrollToPosition(currentPlayingIndex)
        } else {
            queueControlBinding!!.playQueue.scrollToPosition(currentPlayingIndex)
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component On-Click Listener
    ////////////////////////////////////////////////////////////////////////////
    public override fun onClick(view: View) {
        if (player == null) {
            return
        }
        if (view.getId() == queueControlBinding!!.controlRepeat.getId()) {
            player!!.cycleNextRepeatMode()
        } else if (view.getId() == queueControlBinding!!.controlBackward.getId()) {
            player!!.playPrevious()
        } else if (view.getId() == queueControlBinding!!.controlFastRewind.getId()) {
            player!!.fastRewind()
        } else if (view.getId() == queueControlBinding!!.controlPlayPause.getId()) {
            player!!.playPause()
        } else if (view.getId() == queueControlBinding!!.controlFastForward.getId()) {
            player!!.fastForward()
        } else if (view.getId() == queueControlBinding!!.controlForward.getId()) {
            player!!.playNext()
        } else if (view.getId() == queueControlBinding!!.controlShuffle.getId()) {
            player!!.toggleShuffleModeEnabled()
        } else if (view.getId() == queueControlBinding!!.metadata.getId()) {
            scrollToSelected()
        } else if (view.getId() == queueControlBinding!!.liveSync.getId()) {
            player!!.seekToDefault()
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Playback Parameters
    ////////////////////////////////////////////////////////////////////////////
    private fun openPlaybackParameterDialog() {
        if (player == null) {
            return
        }
        PlaybackParameterDialog.Companion.newInstance(player!!.getPlaybackSpeed().toDouble(), player!!.getPlaybackPitch().toDouble(),
                player!!.getPlaybackSkipSilence(), this).show(getSupportFragmentManager(), TAG)
    }

    public override fun onPlaybackParameterChanged(playbackTempo: Float, playbackPitch: Float,
                                                   playbackSkipSilence: Boolean) {
        if (player != null) {
            player!!.setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence)
            onPlaybackParameterChanged(player!!.getPlaybackParameters())
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Seekbar Listener
    ////////////////////////////////////////////////////////////////////////////
    public override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                                          fromUser: Boolean) {
        if (fromUser) {
            val seekTime: String? = Localization.getDurationString((progress / 1000).toLong())
            queueControlBinding!!.currentTime.setText(seekTime)
            queueControlBinding!!.seekDisplay.setText(seekTime)
        }
    }

    public override fun onStartTrackingTouch(seekBar: SeekBar) {
        seeking = true
        queueControlBinding!!.seekDisplay.setVisibility(View.VISIBLE)
    }

    public override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (player != null) {
            player!!.seekTo(seekBar.getProgress().toLong())
        }
        queueControlBinding!!.seekDisplay.setVisibility(View.GONE)
        seeking = false
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Listener
    ////////////////////////////////////////////////////////////////////////////
    public override fun onQueueUpdate(queue: PlayQueue?) {
        if (queue == null) {
            queueControlBinding!!.playQueue.setAdapter(null)
        } else {
            val adapter: PlayQueueAdapter = PlayQueueAdapter(this, queue)
            adapter.setSelectedListener(getOnSelectedListener())
            queueControlBinding!!.playQueue.setAdapter(adapter)
        }
    }

    public override fun onPlaybackUpdate(state: Int, repeatMode: Int, shuffled: Boolean,
                                         parameters: PlaybackParameters?) {
        onStateChanged(state)
        onPlayModeChanged(repeatMode, shuffled)
        onPlaybackParameterChanged(parameters)
        onMaybeMuteChanged()
    }

    public override fun onProgressUpdate(currentProgress: Int, duration: Int,
                                         bufferPercent: Int) {
        // Set buffer progress
        queueControlBinding!!.seekBar.setSecondaryProgress(((queueControlBinding!!.seekBar.getMax()
                * (bufferPercent.toFloat() / 100))).toInt())

        // Set Duration
        queueControlBinding!!.seekBar.setMax(duration)
        queueControlBinding!!.endTime.setText(Localization.getDurationString((duration / 1000).toLong()))

        // Set current time if not seeking
        if (!seeking) {
            queueControlBinding!!.seekBar.setProgress(currentProgress)
            queueControlBinding!!.currentTime.setText(Localization.getDurationString((currentProgress / 1000).toLong()))
        }
        if (player != null) {
            queueControlBinding!!.liveSync.setClickable(!player!!.isLiveEdge())
        }

        // this will make sure progressCurrentTime has the same width as progressEndTime
        val currentTimeParams: ViewGroup.LayoutParams = queueControlBinding!!.currentTime.getLayoutParams()
        currentTimeParams.width = queueControlBinding!!.endTime.getWidth()
        queueControlBinding!!.currentTime.setLayoutParams(currentTimeParams)
    }

    public override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
        if (info != null) {
            queueControlBinding!!.songName.setText(info.getName())
            queueControlBinding!!.artistName.setText(info.getUploaderName())
            queueControlBinding!!.endTime.setVisibility(View.GONE)
            queueControlBinding!!.liveSync.setVisibility(View.GONE)
            when (info.getStreamType()) {
                StreamType.LIVE_STREAM, StreamType.AUDIO_LIVE_STREAM -> queueControlBinding!!.liveSync.setVisibility(View.VISIBLE)
                else -> queueControlBinding!!.endTime.setVisibility(View.VISIBLE)
            }
            scrollToSelected()
        }
    }

    public override fun onServiceStopped() {
        unbind()
        finish()
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Helper
    ////////////////////////////////////////////////////////////////////////////
    private fun onStateChanged(state: Int) {
        val playPauseButton: ImageButton = queueControlBinding!!.controlPlayPause
        when (state) {
            Player.Companion.STATE_PAUSED -> {
                playPauseButton.setImageResource(R.drawable.ic_play_arrow)
                playPauseButton.setContentDescription(getString(R.string.play))
            }

            Player.Companion.STATE_PLAYING -> {
                playPauseButton.setImageResource(R.drawable.ic_pause)
                playPauseButton.setContentDescription(getString(R.string.pause))
            }

            Player.Companion.STATE_COMPLETED -> {
                playPauseButton.setImageResource(R.drawable.ic_replay)
                playPauseButton.setContentDescription(getString(R.string.replay))
            }

            else -> {}
        }
        when (state) {
            Player.Companion.STATE_PAUSED, Player.Companion.STATE_PLAYING, Player.Companion.STATE_COMPLETED -> {
                queueControlBinding!!.controlPlayPause.setClickable(true)
                queueControlBinding!!.controlPlayPause.setVisibility(View.VISIBLE)
                queueControlBinding!!.controlProgressBar.setVisibility(View.GONE)
            }

            else -> {
                queueControlBinding!!.controlPlayPause.setClickable(false)
                queueControlBinding!!.controlPlayPause.setVisibility(View.INVISIBLE)
                queueControlBinding!!.controlProgressBar.setVisibility(View.VISIBLE)
            }
        }
    }

    private fun onPlayModeChanged(repeatMode: Int, shuffled: Boolean) {
        when (repeatMode) {
            com.google.android.exoplayer2.Player.REPEAT_MODE_OFF -> queueControlBinding!!.controlRepeat
                    .setImageResource(R.drawable.exo_controls_repeat_off)

            com.google.android.exoplayer2.Player.REPEAT_MODE_ONE -> queueControlBinding!!.controlRepeat
                    .setImageResource(R.drawable.exo_controls_repeat_one)

            com.google.android.exoplayer2.Player.REPEAT_MODE_ALL -> queueControlBinding!!.controlRepeat
                    .setImageResource(R.drawable.exo_controls_repeat_all)
        }
        val shuffleAlpha: Int = if (shuffled) 255 else 77
        queueControlBinding!!.controlShuffle.setImageAlpha(shuffleAlpha)
    }

    private fun onPlaybackParameterChanged(parameters: PlaybackParameters?) {
        if ((parameters != null) && (menu != null) && (player != null)) {
            val item: MenuItem = menu!!.findItem(R.id.action_playback_speed)
            item.setTitle(PlayerHelper.formatSpeed(parameters.speed.toDouble()))
        }
    }

    private fun onMaybeMuteChanged() {
        if (menu != null && player != null) {
            val item: MenuItem = menu!!.findItem(R.id.action_mute)

            //Change the mute-button item in ActionBar
            //1) Text change:
            item.setTitle(if (player!!.isMuted()) R.string.unmute else R.string.mute)

            //2) Icon change accordingly to current App Theme
            // using rootView.getContext() because getApplicationContext() didn't work
            item.setIcon(if (player!!.isMuted()) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        }
    }

    public override fun onAudioTrackUpdate() {
        buildAudioTrackMenu()
    }

    private fun buildAudioTrackMenu() {
        if (menu == null) {
            return
        }
        val audioTrackSelector: MenuItem = menu!!.findItem(R.id.action_audio_track)
        val availableStreams: List<AudioStream>? = Optional.ofNullable<Player>(player)
                .map<MediaItemTag?>(Function<Player, MediaItemTag?>({ obj: Player -> obj.getCurrentMetadata() }))
                .flatMap<MediaItemTag.AudioTrack?>(Function<MediaItemTag?, Optional<out MediaItemTag.AudioTrack?>>({ obj: MediaItemTag? -> obj.getMaybeAudioTrack() }))
                .map<List<AudioStream>?>(Function<MediaItemTag.AudioTrack?, List<AudioStream>?>({ getAudioStreams() }))
                .orElse(null)
        val selectedAudioStream: Optional<AudioStream?> = Optional.ofNullable(player)
                .flatMap(Function<Player, Optional<out AudioStream?>?>({ obj: Player -> obj.getSelectedAudioStream() }))
        if ((availableStreams == null) || (availableStreams.size < 2
                        ) || selectedAudioStream.isEmpty()) {
            audioTrackSelector.setVisible(false)
        } else {
            val audioTrackMenu: SubMenu? = audioTrackSelector.getSubMenu()
            audioTrackMenu!!.clear()
            for (i in availableStreams.indices) {
                val audioStream: AudioStream = availableStreams.get(i)
                audioTrackMenu.add(MENU_ID_AUDIO_TRACK, i, Menu.NONE,
                        Localization.audioTrackName(this, audioStream))
            }
            val s: AudioStream = selectedAudioStream.get()
            val trackName: String? = Localization.audioTrackName(this, s)
            audioTrackSelector.setTitle(
                    getString(R.string.play_queue_audio_track, trackName))
            val shortName: String = if (s.getAudioLocale() != null) s.getAudioLocale()!!.getLanguage() else (trackName)!!
            audioTrackSelector.setTitleCondensed(
                    shortName.substring(0, min(shortName.length.toDouble(), 2.0).toInt()))
            audioTrackSelector.setVisible(true)
        }
    }

    /**
     * Called when an item from the audio track selector is selected.
     *
     * @param itemId index of the selected item
     */
    private fun onAudioTrackClick(itemId: Int) {
        if (player!!.getCurrentMetadata() == null) {
            return
        }
        player!!.getCurrentMetadata().getMaybeAudioTrack().ifPresent(Consumer<MediaItemTag.AudioTrack?>({ audioTrack: MediaItemTag.AudioTrack? ->
            val availableStreams: List<AudioStream?> = audioTrack.getAudioStreams()
            val selectedStreamIndex: Int = audioTrack.getSelectedAudioStreamIndex()
            if (selectedStreamIndex == itemId || availableStreams.size <= itemId) {
                return@ifPresent
            }
            val newAudioTrack: String? = availableStreams.get(itemId)!!.getAudioTrackId()
            player!!.setAudioTrack(newAudioTrack)
        }))
    }

    companion object {
        private val TAG: String = PlayQueueActivity::class.java.getSimpleName()
        private val SMOOTH_SCROLL_MAXIMUM_DISTANCE: Int = 80
        private val MENU_ID_AUDIO_TRACK: Int = 71
    }
}
