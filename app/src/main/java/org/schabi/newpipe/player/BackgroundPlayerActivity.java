package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;

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
    public boolean onPlayerOptionSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_switch_popup) {

            if (!PermissionHelper.isPopupEnabled(this)) {
                PermissionHelper.showPopupEnablementToast(this);
                return true;
            }

            this.player.setRecovery();
            NavigationHelper.playOnPopupPlayer(
                    getApplicationContext(), player.playQueue, this.player.isPlaying());
            return true;
        }

        if (item.getItemId() == R.id.action_switch_background) {
            this.player.setRecovery();
            NavigationHelper.playOnBackgroundPlayer(
                    getApplicationContext(), player.playQueue, this.player.isPlaying());
            return true;
        }

        return false;
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
