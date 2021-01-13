/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * VideoPlayer.java is part of NewPipe
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.video.VideoListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.views.ExpandableSurfaceView;

import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Base for <b>video</b> players.
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class VideoPlayer extends BasePlayer
        implements VideoListener,
        SeekBar.OnSeekBarChangeListener,
        View.OnClickListener,
        Player.EventListener,
        PopupMenu.OnMenuItemClickListener,
        PopupMenu.OnDismissListener {
    public final String TAG;
    public static final boolean DEBUG = BasePlayer.DEBUG;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    public static final int DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final int DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds

    protected static final int RENDERER_UNAVAILABLE = -1;

    @NonNull
    private final VideoPlaybackResolver resolver;

    private List<VideoStream> availableStreams;
    private int selectedStreamIndex;

    protected boolean wasPlaying = false;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected PlayerBinding binding;

    protected SeekBar playbackSeekBar;
    protected TextView qualityTextView;
    protected TextView playbackSpeed;

    private ValueAnimator controlViewAnimator;
    private final Handler controlsVisibilityHandler = new Handler();

    boolean isSomePopupMenuVisible = false;

    private final int qualityPopupMenuGroupId = 69;
    private PopupMenu qualityPopupMenu;

    private final int playbackSpeedPopupMenuGroupId = 79;
    private PopupMenu playbackSpeedPopupMenu;

    private final int captionPopupMenuGroupId = 89;
    private PopupMenu captionPopupMenu;

    ///////////////////////////////////////////////////////////////////////////

    protected VideoPlayer(final String debugTag, final Context context) {
        super(context);
        this.TAG = debugTag;
        this.resolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
    }

    // workaround to match normalized captions like english to English or deutsch to Deutsch
    private static boolean containsCaseInsensitive(final List<String> list, final String toFind) {
        for (final String i : list) {
            if (i.equalsIgnoreCase(toFind)) {
                return true;
            }
        }
        return false;
    }

    public void setup(@NonNull final PlayerBinding playerBinding) {
        initViews(playerBinding);
        if (simpleExoPlayer == null) {
            initPlayer(true);
        }
        initListeners();
    }

    public void initViews(@NonNull final PlayerBinding playerBinding) {
        binding = playerBinding;
        playbackSeekBar = (SeekBar) binding.playbackSeekBar;
        qualityTextView = (TextView) binding.qualityTextView;
        playbackSpeed = (TextView) binding.playbackSpeed;

        final float captionScale = PlayerHelper.getCaptionScale(context);
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        setupSubtitleView(binding.subtitleView, captionScale, captionStyle);

        ((TextView) binding.resizeTextView).setText(PlayerHelper.resizeTypeOf(context,
                binding.surfaceView.getResizeMode()));

        playbackSeekBar.getThumb().setColorFilter(new PorterDuffColorFilter(Color.RED,
                PorterDuff.Mode.SRC_IN));
        playbackSeekBar.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(Color.RED,
                PorterDuff.Mode.MULTIPLY));

        qualityPopupMenu = new PopupMenu(context, qualityTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, playbackSpeed);
        captionPopupMenu = new PopupMenu(context, binding.captionTextView);

        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
    }

    protected abstract void setupSubtitleView(@NonNull SubtitleView view, float captionScale,
                                              @NonNull CaptionStyleCompat captionStyle);

    @Override
    public void initListeners() {
        playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.playbackSpeed.setOnClickListener(this);
        binding.qualityTextView.setOnClickListener(this);
        binding.captionTextView.setOnClickListener(this);
        binding.resizeTextView.setOnClickListener(this);
        binding.playbackLiveSync.setOnClickListener(this);
    }

    @Override
    public void initPlayer(final boolean playOnReady) {
        super.initPlayer(playOnReady);

        // Setup video view
        simpleExoPlayer.setVideoSurfaceView(binding.surfaceView);
        simpleExoPlayer.addVideoListener(this);

        // Setup subtitle view
        simpleExoPlayer.addTextOutput(binding.subtitleView);

        // Setup audio session with onboard equalizer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context)));
        }
    }

    @Override
    public void handleIntent(final Intent intent) {
        if (intent == null) {
            return;
        }

        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
        }

        super.handleIntent(intent);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // UI Builders
    //////////////////////////////////////////////////////////////////////////*/

    public void buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return;
        }

        qualityPopupMenu.getMenu().removeGroup(qualityPopupMenuGroupId);
        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(qualityPopupMenuGroupId, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.resolution);
        }
        if (getSelectedVideoStream() != null) {
            qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return;
        }

        playbackSpeedPopupMenu.getMenu().removeGroup(playbackSpeedPopupMenuGroupId);
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            playbackSpeedPopupMenu.getMenu().add(playbackSpeedPopupMenuGroupId, i, Menu.NONE,
                    formatSpeed(PLAYBACK_SPEEDS[i]));
        }
        playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private void buildCaptionMenu(final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(captionPopupMenuGroupId);

        final String userPreferredLanguage = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.caption_user_set_key), null);
        /*
         * only search for autogenerated cc as fallback
         * if "(auto-generated)" was not already selected
         * we are only looking for "(" instead of "(auto-generated)" to hopefully get all
         * internationalized variants such as "(automatisch-erzeugt)" and so on
         */
        boolean searchForAutogenerated = userPreferredLanguage != null
                && !userPreferredLanguage.contains("(");

        // Add option for turning off caption
        final MenuItem captionOffItem = captionPopupMenu.getMenu().add(captionPopupMenuGroupId,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true));
            }
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().remove(context.getString(R.string.caption_user_set_key)).commit();
            return true;
        });

        // Add all available captions
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            final MenuItem captionItem = captionPopupMenu.getMenu().add(captionPopupMenuGroupId,
                    i + 1, Menu.NONE, captionLanguage);
            captionItem.setOnMenuItemClickListener(menuItem -> {
                final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.setPreferredTextLanguage(captionLanguage);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false));
                    final SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(context);
                    prefs.edit().putString(context.getString(R.string.caption_user_set_key),
                            captionLanguage).commit();
                }
                return true;
            });
            // apply caption language from previous user preference
            if (userPreferredLanguage != null
                    && (captionLanguage.equals(userPreferredLanguage)
                    || (searchForAutogenerated && captionLanguage.startsWith(userPreferredLanguage))
                    || (userPreferredLanguage.contains("(") && captionLanguage.startsWith(
                    userPreferredLanguage.substring(0, userPreferredLanguage.indexOf('(')))))) {
                final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.setPreferredTextLanguage(captionLanguage);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false));
                }
                searchForAutogenerated = false;
            }
        }
        captionPopupMenu.setOnDismissListener(this);
    }

    private void updateStreamRelatedViews() {
        if (getCurrentMetadata() == null) {
            return;
        }

        final MediaSourceTag tag = getCurrentMetadata();
        final StreamInfo metadata = tag.getMetadata();

        binding.qualityTextView.setVisibility(View.GONE);
        binding.playbackSpeed.setVisibility(View.GONE);

        binding.playbackEndTime.setVisibility(View.GONE);
        binding.playbackLiveSync.setVisibility(View.GONE);

        switch (metadata.getStreamType()) {
            case AUDIO_STREAM:
                binding.surfaceView.setVisibility(View.GONE);
                binding.endScreen.setVisibility(View.VISIBLE);
                binding.playbackEndTime.setVisibility(View.VISIBLE);
                break;

            case AUDIO_LIVE_STREAM:
                binding.surfaceView.setVisibility(View.GONE);
                binding.endScreen.setVisibility(View.VISIBLE);
                binding.playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case LIVE_STREAM:
                binding.surfaceView.setVisibility(View.VISIBLE);
                binding.endScreen.setVisibility(View.GONE);
                binding.playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case VIDEO_STREAM:
                if (metadata.getVideoStreams().size() + metadata.getVideoOnlyStreams().size()
                        == 0) {
                    break;
                }

                availableStreams = tag.getSortedAvailableVideoStreams();
                selectedStreamIndex = tag.getSelectedVideoStreamIndex();
                buildQualityMenu();

                binding.qualityTextView.setVisibility(View.VISIBLE);
                binding.surfaceView.setVisibility(View.VISIBLE);
            default:
                binding.endScreen.setVisibility(View.GONE);
                binding.playbackEndTime.setVisibility(View.VISIBLE);
                break;
        }

        buildPlaybackSpeedMenu();
        binding.playbackSpeed.setVisibility(View.VISIBLE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    protected abstract VideoPlaybackResolver.QualityResolver getQualityResolver();

    @Override
    protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        super.onMetadataChanged(tag);
        updateStreamRelatedViews();
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        return resolver.resolve(info);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onBlocked() {
        super.onBlocked();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        animateView(binding.playbackControlRoot, false, DEFAULT_CONTROLS_DURATION);

        playbackSeekBar.setEnabled(false);
        playbackSeekBar.getThumb().setColorFilter(new PorterDuffColorFilter(Color.RED,
                PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animateView(binding.loadingPanel, true, 0);
        animateView(binding.surfaceForeground, true, 100);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();

        updateStreamRelatedViews();

        showAndAnimateControl(-1, true);

        playbackSeekBar.setEnabled(true);
        playbackSeekBar.getThumb().setColorFilter(new PorterDuffColorFilter(Color.RED,
                PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setVisibility(View.GONE);

        animateView(binding.currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                200);
    }

    @Override
    public void onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called");
        }
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }
        showControls(400);
        binding.loadingPanel.setVisibility(View.GONE);
    }

    @Override
    public void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }
        showAndAnimateControl(-1, true);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();

        showControls(500);
        animateView(binding.currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                200);
        binding.loadingPanel.setVisibility(View.GONE);

        animateView(binding.surfaceForeground, true, 100);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTracksChanged(@NonNull final TrackGroupArray trackGroups,
                                @NonNull final TrackSelectionArray trackSelections) {
        super.onTracksChanged(trackGroups, trackSelections);
        onTextTrackUpdate();
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onVideoSizeChanged(final int width, final int height,
                                   final int unappliedRotationDegrees,
                                   final float pixelWidthHeightRatio) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: "
                    + "width / height = [" + width + " / " + height
                    + " = " + (((float) width) / height) + "], "
                    + "unappliedRotationDegrees = [" + unappliedRotationDegrees + "], "
                    + "pixelWidthHeightRatio = [" + pixelWidthHeightRatio + "]");
        }
        binding.surfaceView.setAspectRatio(((float) width) / height);
    }

    @Override
    public void onRenderedFirstFrame() {
        animateView(binding.surfaceForeground, false, 100);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Track Updates
    //////////////////////////////////////////////////////////////////////////*/

    private void onTextTrackUpdate() {
        final int textRenderer = getRendererIndex(C.TRACK_TYPE_TEXT);

        if (binding == null) {
            return;
        }
        if (trackSelector.getCurrentMappedTrackInfo() == null
                || textRenderer == RENDERER_UNAVAILABLE) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        final TrackGroupArray textTracks = trackSelector.getCurrentMappedTrackInfo()
                .getTrackGroups(textRenderer);

        // Extract all loaded languages
        final List<String> availableLanguages = new ArrayList<>(textTracks.length);
        for (int i = 0; i < textTracks.length; i++) {
            final TrackGroup textTrack = textTracks.get(i);
            if (textTrack.length > 0 && textTrack.getFormat(0) != null) {
                availableLanguages.add(textTrack.getFormat(0).language);
            }
        }

        // Normalize mismatching language strings
        final String preferredLanguage = trackSelector.getPreferredTextLanguage();
        // Build UI
        buildCaptionMenu(availableLanguages);
        if (trackSelector.getParameters().getRendererDisabled(textRenderer)
                || preferredLanguage == null || (!availableLanguages.contains(preferredLanguage)
                && !containsCaseInsensitive(availableLanguages, preferredLanguage))) {
            binding.captionTextView.setText(R.string.caption_none);
        } else {
            binding.captionTextView.setText(preferredLanguage);
        }
        binding.captionTextView.setVisibility(availableLanguages.isEmpty() ? View.GONE
                : View.VISIBLE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPrepared(final boolean playWhenReady) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        }

        playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        binding.playbackEndTime.setText(getTimeString((int) simpleExoPlayer.getDuration()));
        playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));

        super.onPrepared(playWhenReady);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (binding != null) {
            binding.endScreen.setImageBitmap(null);
        }
    }

    @Override
    public void onUpdateProgress(final int currentProgress, final int duration,
                                 final int bufferPercent) {
        if (!isPrepared()) {
            return;
        }

        if (duration != playbackSeekBar.getMax()) {
            binding.playbackEndTime.setText(getTimeString(duration));
            playbackSeekBar.setMax(duration);
        }
        if (currentState != STATE_PAUSED) {
            if (currentState != STATE_PAUSED_SEEK) {
                playbackSeekBar.setProgress(currentProgress);
            }
            binding.playbackCurrentTime.setText(getTimeString(currentProgress));
        }
        if (simpleExoPlayer.isLoading() || bufferPercent > 90) {
            playbackSeekBar.setSecondaryProgress(
                    (int) (playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "updateProgress() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!isLiveEdge());
    }

    @Override
    public void onLoadingComplete(final String imageUri, final View view,
                                  final Bitmap loadedImage) {
        super.onLoadingComplete(imageUri, view, loadedImage);
        if (loadedImage != null) {
            binding.endScreen.setImageBitmap(loadedImage);
        }
    }

    protected void toggleFullscreen() {
        changeState(STATE_BLOCKED);
    }

    @Override
    public void onFastRewind() {
        super.onFastRewind();
        showAndAnimateControl(R.drawable.ic_fast_rewind_white_24dp, true);
    }

    @Override
    public void onFastForward() {
        super.onFastForward();
        showAndAnimateControl(R.drawable.ic_fast_forward_white_24dp, true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick related
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        if (DEBUG) {
            Log.d(TAG, "onClick() called with: v = [" + v + "]");
        }
        if (v.getId() == binding.qualityTextView.getId()) {
            onQualitySelectorClicked();
        } else if (v.getId() == binding.playbackSpeed.getId()) {
            onPlaybackSpeedClicked();
        } else if (v.getId() == binding.resizeTextView.getId()) {
            onResizeClicked();
        } else if (v.getId() == binding.captionTextView.getId()) {
            onCaptionClicked();
        } else if (v.getId() == binding.playbackLiveSync.getId()) {
            seekToDefault();
        }
    }

    /**
     * Called when an item of the quality selector or the playback speed selector is selected.
     */
    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        if (DEBUG) {
            Log.d(TAG, "onMenuItemClick() called with: "
                    + "menuItem = [" + menuItem + "], "
                    + "menuItem.getItemId = [" + menuItem.getItemId() + "]");
        }

        if (qualityPopupMenuGroupId == menuItem.getGroupId()) {
            final int menuItemIndex = menuItem.getItemId();
            if (selectedStreamIndex == menuItemIndex || availableStreams == null
                    || availableStreams.size() <= menuItemIndex) {
                return true;
            }

            final String newResolution = availableStreams.get(menuItemIndex).resolution;
            setRecovery();
            setPlaybackQuality(newResolution);
            reload();

            qualityTextView.setText(menuItem.getTitle());
            return true;
        } else if (playbackSpeedPopupMenuGroupId == menuItem.getGroupId()) {
            final int speedIndex = menuItem.getItemId();
            final float speed = PLAYBACK_SPEEDS[speedIndex];

            setPlaybackSpeed(speed);
            playbackSpeed.setText(formatSpeed(speed));
        }

        return false;
    }

    /**
     * Called when some popup menu is dismissed.
     */
    @Override
    public void onDismiss(final PopupMenu menu) {
        if (DEBUG) {
            Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]");
        }
        isSomePopupMenuVisible = false;
        if (getSelectedVideoStream() != null) {
            qualityTextView.setText(getSelectedVideoStream().resolution);
        }
    }

    public void onQualitySelectorClicked() {
        if (DEBUG) {
            Log.d(TAG, "onQualitySelectorClicked() called");
        }
        qualityPopupMenu.show();
        isSomePopupMenuVisible = true;

        final VideoStream videoStream = getSelectedVideoStream();
        if (videoStream != null) {
            final String qualityText = MediaFormat.getNameById(videoStream.getFormatId()) + " "
                    + videoStream.resolution;
            qualityTextView.setText(qualityText);
        }

        wasPlaying = simpleExoPlayer.getPlayWhenReady();
    }

    public void onPlaybackSpeedClicked() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackSpeedClicked() called");
        }
        playbackSpeedPopupMenu.show();
        isSomePopupMenuVisible = true;
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }
        captionPopupMenu.show();
        isSomePopupMenuVisible = true;
    }

    void onResizeClicked() {
        if (binding != null) {
            final int currentResizeMode = binding.surfaceView.getResizeMode();
            final int newResizeMode = nextResizeMode(currentResizeMode);
            setResizeMode(newResizeMode);
        }
    }

    protected void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        binding.surfaceView.setResizeMode(resizeMode);
        ((TextView) binding.resizeTextView).setText(PlayerHelper.resizeTypeOf(context,
                resizeMode));
    }

    protected abstract int nextResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode);

    /*//////////////////////////////////////////////////////////////////////////
    // SeekBar Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        if (DEBUG && fromUser) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }
        if (fromUser) {
            binding.currentDisplaySeek.setText(getTimeString(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (getCurrentState() != STATE_PAUSED_SEEK) {
            changeState(STATE_PAUSED_SEEK);
        }

        wasPlaying = simpleExoPlayer.getPlayWhenReady();
        if (isPlaying()) {
            simpleExoPlayer.setPlayWhenReady(false);
        }

        showControls(0);
        animateView(binding.currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, true,
                DEFAULT_CONTROLS_DURATION);
    }

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }

        seekTo(seekBar.getProgress());
        if (wasPlaying || simpleExoPlayer.getDuration() == seekBar.getProgress()) {
            simpleExoPlayer.setPlayWhenReady(true);
        }

        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animateView(binding.currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                200);

        if (getCurrentState() == STATE_PAUSED_SEEK) {
            changeState(STATE_BUFFERING);
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public int getRendererIndex(final int trackIndex) {
        if (simpleExoPlayer == null) {
            return RENDERER_UNAVAILABLE;
        }

        for (int t = 0; t < simpleExoPlayer.getRendererCount(); t++) {
            if (simpleExoPlayer.getRendererType(t) == trackIndex) {
                return t;
            }
        }

        return RENDERER_UNAVAILABLE;
    }

    public boolean isControlsVisible() {
        return binding != null
                && binding.playbackControlRoot.getVisibility() == View.VISIBLE;
    }

    /**
     * Show a animation, and depending on goneOnEnd, will stay on the screen or be gone.
     *
     * @param drawableId the drawable that will be used to animate,
     *                   pass -1 to clear any animation that is visible
     * @param goneOnEnd  will set the animation view to GONE on the end of the animation
     */
    public void showAndAnimateControl(final int drawableId, final boolean goneOnEnd) {
        if (DEBUG) {
            Log.d(TAG, "showAndAnimateControl() called with: "
                    + "drawableId = [" + drawableId + "], goneOnEnd = [" + goneOnEnd + "]");
        }
        if (controlViewAnimator != null && controlViewAnimator.isRunning()) {
            if (DEBUG) {
                Log.d(TAG, "showAndAnimateControl: controlViewAnimator.isRunning");
            }
            controlViewAnimator.end();
        }

        if (drawableId == -1) {
            if (binding.controlAnimationView.getVisibility() == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        binding.controlAnimationView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1.0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1.0f)
                ).setDuration(DEFAULT_CONTROLS_DURATION);
                controlViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        binding.controlAnimationView.setVisibility(View.GONE);
                    }
                });
                controlViewAnimator.start();
            }
            return;
        }

        final float scaleFrom = goneOnEnd ? 1f : 1f;
        final float scaleTo = goneOnEnd ? 1.8f : 1.4f;
        final float alphaFrom = goneOnEnd ? 1f : 0f;
        final float alphaTo = goneOnEnd ? 0f : 1f;


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.controlAnimationView,
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        );
        controlViewAnimator.setDuration(goneOnEnd ? 1000 : 500);
        controlViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                binding.controlAnimationView.setVisibility(goneOnEnd ? View.GONE
                        : View.VISIBLE);
            }
        });

        binding.controlAnimationView.setVisibility(View.VISIBLE);
        binding.controlAnimationView.setImageDrawable(AppCompatResources.getDrawable(context,
                drawableId));
        controlViewAnimator.start();
    }

    public boolean isSomePopupMenuVisible() {
        return isSomePopupMenuVisible;
    }

    public void showControlsThenHide() {
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }

        final int hideTime = binding.playbackControlRoot.isInTouchMode()
                ? DEFAULT_CONTROLS_HIDE_TIME
                : DPAD_CONTROLS_HIDE_TIME;

        showHideShadow(true, DEFAULT_CONTROLS_DURATION, 0);
        animateView(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION, 0,
                () -> hideControls(DEFAULT_CONTROLS_DURATION, hideTime));
    }

    public void showControls(final long duration) {
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration, 0);
        animateView(binding.playbackControlRoot, true, duration);
    }

    public void safeHideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "safeHideControls() called with: delay = [" + delay + "]");
        }
        if (binding.getRoot().isInTouchMode()) {
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
            controlsVisibilityHandler.postDelayed(
                    () -> animateView(binding.playbackControlRoot, false, duration),
                    delay);
        }
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            showHideShadow(false, duration, 0);
            animateView(binding.playbackControlRoot, false, duration);
        }, delay);
    }

    public void hideControlsAndButton(final long duration, final long delay, final View button) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler
                .postDelayed(hideControlsAndButtonHandler(duration, button), delay);
    }

    private Runnable hideControlsAndButtonHandler(final long duration, final View videoPlayPause) {
        return () -> {
            videoPlayPause.setVisibility(View.INVISIBLE);
            animateView(binding.playbackControlRoot, false, duration);
        };
    }

    void showHideShadow(final boolean show, final long duration, final long delay) {
        animateView(binding.playerTopShadow, show, duration, delay, null);
        animateView(binding.playerBottomShadow, show, duration, delay, null);
    }

    public abstract void hideSystemUIIfNeeded();

    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public String getPlaybackQuality() {
        return resolver.getPlaybackQuality();
    }

    public void setPlaybackQuality(final String quality) {
        this.resolver.setPlaybackQuality(quality);
    }

    public ExpandableSurfaceView getSurfaceView() {
        return binding.surfaceView;
    }

    public boolean wasPlaying() {
        return wasPlaying;
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        return (selectedStreamIndex >= 0 && availableStreams != null
                && availableStreams.size() > selectedStreamIndex)
                ? availableStreams.get(selectedStreamIndex) : null;
    }

    public Handler getControlsVisibilityHandler() {
        return controlsVisibilityHandler;
    }

    @NonNull
    public View getRootView() {
        return binding.getRoot();
    }

    @NonNull
    public View getLoadingPanel() {
        return binding.loadingPanel;
    }

    @NonNull
    public View getPlaybackControlRoot() {
        return binding.playbackControlRoot;
    }

    @NonNull
    public TextView getCurrentDisplaySeek() {
        return binding.currentDisplaySeek;
    }
}
