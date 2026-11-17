package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlaybackPresentationMode;
import org.schabi.newpipe.player.equalizer.EqualizerState;
import org.schabi.newpipe.player.helper.SleepTimer;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;

import java.util.List;

/**
 * A player UI is a component that can seamlessly connect and disconnect from the {@link Player} and
 * provide a user interface of some sort. Try to extend this class instead of adding more code to
 * {@link Player}!
 */
public abstract class PlayerUi {

    @NonNull protected final Context context;
    @NonNull protected final Player player;

    /**
     * @param player the player instance that will be usable throughout the lifetime of this UI; its
     *               context should already have been initialized
     */
    protected PlayerUi(@NonNull final Player player) {
        this.context = player.getContext();
        this.player = player;
    }

    /**
     * @return the player instance this UI was constructed with
     */
    @NonNull
    public Player getPlayer() {
        return player;
    }


    /**
     * Called after the player received an intent and processed it.
     */
    public void setupAfterIntent() {
    }

    /**
     * Called right after the exoplayer instance is constructed, or right after this UI is
     * constructed if the exoplayer is already available then. Note that the exoplayer instance
     * could be built and destroyed multiple times during the lifetime of the player, so this method
     * might be called multiple times.
     */
    public void initPlayer() {
    }

    /**
     * Called when playback in the exoplayer is about to start, or right after this UI is
     * constructed if the exoplayer and the play queue are already available then. The play queue
     * will therefore always be not null.
     */
    public void initPlayback() {
    }

    /**
     * @param mode the new video, listening, background, or car presentation mode
     */
    public void onPlaybackPresentationModeChanged(
            @NonNull final PlaybackPresentationMode mode) {
    }

    /**
     * Called when the exoplayer instance is about to be destroyed. Note that the exoplayer instance
     * could be built and destroyed multiple times during the lifetime of the player, so this method
     * might be called multiple times. Be sure to unset any video surface view or play queue
     * listeners! This will also be called when this UI is being discarded, just before {@link
     * #destroy()}.
     */
    public void destroyPlayer() {
    }

    /**
     * Called when this UI is being discarded, either because the player is switching to a different
     * UI or because the player is shutting down completely.
     */
    public void destroy() {
    }

    /**
     * Called when the player is smooth-stopping, that is, transitioning smoothly to a new play
     * queue after the user tapped on a new video stream while a stream was playing in the video
     * detail fragment.
     */
    public void smoothStopForImmediateReusing() {
    }

    /**
     * Called when the video detail fragment listener is connected with the player, or right after
     * this UI is constructed if the listener is already connected then.
     */
    public void onFragmentListenerSet() {
    }

    /**
     * Broadcasts that the player receives will also be notified to UIs here. If you want to
     * register new broadcast actions to receive here, add them to {@link
     * Player#setupBroadcastReceiver()}.
     * @param intent the broadcast intent received by the player
     */
    public void onBroadcastReceived(final Intent intent) {
    }

    /**
     * Called when stream progress (i.e. the current time in the seekbar) or stream duration change.
     * Will surely be called every {@link Player#PROGRESS_LOOP_INTERVAL_MILLIS} while a stream is
     * playing.
     * @param currentProgress the current progress in milliseconds
     * @param duration        the duration of the stream being played
     * @param bufferPercent   the percentage of stream already buffered, see {@link
     *                        androidx.media3.common.BasePlayer#getBufferedPercentage()}
     */
    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
    }

    /**
     * Shows a SponsorBlock skip affordance in UIs that can safely display one.
     *
     * @param label the text to display inside the skip affordance
     * @param onClick the action to run when the skip affordance is clicked
     */
    public void showSponsorBlockSkipButton(@NonNull final String label,
                                           @NonNull final Runnable onClick) {
    }

    /**
     * Hides the SponsorBlock skip affordance if it is currently visible.
     */
    public void hideSponsorBlockSkipButton() {
    }

    /**
     * Updates SponsorBlock segment markers in UIs that have a video seek bar.
     *
     * @param segments the eligible SponsorBlock segments to draw
     * @param durationMillis the video duration in milliseconds
     */
    public void updateSponsorBlockSeekBarMarkers(
            @NonNull final List<SponsorBlockSegment> segments,
            final long durationMillis) {
    }

    /**
     * Clears SponsorBlock segment markers from UIs that have a video seek bar.
     */
    public void clearSponsorBlockSeekBarMarkers() {
    }


    public void onPrepared() {
    }

    public void onBlocked() {
    }

    public void onMediaItemTransition() {
    }

    public void onPlaying() {
    }

    public void onBuffering() {
    }

    public void onPaused() {
    }

    public void onPausedSeek() {
    }

    public void onCompleted() {
    }

    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
    }

    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
    }

    public void onMuteUnmuteChanged(final boolean isMuted) {
    }

    public void onEqualizerStateChanged(@NonNull final EqualizerState state,
                                        final boolean operational) {
    }

    public void onVolumeBoostChanged(final int gainMb, final boolean operational) {
    }

    /**
     * Called when sleep timer state or its displayed countdown changes.
     *
     * @param mode the active timer mode, or {@link SleepTimer.Mode#NONE}
     * @param remainingMillis estimated wall-clock time remaining, or
     *                        {@link SleepTimer#REMAINING_TIME_UNSET}
     * @param fadeOutEnabled whether final fade-out is enabled
     */
    public void onSleepTimerChanged(@NonNull final SleepTimer.Mode mode,
                                    final long remainingMillis,
                                    final boolean fadeOutEnabled) {
    }

    /**
     * @see androidx.media3.common.Player.Listener#onTracksChanged(Tracks)
     * @param currentTracks the available tracks information
     */
    public void onTextTracksChanged(@NonNull final Tracks currentTracks) {
    }

    /**
     * @see androidx.media3.common.Player.Listener#onPlaybackParametersChanged
     * @param playbackParameters the new playback parameters
     */
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
    }

    /**
     * @see androidx.media3.common.Player.Listener#onRenderedFirstFrame
     */
    public void onRenderedFirstFrame() {
    }

    /**
     * @see androidx.media3.exoplayer.text.TextOutput#onCues
     * @param cues the cues to pass to the subtitle view
     */
    public void onCues(@NonNull final List<Cue> cues) {
    }

    /**
     * Called when the stream being played changes.
     * @param info the {@link StreamInfo} metadata object, along with data about the selected and
     *             available video streams (to be used to build the resolution menus, for example)
     */
    public void onMetadataChanged(@NonNull final StreamInfo info) {
    }

    /**
     * Called for metadata that does not originate from an online extractor.
     *
     * @param tag the metadata for the current non-extractor media item
     */
    public void onMetadataChanged(@NonNull final MediaItemTag tag) {
    }

    /**
     * Called when the thumbnail for the current metadata was loaded.
     * @param bitmap the thumbnail to process, or null if there is no thumbnail or there was an
     *               error when loading the thumbnail
     */
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
    }

    /**
     * Called when the play queue was edited: a stream was appended, moved or removed.
     */
    public void onPlayQueueEdited() {
    }

    /**
     * @param videoSize the new video size, useful to set the surface aspect ratio
     * @see androidx.media3.common.Player.Listener#onVideoSizeChanged
     */
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
    }
}
