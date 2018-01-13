package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.MenuItem;

import org.schabi.newpipe.R;

import static org.schabi.newpipe.player.MainPlayerService.ACTION_CLOSE;

public final class BackgroundPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "BackgroundPlayerActivity";

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
        return new Intent(this, MainPlayerService.class);
    }

    @Override
    public void startPlayerListener() {
        if (player != null && player instanceof MainPlayerService.VideoPlayerImpl) {
            ((MainPlayerService.VideoPlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null && player instanceof MainPlayerService.VideoPlayerImpl) {
            ((MainPlayerService.VideoPlayerImpl) player).removeActivityListener(this);
        }
    }

    @Override
    public int getPlayerOptionMenuResource() {
        return R.menu.menu_play_queue_bg;
    }

    @Override
    public boolean onPlayerOptionSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_popup) {
            this.player.setRecovery();
            getApplicationContext().sendBroadcast(getPlayerShutdownIntent());
            getApplicationContext().startService(getSwitchIntent(PopupVideoPlayer.class, false));
            return true;
        }
        return false;
    }

    @Override
    public Intent getPlayerShutdownIntent() {
        return new Intent(ACTION_CLOSE);
    }
}
