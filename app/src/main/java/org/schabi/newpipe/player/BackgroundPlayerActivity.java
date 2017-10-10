package org.schabi.newpipe.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;

public class BackgroundPlayerActivity extends AppCompatActivity
        implements BackgroundPlayer.PlayerEventListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private static final String TAG = "BGPlayerActivity";

    private boolean serviceBound;
    private ServiceConnection serviceConnection;

    private BackgroundPlayer.BasePlayerImpl player;

    private boolean seeking;

    ////////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////////

    private static final int RECYCLER_ITEM_POPUP_MENU_GROUP_ID = 47;
    private static final int PLAYBACK_SPEED_POPUP_MENU_GROUP_ID  = 61;
    private static final int PLAYBACK_PITCH_POPUP_MENU_GROUP_ID = 97;

    private View rootView;

    private RecyclerView itemsList;
    private ItemTouchHelper itemTouchHelper;

    private TextView metadataTitle;
    private TextView metadataArtist;

    private SeekBar progressSeekBar;
    private TextView progressCurrentTime;
    private TextView progressEndTime;

    private ImageButton repeatButton;
    private ImageButton backwardButton;
    private ImageButton playPauseButton;
    private ImageButton forwardButton;
    private ImageButton shuffleButton;

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
        if(serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
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
                serviceBound = false;
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
                    serviceBound = true;
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
        itemsList.setLongClickable(true);

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        player.playQueueAdapter.setSelectedListener(getOnSelectedListener());
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
        shuffleButton = rootView.findViewById(R.id.control_shuffle);

        repeatButton.setOnClickListener(this);
        backwardButton.setOnClickListener(this);
        playPauseButton.setOnClickListener(this);
        forwardButton.setOnClickListener(this);
        shuffleButton.setOnClickListener(this);
    }

    private void buildItemPopupMenu(final PlayQueueItem item, final View view) {
        final PopupMenu menu = new PopupMenu(this, view);
        final MenuItem remove = menu.getMenu().add(RECYCLER_ITEM_POPUP_MENU_GROUP_ID, 0, Menu.NONE, "Remove");
        remove.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final int index = player.playQueue.indexOf(item);
                if (index != -1) player.playQueue.remove(index);
                return true;
            }
        });

        final MenuItem detail = menu.getMenu().add(RECYCLER_ITEM_POPUP_MENU_GROUP_ID, 1, Menu.NONE, "Detail");
        detail.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                onOpenDetail(BackgroundPlayerActivity.this, item.getUrl(), item.getTitle());
                return true;
            }
        });

        menu.show();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Component Helpers
    ////////////////////////////////////////////////////////////////////////////

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()) {
                    return false;
                }

                final int sourceIndex = source.getLayoutPosition();
                final int targetIndex = target.getLayoutPosition();
                player.playQueue.move(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(PlayQueueItem item, View view) {
                final int index = player.playQueue.indexOf(item);
                if (index == -1) return;

                if (player.playQueue.getIndex() == index) {
                    player.onRestart();
                } else {
                    player.playQueue.setIndex(index);
                }
            }

            @Override
            public void held(PlayQueueItem item, View view) {
                final int index = player.playQueue.indexOf(item);
                if (index != -1) buildItemPopupMenu(item, view);
            }

            @Override
            public void onStartDrag(PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
            }
        };
    }

    private void onOpenDetail(Context context, String videoUrl, String videoTitle) {
        Intent i = new Intent(context, MainActivity.class);
        i.putExtra(Constants.KEY_SERVICE_ID, 0);
        i.putExtra(Constants.KEY_URL, videoUrl);
        i.putExtra(Constants.KEY_TITLE, videoTitle);
        i.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    private void scrollToSelected() {
        itemsList.smoothScrollToPosition(player.playQueue.getIndex());
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
            scrollToSelected();
        } else if (view.getId() == playPauseButton.getId()) {
            player.onVideoPlayPause();
            scrollToSelected();
        } else if (view.getId() == forwardButton.getId()) {
            player.onPlayNext();
        } else if (view.getId() == shuffleButton.getId()) {
            player.onShuffleClicked();
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
        seeking = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        player.simpleExoPlayer.seekTo(seekBar.getProgress());
        seeking = false;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Binding Service Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackUpdate(int state, int repeatMode, boolean shuffled, PlaybackParameters parameters) {
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

        int repeatAlpha = 255;
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                repeatAlpha = 77;
                break;
            case Player.REPEAT_MODE_ONE:
                // todo change image
                repeatAlpha = 168;
                break;
            case Player.REPEAT_MODE_ALL:
                repeatAlpha = 255;
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            repeatButton.setImageAlpha(repeatAlpha);
        } else {
            repeatButton.setAlpha(repeatAlpha);
        }

        int shuffleAlpha = 255;
        if (!shuffled) {
            shuffleAlpha = 77;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            shuffleButton.setImageAlpha(shuffleAlpha);
        } else {
            shuffleButton.setAlpha(shuffleAlpha);
        }

        if (parameters != null) {
            final float speed = parameters.speed;
            final float pitch = parameters.pitch;
        }

        scrollToSelected();
    }

    @Override
    public void onProgressUpdate(int currentProgress, int duration, int bufferPercent) {
        // Set buffer progress
        progressSeekBar.setSecondaryProgress((int) (progressSeekBar.getMax() * ((float) bufferPercent / 100)));

        // Set Duration
        progressSeekBar.setMax(duration);
        progressEndTime.setText(Localization.getDurationString(duration / 1000));

        // Set current time if not seeking
        if (!seeking) {
            progressSeekBar.setProgress(currentProgress);
            progressCurrentTime.setText(Localization.getDurationString(currentProgress / 1000));
        }
    }

    @Override
    public void onMetadataUpdate(StreamInfo info) {
        if (info != null) {
            metadataTitle.setText(info.name);
            metadataArtist.setText(info.uploader_name);
            scrollToSelected();
        }
    }

    @Override
    public void onServiceStopped() {
        finish();
    }
}
