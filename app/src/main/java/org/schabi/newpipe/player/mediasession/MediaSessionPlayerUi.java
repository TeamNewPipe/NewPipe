package org.schabi.newpipe.player.mediasession;

import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playback.PlayerMediaSession;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.Optional;

public class MediaSessionPlayerUi extends PlayerUi {

    private MediaSessionManager mediaSessionManager;

    public MediaSessionPlayerUi(@NonNull final Player player) {
        super(player);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        if (mediaSessionManager != null) {
            mediaSessionManager.dispose();
        }
        mediaSessionManager = new MediaSessionManager(context, player.getExoPlayer(),
                new PlayerMediaSession(player));
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        if (mediaSessionManager != null) {
            mediaSessionManager.dispose();
            mediaSessionManager = null;
        }
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        // TODO decide whether to handle ACTION_HEADSET_PLUG or not
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);

        final boolean showThumbnail = player.getPrefs().getBoolean(
                context.getString(R.string.show_thumbnail_key), true);

        mediaSessionManager.setMetadata(
                player.getVideoTitle(),
                player.getUploaderName(),
                showThumbnail ? Optional.ofNullable(player.getThumbnail()) : Optional.empty(),
                StreamTypeUtil.isLiveStream(info.getStreamType()) ? -1 : info.getDuration()
        );
    }

    public void handleMediaButtonIntent(final Intent intent) {
        if (mediaSessionManager != null) {
            mediaSessionManager.handleMediaButtonIntent(intent);
        }
    }

    public Optional<MediaSessionCompat.Token> getSessionToken() {
        return Optional.ofNullable(mediaSessionManager).map(MediaSessionManager::getSessionToken);
    }
}
