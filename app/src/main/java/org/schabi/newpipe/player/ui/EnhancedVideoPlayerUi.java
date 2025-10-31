package org.schabi.newpipe.player.ui;

import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener;

/**
 * Enhanced Video Player UI with modern animations and improved user experience
 * This extends the original VideoPlayerUi while adding modern enhancements
 */
public final class EnhancedVideoPlayerUi extends VideoPlayerUi {
    private static final String TAG = EnhancedVideoPlayerUi.class.getSimpleName();
    
    // Animation constants
    private static final long MODERN_ANIMATION_DURATION = 250;
    private static final long BUTTON_SCALE_DURATION = 120;
    private static final float BUTTON_PRESS_SCALE = 0.92f;

    public EnhancedVideoPlayerUi(@NonNull final Player player,
                                @NonNull final PlayerBinding playerBinding) {
        super(player, playerBinding);
        setupModernEnhancements();
    }

    private void setupModernEnhancements() {
        // Apply modern styling to existing elements
        applyModernStyling();
        
        // Setup enhanced animations
        setupEnhancedAnimations();
        
        // Apply modern colors
        applyModernColors();
    }

    private void applyModernStyling() {
        // Apply modern accent color to seekbar
        final int accentColor = ContextCompat.getColor(context, R.color.modern_accent);
        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        binding.playbackSeekBar.getProgressDrawable()
                .setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.MULTIPLY));
        
        // Enhanced loading panel
        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.MULTIPLY));
    }

    private void setupEnhancedAnimations() {
        // Add modern button press animations
        setupButtonScaleAnimations();
        
        // Setup smooth control transitions
        setupSmoothTransitions();
    }

    private void setupButtonScaleAnimations() {
        // Main play controls with bouncy animation
        addBounceAnimation(binding.playPauseButton);
        addScaleAnimation(binding.playPreviousButton);
        addScaleAnimation(binding.playNextButton);
        
        // Control buttons with subtle scale
        addScaleAnimation(binding.moreOptionsButton);
        addScaleAnimation(binding.playerCloseButton);
        addScaleAnimation(binding.fullScreenButton);
        addScaleAnimation(binding.screenRotationButton);
        
        // Secondary controls
        addScaleAnimation(binding.share);
        addScaleAnimation(binding.playWithKodi);
        addScaleAnimation(binding.openInBrowser);
        addScaleAnimation(binding.switchMute);
    }

    private void addBounceAnimation(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(BUTTON_PRESS_SCALE)
                            .scaleY(BUTTON_PRESS_SCALE)
                            .setDuration(BUTTON_SCALE_DURATION)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(BUTTON_SCALE_DURATION)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                    break;
            }
            return false;
        });
    }

    private void addScaleAnimation(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(BUTTON_PRESS_SCALE)
                            .scaleY(BUTTON_PRESS_SCALE)
                            .setDuration(BUTTON_SCALE_DURATION)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(BUTTON_SCALE_DURATION)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
            }
            return false;
        });
    }

    private void setupSmoothTransitions() {
        // Smoother control show/hide animations
        binding.playbackControlRoot.setAlpha(0f);
    }

    private void applyModernColors() {
        // Apply modern background colors where appropriate
        final int surfaceColor = ContextCompat.getColor(context, R.color.modern_surface);
        final int surfaceVariantColor = ContextCompat.getColor(context, R.color.modern_surface_variant);
        
        // These will be applied when the queue panel is shown
        // binding.itemsListPanel.setBackgroundColor(surfaceColor);
    }

    @Override
    BasePlayerGestureListener buildGestureListener() {
        return new MainPlayerGestureListener(this);
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        // Use modern dimensions
        final int buttonsMinWidth = resources.getDimensionPixelSize(
                R.dimen.player_modern_button_min_width);
        final int playerTopPad = resources.getDimensionPixelSize(
                R.dimen.player_modern_top_padding);
        final int controlsPad = resources.getDimensionPixelSize(
                R.dimen.player_modern_controls_padding);
        final int buttonsPad = resources.getDimensionPixelSize(
                R.dimen.player_modern_buttons_padding);
        
        setupElementsSize(buttonsMinWidth, playerTopPad, controlsPad, buttonsPad);
    }

    @Override
    protected float calculateMaxEndScreenThumbnailHeight(@NonNull final Bitmap bitmap) {
        // Modern calculation with better aspect ratio handling
        final float screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        final float maxHeight = screenHeight * 0.75f; // Use 75% of screen height for better UX
        return Math.min(bitmap.getHeight(), maxHeight);
    }

    @Override
    protected void setupSubtitleView(final float captionScale) {
        // Enhanced subtitle styling
        binding.subtitleView.setUserDefaultStyle();
        binding.subtitleView.setUserDefaultTextSize();
        
        // Apply modern caption styling with better contrast
        if (captionScale != 1.0f) {
            binding.subtitleView.setTextSize(binding.subtitleView.getTextSize() * captionScale);
        }
    }

    @Override
    protected void onPlaybackSpeedClicked() {
        if (playbackSpeedPopupMenu != null) {
            playbackSpeedPopupMenu.show();
            isSomePopupMenuVisible = true;
        }
    }

    @Override
    public void removeViewFromParent() {
        // This method will be called by the parent class when needed
    }

    // Enhanced control animations
    
    @Override
    public void showControlsThenHide() {
        showOrHideButtons();
        showSystemUIPartially();

        final long hideTime = binding.playbackControlRoot.isInTouchMode()
                ? DEFAULT_CONTROLS_HIDE_TIME
                : DPAD_CONTROLS_HIDE_TIME;

        // Modern fade-in animation with smooth gradient
        showModernShadows(true, MODERN_ANIMATION_DURATION);
        animateControlsWithEasing(true, MODERN_ANIMATION_DURATION, 0, 
            () -> hideControls(MODERN_ANIMATION_DURATION, hideTime));
    }

    @Override
    public void showControls(final long duration) {
        showOrHideButtons();
        showSystemUIPartially();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showModernShadows(true, duration);
        animateControlsWithEasing(true, duration, 0, null);
    }

    @Override
    public void hideControls(final long duration, final long delay) {
        showOrHideButtons();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            showModernShadows(false, duration);
            animateControlsWithEasing(false, duration, 0, this::hideSystemUIIfNeeded);
        }, delay);
    }

    private void showModernShadows(final boolean show, final long duration) {
        // Enhanced shadow animations with gradients
        animate(binding.playbackControlsShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerTopShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerBottomShadow, show, duration, AnimationType.ALPHA, 0, null);
    }

    private void animateControlsWithEasing(final boolean show, final long duration, 
                                         final long delay, final Runnable onAnimationEnd) {
        binding.playbackControlRoot.animate()
                .alpha(show ? 1.0f : 0.0f)
                .setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(onAnimationEnd)
                .start();
    }

    // Enhanced state change animations
    
    @Override
    public void onPlaying() {
        super.onPlaying();
        
        // Modern play button animation with bounce
        binding.playPauseButton.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    updatePlayPauseButton(PlayButtonAction.PAUSE);
                    binding.playPauseButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(200)
                            .setInterpolator(new OvershootInterpolator(1.1f))
                            .start();
                })
                .start();
    }

    @Override
    public void onPaused() {
        super.onPaused();
        
        // Modern pause animation
        if (!playerGestureListener.isDoubleTapping()) {
            binding.playPauseButton.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(80)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        updatePlayPauseButton(PlayButtonAction.PLAY);
                        binding.playPauseButton.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .setInterpolator(new OvershootInterpolator(1.1f))
                                .start();
                    })
                    .start();
        }
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        
        // Enhanced completion animation
        binding.playPauseButton.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    updatePlayPauseButton(PlayButtonAction.REPLAY);
                    binding.playPauseButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(300)
                            .setInterpolator(new OvershootInterpolator(1.3f))
                            .start();
                })
                .start();
    }

    // Enhanced secondary controls animation
    
    private void onMoreOptionsClicked() {
        final boolean isMoreControlsVisible =
                binding.secondaryControls.getVisibility() == View.VISIBLE;

        // Enhanced rotation animation
        animateRotation(binding.moreOptionsButton, MODERN_ANIMATION_DURATION,
                isMoreControlsVisible ? 0 : 180);
        
        // Smooth slide animation with modern easing
        if (!isMoreControlsVisible) {
            binding.secondaryControls.setVisibility(View.VISIBLE);
            binding.secondaryControls.setAlpha(0f);
            binding.secondaryControls.setTranslationY(-50f);
            
            binding.secondaryControls.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            binding.secondaryControls.animate()
                    .alpha(0f)
                    .translationY(-50f)
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> binding.secondaryControls.setVisibility(View.INVISIBLE))
                    .start();
        }
        
        showControls(MODERN_ANIMATION_DURATION);
    }

    // Enhanced volume/brightness overlays
    
    private void showModernVolumeOverlay(final int volume, final int maxVolume) {
        if (binding.volumeRelativeLayout.getVisibility() != View.VISIBLE) {
            // Modern entrance animation
            binding.volumeRelativeLayout.setVisibility(View.VISIBLE);
            binding.volumeRelativeLayout.setAlpha(0f);
            binding.volumeRelativeLayout.setScaleX(0.8f);
            binding.volumeRelativeLayout.setScaleY(0.8f);
            
            binding.volumeRelativeLayout.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
        
        // Update progress with smooth animation
        final int progress = (int) (((float) volume / maxVolume) * 100);
        binding.volumeProgressBar.setProgress(progress);
    }

    private void hideModernVolumeOverlay() {
        if (binding.volumeRelativeLayout.getVisibility() == View.VISIBLE) {
            binding.volumeRelativeLayout.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> binding.volumeRelativeLayout.setVisibility(View.GONE))
                    .start();
        }
    }

    // Modern seekbar preview enhancement
    
    @Override
    public void onStartTrackingTouch(final android.widget.SeekBar seekBar) {
        super.onStartTrackingTouch(seekBar);
        
        // Enhanced seekbar preview animation
        if (binding.currentDisplaySeek.getVisibility() != View.VISIBLE) {
            binding.currentDisplaySeek.setAlpha(0f);
            binding.currentDisplaySeek.setScaleX(0.8f);
            binding.currentDisplaySeek.setScaleY(0.8f);
        }
        
        animate(binding.currentDisplaySeek, true, MODERN_ANIMATION_DURATION,
                AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, true, MODERN_ANIMATION_DURATION,
                AnimationType.SCALE_AND_ALPHA);
    }

    @Override
    public void onStopTrackingTouch(final android.widget.SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        
        // Smooth exit animation for preview
        binding.currentDisplaySeek.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
                
        binding.currentSeekbarPreviewThumbnail.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // Modern queue panel animations
    
    public void showQueue() {
        if (binding.itemsListPanel.getVisibility() != View.VISIBLE) {
            binding.itemsListPanel.setVisibility(View.VISIBLE);
            binding.itemsListPanel.setAlpha(0f);
            binding.itemsListPanel.setTranslationX(binding.itemsListPanel.getWidth());
            
            binding.itemsListPanel.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    public void hideQueue() {
        if (binding.itemsListPanel.getVisibility() == View.VISIBLE) {
            binding.itemsListPanel.animate()
                    .alpha(0f)
                    .translationX(binding.itemsListPanel.getWidth())
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> binding.itemsListPanel.setVisibility(View.GONE))
                    .start();
        }
    }

    /**
     * Enhanced button visibility animation
     */
    @Override
    protected void showOrHideButtons() {
        super.showOrHideButtons();
        
        // Add subtle fade animations for prev/next buttons
        final org.schabi.newpipe.player.playqueue.PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final boolean showPrev = playQueue.getIndex() != 0;
        final boolean showNext = playQueue.getIndex() + 1 != playQueue.getStreams().size();

        // Animate button visibility changes
        if (binding.playPreviousButton.getVisibility() != (showPrev ? View.VISIBLE : View.INVISIBLE)) {
            binding.playPreviousButton.animate()
                    .alpha(showPrev ? 1.0f : 0.3f)
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .start();
        }
        
        if (binding.playNextButton.getVisibility() != (showNext ? View.VISIBLE : View.INVISIBLE)) {
            binding.playNextButton.animate()
                    .alpha(showNext ? 1.0f : 0.3f)
                    .setDuration(MODERN_ANIMATION_DURATION)
                    .start();
        }
    }

    // Private enum for play button actions (since it's private in parent)
    private enum PlayButtonAction {
        PLAY, PAUSE, REPLAY
    }

    private void updatePlayPauseButton(final PlayButtonAction action) {
        final androidx.appcompat.widget.AppCompatImageButton button = binding.playPauseButton;
        switch (action) {
            case PLAY:
                button.setContentDescription(context.getString(R.string.play));
                button.setImageResource(R.drawable.ic_play_arrow);
                break;
            case PAUSE:
                button.setContentDescription(context.getString(R.string.pause));
                button.setImageResource(R.drawable.ic_pause);
                break;
            case REPLAY:
                button.setContentDescription(context.getString(R.string.replay));
                button.setImageResource(R.drawable.ic_replay);
                break;
        }
    }
}