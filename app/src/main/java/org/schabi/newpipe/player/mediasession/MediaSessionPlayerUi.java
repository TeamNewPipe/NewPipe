package org.schabi.newpipe.player.mediasession;

import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommands;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.notification.NotificationActionData;
import org.schabi.newpipe.player.notification.NotificationConstants;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MediaSessionPlayerUi extends PlayerUi
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    @NonNull
    private final MediaLibrarySession mediaSession;
    @NonNull
    private final androidx.media3.common.Player browserPlayer;

    private final String ignoreHardwareMediaButtonsKey;
    @NonNull
    private final List<String> notificationActionKeys;
    private boolean shouldIgnoreHardwareMediaButtons = false;

    // used to check whether any notification action changed, before sending costly updates
    private List<NotificationActionData> prevNotificationActions = List.of();


    public MediaSessionPlayerUi(@NonNull final Player player,
                                @NonNull final MediaLibrarySession mediaSession,
                                @NonNull final androidx.media3.common.Player browserPlayer) {
        super(player);
        this.mediaSession = mediaSession;
        this.browserPlayer = browserPlayer;
        this.ignoreHardwareMediaButtonsKey =
                context.getString(R.string.ignore_hardware_media_buttons_key);
        this.notificationActionKeys = IntStream.of(3, 4)
                .mapToObj(i -> context.getString(NotificationConstants.SLOT_PREF_KEYS[i]))
                .collect(Collectors.toList());
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        destroyPlayer(); // release previously used resources

        mediaSession.setPlayer(getForwardingPlayer());

        // listen to changes to ignore_hardware_media_buttons_key
        updateShouldIgnoreHardwareMediaButtons(player.getPrefs());
        player.getPrefs().registerOnSharedPreferenceChangeListener(this);

        // force updating media session actions by resetting the previous ones
        prevNotificationActions = List.of();
        updateMediaSessionActions();
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        player.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        player.getService().setShouldIgnoreHardwareMediaButtons(false);
        if (mediaSession.getPlayer() != browserPlayer) {
            mediaSession.setPlayer(browserPlayer);
        }
        prevNotificationActions = List.of();
    }

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        // Artwork is published through the Media3 MediaItem metadata. The custom notification
        // continues to use the already-loaded bitmap directly.
    }


    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key == null || key.equals(ignoreHardwareMediaButtonsKey)) {
            updateShouldIgnoreHardwareMediaButtons(sharedPreferences);
        }
        if (key == null || notificationActionKeys.contains(key)) {
            updateMediaSessionActions();
        }
    }

    public void updateShouldIgnoreHardwareMediaButtons(final SharedPreferences sharedPreferences) {
        shouldIgnoreHardwareMediaButtons =
                sharedPreferences.getBoolean(ignoreHardwareMediaButtonsKey, false);
        player.getService().setShouldIgnoreHardwareMediaButtons(
                shouldIgnoreHardwareMediaButtons);
    }

    public Optional<MediaLibrarySession> getMediaSession() {
        return Optional.of(mediaSession);
    }

    /**
     * Republishes configured actions after Android's media-notification controller connects.
     * The controller commonly connects after player initialization, especially on newer OEM
     * System UI implementations, so the initial action update cannot reliably target it.
     *
     * @param controller the controller whose connection has completed
     */
    public void onMediaSessionControllerConnected(
            @NonNull final MediaSession.ControllerInfo controller) {
        if (shouldRefreshNotificationActions(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                mediaSession.isMediaNotificationController(controller))) {
            updateMediaSessionActions();
        }
    }

    static boolean shouldRefreshNotificationActions(final boolean systemUiActionsSupported,
                                                    final boolean notificationController) {
        return systemUiActionsSupported && notificationController;
    }


    private ForwardingPlayer getForwardingPlayer() {
        // ForwardingPlayer means that all media session actions called on this player are
        // forwarded directly to the connected exoplayer, except for the overridden methods. So
        // override play and pause since our player adds more functionality to them over exoplayer.
        return new ForwardingPlayer(player.getExoPlayer()) {
            @Override
            public void play() {
                player.play();
                // hide the player controls even if the play command came from the media session
                player.UIs().get(VideoPlayerUi.class).ifPresent(ui -> ui.hideControls(0, 0));
            }

            @Override
            public void pause() {
                player.pause();
            }

            @Override
            public void seekToNextMediaItem() {
                player.playNext();
            }

            @Override
            public void seekToNext() {
                player.playNext();
            }

            @Override
            public void seekToPreviousMediaItem() {
                player.playPrevious();
            }

            @Override
            public void seekToPrevious() {
                player.playPrevious();
            }

            @Override
            public void seekTo(final int mediaItemIndex, final long positionMs) {
                if (player.getPlayQueue() != null
                        && mediaItemIndex >= 0
                        && mediaItemIndex < player.getPlayQueue().size()) {
                    player.selectQueueItem(player.getPlayQueue().getItem(mediaItemIndex));
                    if (positionMs != C.TIME_UNSET && positionMs > 0) {
                        player.seekTo(positionMs);
                    }
                } else {
                    super.seekTo(mediaItemIndex, positionMs);
                }
            }

            @NonNull
            @Override
            public MediaMetadata getMediaMetadata() {
                // Preserve the MediaItem metadata until the resolved tag is available.
                // Overriding it with WizeStream's "Unknown" display fallback can make
                // Android System UI lose the video's title and artist during transitions.
                return MediaSessionMetadataPolicy.merge(
                        super.getMediaMetadata(), player.getCurrentMetadata());
            }
        };
    }


    private void updateMediaSessionActions() {
        // On Android 13+ (or Android T or API 33+) the actions in the player notification can't be
        // controlled directly anymore, but are instead derived from custom media session actions.
        // However the system allows customizing only two of these actions, since the other three
        // are fixed to play-pause-buffering, previous, next.

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Although setting media session actions on older android versions doesn't seem to
            // cause any trouble, it also doesn't seem to do anything, so we don't do anything to
            // save battery. Check out NotificationUtil.updateActions() to see what happens on
            // older android versions.
            return;
        }

        if (mediaSession.getPlayer() == browserPlayer) {
            return;
        }

        // only use the fourth and fifth actions (the settings page also shows only the last 2 on
        // Android 13+)
        // Keep null placeholders for actions configured as "Nothing". Dropping them here would
        // shift the other action into the wrong button slot and make configurations with the same
        // non-empty actions but different positions compare as equal.
        final List<NotificationActionData> newNotificationActions = IntStream.of(3, 4)
                .map(i -> player.getPrefs().getInt(
                        player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                        NotificationConstants.SLOT_DEFAULTS[i]))
                .mapToObj(action -> NotificationActionData
                        .fromNotificationActionEnum(player, action))
                .collect(Collectors.toList());

        final List<CommandButton> buttons = IntStream.range(0, newNotificationActions.size())
                .filter(index -> newNotificationActions.get(index) != null)
                .mapToObj(index -> MediaSessionActionProvider.buttonFor(
                        newNotificationActions.get(index), index == 0))
                .collect(Collectors.toList());

        // Avoid the global broadcast when the actions did not change. The media-notification
        // controller is synchronized below on every update because it can connect after the
        // initial player setup.
        if (!newNotificationActions.equals(prevNotificationActions)) {
            prevNotificationActions = newNotificationActions;
            mediaSession.setMediaButtonPreferences(buttons);
        }
        syncMediaNotificationController(buttons);
    }

    /**
     * Keeps Android's System UI controller authorized for the custom buttons published above.
     *
     * <p>Media3 filters media button preferences against the commands available to its special
     * media-notification controller. If the custom commands are not explicitly available there,
     * Android 13+ drops the corresponding platform PlaybackState custom actions, which removes
     * WizeStream's fourth and fifth controls (Repeat and Close by default).</p>
     *
     * @param buttons the currently configured Android 13+ media action buttons
     */
    private void syncMediaNotificationController(@NonNull final List<CommandButton> buttons) {
        final MediaSession.ControllerInfo notificationController =
                mediaSession.getMediaNotificationControllerInfo();
        if (notificationController == null) {
            return;
        }

        final SessionCommands.Builder sessionCommands = new SessionCommands.Builder()
                .addSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.commands);
        MediaSessionActionProvider.supportedActions().stream()
                .map(MediaSessionActionProvider::commandFor)
                .forEach(sessionCommands::add);

        mediaSession.setAvailableCommands(
                notificationController,
                sessionCommands.build(),
                mediaSession.getPlayer().getAvailableCommands());
        mediaSession.setMediaButtonPreferences(notificationController, buttons);
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        updateMediaSessionActions();
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        updateMediaSessionActions();
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        updateMediaSessionActions();
    }

    @Override
    public void onPaused() {
        super.onPaused();
        updateMediaSessionActions();
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        updateMediaSessionActions();
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        updateMediaSessionActions();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);
        updateMediaSessionActions();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        updateMediaSessionActions();
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (ACTION_RECREATE_NOTIFICATION.equals(intent.getAction())) {
            // the notification actions changed
            updateMediaSessionActions();
        }
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        updateMediaSessionActions();
    }

    @Override
    public void onMetadataChanged(@NonNull final MediaItemTag tag) {
        super.onMetadataChanged(tag);
        updateMediaSessionActions();
    }

    @Override
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        updateMediaSessionActions();
    }
}
