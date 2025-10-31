package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerModernBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.MainPlayerGestureListener;
import org.schabi.newpipe.player.helper.PlayerHelper;

/**
 * Modern Video Player UI with improved design and user experience
 */
public final class ModernVideoPlayerUi extends VideoPlayerUi {
    private static final String TAG = ModernVideoPlayerUi.class.getSimpleName();
    
    private PlayerModernBinding modernBinding;
    private final ViewGroup parent;

    public ModernVideoPlayerUi(@NonNull final Player player,
                               @NonNull final ViewGroup parentViewGroup) {
        super(player, createBinding(player.getContext(), parentViewGroup));
        parent = parentViewGroup;
        modernBinding = PlayerModernBinding.bind(binding.getRoot());
        
        // Apply modern styling
        setupModernStyling();
    }

    private static PlayerBinding createBinding(@NonNull final Context context,
                                             @NonNull final ViewGroup parent) {
        final View view = LayoutInflater.from(context)
                .inflate(R.layout.player_modern, parent, false);
        return PlayerBinding.bind(view);
    }

    private void setupModernStyling() {
        // Apply modern accent color to seekbar
        final int accentColor = ContextCompat.getColor(context, R.color.modern_accent);
        binding.playbackSeekBar.getThumb().setColorFilter(accentColor);
        binding.playbackSeekBar.getProgressDrawable().setColorFilter(accentColor);
        
        // Set modern button backgrounds
        setupModernButtons();
        
        // Apply modern gradient overlays
        binding.playerTopShadow.setVisibility(View.VISIBLE);
        binding.playerBottomShadow.setVisibility(View.VISIBLE);
    }

    private void setupModernButtons() {
        // The button backgrounds are already set in the layout XML
        // This method can be extended for additional button styling
        
        // Apply modern ripple effects and states
        final int rippleColor = ContextCompat.getColor(context, R.color.white_20);
        
        // Add subtle animations to button presses
        setupButtonAnimations();
    }

    private void setupButtonAnimations() {
        // Add scale animations for button interactions
        final float scale = 0.95f;
        final long duration = 100;
        
        // Apply to main play buttons
        addScaleAnimation(binding.playPauseButton, scale, duration);
        addScaleAnimation(binding.playPreviousButton, scale, duration);
        addScaleAnimation(binding.playNextButton, scale, duration);
        
        // Apply to control buttons
        addScaleAnimation(binding.moreOptionsButton, scale, duration);
        addScaleAnimation(binding.playerCloseButton, scale, duration);
    }

    private void addScaleAnimation(final View view, final float scale, final long duration) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(duration).start();
                    break;
            }
            return false; // Let the click listener handle the actual click
        });
    }

    @Override
    BasePlayerGestureListener buildGestureListener() {
        return new MainPlayerGestureListener(this);
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        // Modern sizing with improved touch targets
        final int buttonsMinWidth = resources.getDimensionPixelSize(R.dimen.player_modern_button_min_width);
        final int playerTopPad = resources.getDimensionPixelSize(R.dimen.player_modern_top_padding);
        final int controlsPad = resources.getDimensionPixelSize(R.dimen.player_modern_controls_padding);
        final int buttonsPad = resources.getDimensionPixelSize(R.dimen.player_modern_buttons_padding);
        
        setupElementsSize(buttonsMinWidth, playerTopPad, controlsPad, buttonsPad);
    }

    @Override
    protected float calculateMaxEndScreenThumbnailHeight(@NonNull final Bitmap bitmap) {
        // Modern calculation for thumbnail scaling
        final float screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        final float maxHeight = screenHeight * 0.8f; // Use 80% of screen height
        return Math.min(bitmap.getHeight(), maxHeight);
    }

    @Override
    protected void setupSubtitleView(final float captionScale) {
        // Modern subtitle styling
        binding.subtitleView.setUserDefaultStyle();
        binding.subtitleView.setUserDefaultTextSize();
        
        // Apply modern caption scale
        if (captionScale != 1.0f) {
            binding.subtitleView.setTextSize(binding.subtitleView.getTextSize() * captionScale);
        }
    }

    @Override
    protected void onPlaybackSpeedClicked() {
        // Modern playback speed dialog can be implemented here
        // For now, use the existing popup menu
        if (playbackSpeedPopupMenu != null) {
            playbackSpeedPopupMenu.show();
            isSomePopupMenuVisible = true;
        }
    }

    @Override
    public void removeViewFromParent() {
        if (binding.getRoot().getParent() == parent) {
            parent.removeView(binding.getRoot());
        }
    }

    // Additional modern UI enhancements
    
    /**
     * Apply modern fade animations to controls
     */
    @Override
    public void showControlsThenHide() {
        // Enhanced fade animation with modern easing
        super.showControlsThenHide();
        
        // Add gradient overlay animations
        animateGradientOverlays(true);
    }

    @Override
    public void hideControls(final long duration, final long delay) {
        super.hideControls(duration, delay);
        
        // Hide gradient overlays
        animateGradientOverlays(false);
    }

    private void animateGradientOverlays(final boolean show) {
        final float alpha = show ? 1.0f : 0.0f;
        final long duration = DEFAULT_CONTROLS_DURATION;
        
        binding.playerTopShadow.animate()
                .alpha(alpha)
                .setDuration(duration)
                .start();
                
        binding.playerBottomShadow.animate()
                .alpha(alpha)
                .setDuration(duration)
                .start();
    }

    /**
     * Enhanced volume/brightness overlay with modern styling
     */
    @Override
    public void onVolumeChanged(final int volume, final int maxVolume) {
        super.onVolumeChanged(volume, maxVolume);
        
        // Modern volume indicator styling
        if (binding.volumeRelativeLayout.getVisibility() == View.VISIBLE) {
            // Apply modern background and animations
            binding.volumeRelativeLayout.setBackground(
                ContextCompat.getDrawable(context, R.drawable.modern_overlay_background));
        }
    }

    /**
     * Get the modern binding for extended functionality
     */
    public PlayerModernBinding getModernBinding() {
        return modernBinding;
    }
}