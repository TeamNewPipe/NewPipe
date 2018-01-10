package org.schabi.newpipe.player;

import android.content.Intent;

import org.schabi.newpipe.R;

public final class PopupVideoPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "PopupVideoPlayerActivity";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public String getSupportActionTitle() {
        return getResources().getString(R.string.title_activity_popup_player);
    }

    @Override
    public Intent getBindIntent() {
        return new Intent(this, PopupVideoPlayer.class);
    }

    @Override
    public void startPlayerListener() {
        if (player != null && player instanceof PopupVideoPlayer.VideoPlayerImpl) {
            ((PopupVideoPlayer.VideoPlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null && player instanceof PopupVideoPlayer.VideoPlayerImpl) {
            ((PopupVideoPlayer.VideoPlayerImpl) player).removeActivityListener(this);
        }
    }
}
