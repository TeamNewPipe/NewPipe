package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.Menu;

import org.schabi.newpipe.R;

public final class BackgroundPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "BackgroundPlayerActivity";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public String getSupportActionTitle() {
        return getResources().getString(R.string.title_activity_play_queue);
    }

    @Override
    public Intent getBindIntent() {
        return new Intent(this, MainPlayer.class);
    }

    @Override
    public void startPlayerListener() {
        if (player instanceof VideoPlayerImpl) {
            ((VideoPlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player instanceof VideoPlayerImpl) {
            ((VideoPlayerImpl) player).removeActivityListener(this);
        }
    }

    @Override
    public int getPlayerOptionMenuResource() {
        return R.menu.menu_play_queue_bg;
    }

    @Override
    public void setupMenu(final Menu menu) {
        if (player == null) {
            return;
        }

        menu.findItem(R.id.action_switch_popup)
                .setVisible(!((VideoPlayerImpl) player).popupPlayerSelected());
        menu.findItem(R.id.action_switch_background)
                .setVisible(!((VideoPlayerImpl) player).audioPlayerSelected());
    }
}
