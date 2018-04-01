package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

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
        if (player != null) {
            player.setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null) {
            player.removeActivityListener(this);
        }
    }

    @Override
    public int getPlayerOptionMenuResource() {
        return R.menu.menu_play_queue_bg;
    }

    @Override
    public boolean onPlayerOptionSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_popup) {

            if (!PermissionHelper.isPopupEnabled(this)) {
                PermissionHelper.showPopupEnablementToast(this);
                return true;
            }

            this.player.setRecovery();
            getApplicationContext().startService(getSwitchIntent(MainPlayerService.class, false));
            return true;
        }

        if (item.getItemId() == R.id.action_switch_background) {
            this.player.setRecovery();
            getApplicationContext().startService(getSwitchIntent(MainPlayerService.class, true));
            return true;
        }
        return false;
    }

    @Override
    public void setupMenu(Menu menu) {
        if(player == null) return;

        menu.findItem(R.id.action_switch_popup).setVisible(!player.popupPlayerSelected());
        menu.findItem(R.id.action_switch_background).setVisible(!player.audioPlayerSelected());
    }
}
