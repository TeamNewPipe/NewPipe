package org.schabi.newpipe.player;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;

public class BackgroundPlayerActivity extends AppCompatActivity
        implements BackgroundPlayer.PlayerEventListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private static final String TAG = "BGPlayerActivity";

    private boolean isServiceBound;
    private ServiceConnection serviceConnection;

    private BackgroundPlayer.BasePlayerImpl player;

    private boolean isSeeking;

    ////////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////////

    private View rootView;

    private RecyclerView itemsList;

    private TextView metadataTitle;
    private TextView metadataArtist;

    private SeekBar progressSeekBar;
    private TextView progressCurrentTime;
    private TextView progressEndTime;

    private ImageButton repeatButton;
    private ImageButton backwardButton;
    private ImageButton playPauseButton;
    private ImageButton forwardButton;

    ////////////////////////////////////////////////////////////////////////////
    // Activity Lifecycle
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);
        setContentView(R.layout.activity_background_player);
        rootView = findViewById(R.id.main_content);

        final Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_background_player);

        serviceConnection = backgroundPlayerConnection();
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Intent mIntent = new Intent(this, BackgroundPlayer.class);
        final boolean success = bindService(mIntent, serviceConnection, BIND_AUTO_CREATE);
        if (!success) unbindService(serviceConnection);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Service Connection
    ////////////////////////////////////////////////////////////////////////////

    private ServiceConnection backgroundPlayerConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "Background player service is disconnected");
                isServiceBound = false;
                player = null;
                finish();
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Background player service is connected");
                final BackgroundPlayer.LocalBinder mLocalBinder = (BackgroundPlayer.LocalBinder) service;
                player = mLocalBinder.getBackgroundPlayerInstance();
                if (player == null) {
                    finish();
                } else {
                    isServiceBound = true;
                    buildComponents();

                    player.setActivityListener(BackgroundPlayerActivity.this);
                }
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Building
    ////////////////////////////////////////////////////////////////////////////

    private void buildComponents() {
        buildQueue();
        buildMetadata();
        buildSeekBar();
        buildControls();
    }

    private void buildQueue() {
        itemsList = findViewById(R.id.play_queue);
        itemsList.setLayoutManager(new LinearLayoutManager(this));
        itemsList.setAdapter(player.playQueueAdapter);
        itemsList.setClickable(true);

        player.playQueueAdapter.setSelectedListener(new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(PlayQueueItem item) {
                final int index = player.playQueue.indexOf(item);
                if (index != -1) player.playQueue.setIndex(index);
            }
        });
    }

    private void buildMetadata() {
        metadataTitle = rootView.findViewById(R.id.song_name);
        metadataArtist = rootView.findViewById(R.id.artist_name);
    }

    private void buildSeekBar() {
        progressCurrentTime = rootView.findViewById(R.id.current_time);
        progressSeekBar = rootView.findViewById(R.id.seek_bar);
        progressEndTime = rootView.findViewById(R.id.end_time);

        progressSeekBar.setOnSeekBarChangeListener(this);
    }

    private void buildControls() {
        repeatButton = rootView.findViewById(R.id.control_repeat);
        backwardButton = rootView.findViewById(R.id.control_backward);
        playPauseButton = rootView.findViewById(R.id.control_play_pause);
        forwardButton = rootView.findViewById(R.id.control_forward);

        repeatButton.setOnClickListener(this);
        backwardButton.setOnClickListener(this);
        playPauseButton.setOnClickListener(this);
        forwardButton.setOnClickListener(this);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component On-Click Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onClick(View view) {
        if (view.getId() == repeatButton.getId()) {
            player.onRepeatClicked();
        } else if (view.getId() == backwardButton.getId()) {
            player.onPlayPrevious();
        } else if (view.getId() == playPauseButton.getId()) {
            player.onVideoPlayPause();
        } else if (view.getId() == forwardButton.getId()) {
            player.onPlayNext();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Seekbar Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) progressCurrentTime.setText(Localization.getDurationString(progress / 1000));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        isSeeking = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        player.simpleExoPlayer.seekTo(seekBar.getProgress());
        isSeeking = false;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackUpdate(int state, int repeatMode, PlaybackParameters parameters) {
        switch (state) {
            case BasePlayer.STATE_PAUSED:
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                break;
            case BasePlayer.STATE_PLAYING:
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
                break;
            case BasePlayer.STATE_COMPLETED:
                playPauseButton.setImageResource(R.drawable.ic_replay_white);
                break;
            default:
                break;
        }

        int alpha = 255;
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                alpha = 77;
                break;
            case Player.REPEAT_MODE_ONE:
                // todo change image
                alpha = 168;
                break;
            case Player.REPEAT_MODE_ALL:
                alpha = 255;
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            repeatButton.setImageAlpha(alpha);
        } else {
            repeatButton.setAlpha(alpha);
        }

        if (parameters != null) {
            final float speed = parameters.speed;
            final float pitch = parameters.pitch;
        }
    }

    @Override
    public void onProgressUpdate(int currentProgress, int duration, int bufferPercent) {
        // Set buffer progress
        progressSeekBar.setSecondaryProgress((int) (progressSeekBar.getMax() * ((float) bufferPercent / 100)));

        // Set Duration
        progressSeekBar.setMax(duration);
        progressEndTime.setText(Localization.getDurationString(duration / 1000));

        // Set current time if not seeking
        if (!isSeeking) {
            progressSeekBar.setProgress(currentProgress);
            progressCurrentTime.setText(Localization.getDurationString(currentProgress / 1000));
        }
    }

    @Override
    public void onMetadataUpdate(StreamInfo info) {
        if (info != null) {
            metadataTitle.setText(info.name);
            metadataArtist.setText(info.uploader_name);
        }
    }

    @Override
    public void onServiceStopped() {
        finish();
    }
}
