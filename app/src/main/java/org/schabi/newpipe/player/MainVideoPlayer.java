/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * MainVideoPlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Activity Player implementing VideoPlayer
 *
 * @author mauriciocolli
 */
public final class MainVideoPlayer extends Activity {
    private static final String TAG = ".MainVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final String PLAYER_STATE_INTENT = "player_state_intent";

    private GestureDetector gestureDetector;

    private boolean activityPaused;
    private VideoPlayerImpl playerImpl;

    private SharedPreferences defaultPreferences;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        ThemeHelper.setTheme(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getWindow().setStatusBarColor(Color.BLACK);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final Intent intent;
        if (savedInstanceState != null && savedInstanceState.getParcelable(PLAYER_STATE_INTENT) != null) {
            intent = savedInstanceState.getParcelable(PLAYER_STATE_INTENT);
        } else {
            intent = getIntent();
        }

        if (intent == null) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showSystemUi();
        setContentView(R.layout.activity_main_player);
        playerImpl = new VideoPlayerImpl(this);
        playerImpl.setup(findViewById(android.R.id.content));
        playerImpl.handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.playerImpl == null) return;

        final Intent intent = NavigationHelper.getPlayerIntent(
                getApplicationContext(),
                this.getClass(),
                this.playerImpl.getPlayQueue(),
                this.playerImpl.getRepeatMode(),
                this.playerImpl.getPlaybackSpeed(),
                this.playerImpl.getPlaybackPitch(),
                this.playerImpl.getPlaybackQuality()
        );
        outState.putParcelable(PLAYER_STATE_INTENT, intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        super.onNewIntent(intent);
        playerImpl.handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");
        super.onBackPressed();
        if (playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (DEBUG) Log.d(TAG, "onStop() called");
        activityPaused = true;

        if (playerImpl.getPlayer() != null) {
            playerImpl.wasPlaying = playerImpl.getPlayer().getPlayWhenReady();
            playerImpl.setRecovery();
            playerImpl.destroyPlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume() called");
        if (activityPaused) {
            playerImpl.initPlayer();
            playerImpl.getPlayPauseButton().setImageResource(R.drawable.ic_play_arrow_white);

            playerImpl.getPlayer().setPlayWhenReady(playerImpl.wasPlaying);
            playerImpl.initPlayback(playerImpl.playQueue);

            activityPaused = false;
        }
        if(globalScreenOrientationLocked()) {
            boolean lastOrientationWasLandscape
                    = defaultPreferences.getBoolean(getString(R.string.last_orientation_landscape_key), false);
            setLandScape(lastOrientationWasLandscape);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        if (playerImpl != null) playerImpl.destroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (playerImpl.isSomePopupMenuVisible()) {
            playerImpl.moreOptionsPopupMenu.dismiss();
            playerImpl.getQualityPopupMenu().dismiss();
            playerImpl.getPlaybackSpeedPopupMenu().dismiss();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) Log.d(TAG, "showSystemUi() called");
        if (playerImpl != null && playerImpl.queueVisible) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        } else getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void hideSystemUi() {
        if (DEBUG) Log.d(TAG, "hideSystemUi() called");
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void toggleOrientation() {
        setLandScape(!isLandScape());
        defaultPreferences.edit()
                .putBoolean(getString(R.string.last_orientation_landscape_key), !isLandScape())
                .apply();
    }

    private boolean isLandScape() {
        return getResources().getDisplayMetrics().heightPixels < getResources().getDisplayMetrics().widthPixels;
    }

    private void setLandScape(boolean v) {
        setRequestedOrientation(v
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private boolean globalScreenOrientationLocked() {
        // 1: Screen orientation changes using acelerometer
        // 0: Screen orientatino is locked
        return !(android.provider.Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
    }

    protected void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    protected void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
        final int shuffleAlpha = shuffled ? 255 : 77;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            shuffleButton.setImageAlpha(shuffleAlpha);
        } else {
            shuffleButton.setAlpha(shuffleAlpha);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "WeakerAccess"})
    private class VideoPlayerImpl extends VideoPlayer {
        private TextView titleTextView;
        private TextView channelTextView;
        private TextView volumeTextView;
        private TextView brightnessTextView;
        private ImageButton queueButton;
        private ImageButton repeatButton;
        private ImageButton shuffleButton;

        private ImageButton playPauseButton;
        private ImageButton playPreviousButton;
        private ImageButton playNextButton;

        private RelativeLayout queueLayout;
        private ImageButton itemsListCloseButton;
        private RecyclerView itemsList;
        private ItemTouchHelper itemTouchHelper;

        private boolean queueVisible;

        private ImageButton moreOptionsButton;
        public int moreOptionsPopupMenuGroupId = 89;
        public PopupMenu moreOptionsPopupMenu;

        VideoPlayerImpl(final Context context) {
            super("VideoPlayerImpl" + MainVideoPlayer.TAG, context);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            this.titleTextView = rootView.findViewById(R.id.titleTextView);
            this.channelTextView = rootView.findViewById(R.id.channelTextView);
            this.volumeTextView = rootView.findViewById(R.id.volumeTextView);
            this.brightnessTextView = rootView.findViewById(R.id.brightnessTextView);
            this.queueButton = rootView.findViewById(R.id.queueButton);
            this.repeatButton = rootView.findViewById(R.id.repeatButton);
            this.shuffleButton = rootView.findViewById(R.id.shuffleButton);

            this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
            this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
            this.playNextButton = rootView.findViewById(R.id.playNextButton);
            this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
            this.moreOptionsPopupMenu = new PopupMenu(context, moreOptionsButton);
            this.moreOptionsPopupMenu.getMenuInflater().inflate(R.menu.menu_videooptions, moreOptionsPopupMenu.getMenu());

            titleTextView.setSelected(true);
            channelTextView.setSelected(true);

            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void initListeners() {
            super.initListeners();

            MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(false);
            getRootView().setOnTouchListener(listener);

            queueButton.setOnClickListener(this);
            repeatButton.setOnClickListener(this);
            shuffleButton.setOnClickListener(this);

            playPauseButton.setOnClickListener(this);
            playPreviousButton.setOnClickListener(this);
            playNextButton.setOnClickListener(this);
            moreOptionsButton.setOnClickListener(this);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onRepeatModeChanged(int i) {
            super.onRepeatModeChanged(i);
            updatePlaybackButtons();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void shutdown() {
            super.shutdown();
            finish();
        }

        @Override
        public void sync(@NonNull final PlayQueueItem item, @Nullable final StreamInfo info) {
            super.sync(item, info);
            titleTextView.setText(getVideoTitle());
            channelTextView.setText(getUploaderName());

            playPauseButton.setImageResource(R.drawable.ic_pause_white);
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlaybackButtons();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked();

            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            if (simpleExoPlayer == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !PermissionHelper.checkSystemAlertWindowPermission(MainVideoPlayer.this)) {
                Toast.makeText(MainVideoPlayer.this, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
                return;
            }

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    PopupVideoPlayer.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackQuality()
            );
            context.startService(intent);

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            destroy();
            finish();
        }

        public void onPlayBackgroundButtonClicked() {
            if (DEBUG) Log.d(TAG, "onPlayBackgroundButtonClicked() called");
            if (playerImpl.getPlayer() == null) return;

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    BackgroundPlayer.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackQuality()
            );
            context.startService(intent);

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            destroy();
            finish();
        }


        @Override
        public void onClick(View v) {
            super.onClick(v);
            if (v.getId() == playPauseButton.getId()) {
                onVideoPlayPause();

            } else if (v.getId() == playPreviousButton.getId()) {
                onPlayPrevious();

            } else if (v.getId() == playNextButton.getId()) {
                onPlayNext();

            } else if (v.getId() == queueButton.getId()) {
                onQueueClicked();
                return;
            } else if (v.getId() == repeatButton.getId()) {
                onRepeatClicked();
                return;
            } else if (v.getId() == shuffleButton.getId()) {
                onShuffleClicked();
                return;
            } else if (v.getId() == moreOptionsButton.getId()) {
                onMoreOptionsClicked();
            }

            if (getCurrentState() != STATE_COMPLETED) {
                getControlsVisibilityHandler().removeCallbacksAndMessages(null);
                animateView(getControlsRoot(), true, 300, 0, () -> {
                    if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                        hideControls(300, DEFAULT_CONTROLS_HIDE_TIME);
                    }
                });
            }
        }

        private void onQueueClicked() {
            queueVisible = true;
            hideSystemUi();

            buildQueue();
            updatePlaybackButtons();

            getControlsRoot().setVisibility(View.INVISIBLE);
            queueLayout.setVisibility(View.VISIBLE);

            itemsList.scrollToPosition(playQueue.getIndex());
        }

        private void onQueueClosed() {
            queueLayout.setVisibility(View.GONE);
            queueVisible = false;
        }

        private void onMoreOptionsClicked() {
            if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");
            buildMoreOptionsMenu();

            try {
                Field[] fields = moreOptionsPopupMenu.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(moreOptionsPopupMenu);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            moreOptionsPopupMenu.show();
            isSomePopupMenuVisible = true;
            showControls(300);
        }

        private void onScreenRotationClicked() {
            if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
            toggleOrientation();
            showControlsThenHide();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) {
                hideControls(100, 0);
            }
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) hideControls(300, 0);
        }

        @Override
        protected int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
            return ListHelper.getDefaultResolutionIndex(context, sortedVideos);
        }

        @Override
        protected int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                 final String playbackQuality) {
            return ListHelper.getDefaultResolutionIndex(context, sortedVideos, playbackQuality);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        private void animatePlayButtons(final boolean show, final int duration) {
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            animateView(playNextButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        }

        @Override
        public void onBlocked() {
            super.onBlocked();
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
                animatePlayButtons(true, 200);
            });
            showSystemUi();
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                animatePlayButtons(true, 200);
            });

            showSystemUi();
            getRootView().setKeepScreenOn(false);
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
        }


        @Override
        public void onCompleted() {
            showSystemUi();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_replay_white);
                animatePlayButtons(true, 300);
            });

            getRootView().setKeepScreenOn(false);
            super.onCompleted();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void showControlsThenHide() {
            if (queueVisible) return;

            super.showControlsThenHide();
        }

        @Override
        public void showControls(long duration) {
            if (queueVisible) return;

            super.showControls(duration);
        }

        @Override
        public void hideControls(final long duration, long delay) {
            if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            getControlsVisibilityHandler().postDelayed(() ->
                    animateView(getControlsRoot(), false, duration, 0, MainVideoPlayer.this::hideSystemUi),
                    delay
            );
        }

        private void updatePlaybackButtons() {
            if (repeatButton == null || shuffleButton == null ||
                    simpleExoPlayer == null || playQueue == null) return;

            setRepeatModeButton(repeatButton, getRepeatMode());
            setShuffleButton(shuffleButton, playQueue.isShuffled());
        }

        private void buildMoreOptionsMenu() {
            if (moreOptionsPopupMenu == null) return;
            moreOptionsPopupMenu.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.toggleOrientation:
                        onScreenRotationClicked();
                        break;
                    case R.id.switchPopup:
                        onFullScreenButtonClicked();
                        break;
                    case R.id.switchBackground:
                        onPlayBackgroundButtonClicked();
                        break;
                }
                return false;
            });
        }

        private void buildQueue() {
            queueLayout = findViewById(R.id.playQueuePanel);

            itemsListCloseButton = findViewById(R.id.playQueueClose);

            itemsList = findViewById(R.id.playQueue);
            itemsList.setAdapter(playQueueAdapter);
            itemsList.setClickable(true);
            itemsList.setLongClickable(true);

            itemsList.clearOnScrollListeners();
            itemsList.addOnScrollListener(getQueueScrollListener());

            itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
            itemTouchHelper.attachToRecyclerView(itemsList);

            playQueueAdapter.setSelectedListener(getOnSelectedListener());

            itemsListCloseButton.setOnClickListener(view -> onQueueClosed());
        }

        private OnScrollBelowItemsListener getQueueScrollListener() {
            return new OnScrollBelowItemsListener() {
                @Override
                public void onScrolledDown(RecyclerView recyclerView) {
                    if (playQueue != null && !playQueue.isComplete()) {
                        playQueue.fetch();
                    } else if (itemsList != null) {
                        itemsList.clearOnScrollListeners();
                    }
                }
            };
        }

        private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
            return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                    if (source.getItemViewType() != target.getItemViewType()) {
                        return false;
                    }

                    final int sourceIndex = source.getLayoutPosition();
                    final int targetIndex = target.getLayoutPosition();
                    playQueue.move(sourceIndex, targetIndex);
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
                    onSelected(item);
                }

                @Override
                public void held(PlayQueueItem item, View view) {
                    final int index = playQueue.indexOf(item);
                    if (index != -1) playQueue.remove(index);
                }

                @Override
                public void onStartDrag(PlayQueueItemHolder viewHolder) {
                    if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
                }
            };
        }

        ///////////////////////////////////////////////////////////////////////////
        // Getters
        ///////////////////////////////////////////////////////////////////////////

        public TextView getTitleTextView() {
            return titleTextView;
        }

        public TextView getChannelTextView() {
            return channelTextView;
        }

        public TextView getVolumeTextView() {
            return volumeTextView;
        }

        public TextView getBrightnessTextView() {
            return brightnessTextView;
        }

        public ImageButton getRepeatButton() {
            return repeatButton;
        }

        public ImageButton getPlayPauseButton() {
            return playPauseButton;
        }
    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (!playerImpl.isPlaying()) return false;

            if (e.getX() > playerImpl.getRootView().getWidth() / 2) {
                playerImpl.onFastForward();
            } else {
                playerImpl.onFastRewind();
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED) return true;

            if (playerImpl.isControlsVisible()) playerImpl.hideControls(150, 0);
            else {
                playerImpl.showControlsThenHide();
                showSystemUi();
            }
            return true;
        }

        private final boolean isPlayerGestureEnabled = PlayerHelper.isPlayerGestureEnabled(getApplicationContext());

        private final float stepsBrightness = 15, stepBrightness = (1f / stepsBrightness), minBrightness = .01f;
        private float currentBrightness = .5f;

        private int currentVolume, maxVolume = playerImpl.getAudioReactor().getMaxVolume();
        private final float stepsVolume = 15, stepVolume = (float) Math.ceil(maxVolume / stepsVolume), minVolume = 0;

        private final String brightnessUnicode = new String(Character.toChars(0x2600));
        private final String volumeUnicode = new String(Character.toChars(0x1F508));

        private final int MOVEMENT_THRESHOLD = 40;
        private final int eventsThreshold = 8;
        private boolean triggered = false;
        private int eventsNum;

        // TODO: Improve video gesture controls
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isPlayerGestureEnabled) return false;

            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "MainVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                    ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]");
            float abs = Math.abs(e2.getY() - e1.getY());
            if (!triggered) {
                triggered = abs > MOVEMENT_THRESHOLD;
                return false;
            }

            if (eventsNum++ % eventsThreshold != 0 || playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) return false;
            isMoving = true;
//            boolean up = !((e2.getY() - e1.getY()) > 0) && distanceY > 0; // Android's origin point is on top
            boolean up = distanceY > 0;


            if (e1.getX() > playerImpl.getRootView().getWidth() / 2) {
                double floor = Math.floor(up ? stepVolume : -stepVolume);
                currentVolume = (int) (playerImpl.getAudioReactor().getVolume() + floor);
                if (currentVolume >= maxVolume) currentVolume = maxVolume;
                if (currentVolume <= minVolume) currentVolume = (int) minVolume;
                playerImpl.getAudioReactor().setVolume(currentVolume);

                currentVolume = playerImpl.getAudioReactor().getVolume();
                if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
                final String volumeText = volumeUnicode + " " + Math.round((((float) currentVolume) / maxVolume) * 100) + "%";
                playerImpl.getVolumeTextView().setText(volumeText);

                if (playerImpl.getVolumeTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getVolumeTextView(), true, 200);
                if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.getBrightnessTextView().setVisibility(View.GONE);
            } else {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                currentBrightness += up ? stepBrightness : -stepBrightness;
                if (currentBrightness >= 1f) currentBrightness = 1f;
                if (currentBrightness <= minBrightness) currentBrightness = minBrightness;

                lp.screenBrightness = currentBrightness;
                getWindow().setAttributes(lp);
                if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = " + currentBrightness);
                int brightnessNormalized = Math.round(currentBrightness * 100);

                final String brightnessText = brightnessUnicode + " " + (brightnessNormalized == 1 ? 0 : brightnessNormalized) + "%";
                playerImpl.getBrightnessTextView().setText(brightnessText);

                if (playerImpl.getBrightnessTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getBrightnessTextView(), true, 200);
                if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.getVolumeTextView().setVisibility(View.GONE);
            }
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");
            triggered = false;
            eventsNum = 0;
            /* if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.getVolumeTextView().setVisibility(View.GONE);
            if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.getBrightnessTextView().setVisibility(View.GONE);*/
            if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) animateView(playerImpl.getVolumeTextView(), false, 200, 200);
            if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) animateView(playerImpl.getBrightnessTextView(), false, 200, 200);

            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == BasePlayer.STATE_PLAYING) {
                playerImpl.hideControls(300, VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isMoving) {
                isMoving = false;
                onScrollEnd();
            }
            return true;
        }

    }
}
