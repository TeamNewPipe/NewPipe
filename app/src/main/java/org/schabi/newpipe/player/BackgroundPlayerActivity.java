package org.schabi.newpipe.player;

import android.content.Intent;

import org.schabi.newpipe.R;

public final class BackgroundPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "BGPlayerActivity";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public String getSupportActionTitle() {
        return getResources().getString(R.string.title_activity_background_player);
    }

    @Override
    public Intent getBindIntent() {
        return new Intent(this, BackgroundPlayer.class);
    }

    @Override
    public void startPlayerListener() {
        if (player != null && player instanceof BackgroundPlayer.BasePlayerImpl) {
            ((BackgroundPlayer.BasePlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null && player instanceof BackgroundPlayer.BasePlayerImpl) {
            ((BackgroundPlayer.BasePlayerImpl) player).removeActivityListener(this);
        }
    }
}
