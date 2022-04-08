package org.schabi.newpipe.player.ui;

import androidx.annotation.NonNull;

import org.schabi.newpipe.player.NotificationUtil;
import org.schabi.newpipe.player.Player;

public final class NotificationPlayerUi extends PlayerUi {
    boolean foregroundNotificationAlreadyCreated = false;

    public NotificationPlayerUi(@NonNull final Player player) {
        super(player);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        if (!foregroundNotificationAlreadyCreated) {
            NotificationUtil.getInstance()
                    .createNotificationAndStartForeground(player, player.getService());
            foregroundNotificationAlreadyCreated = true;
        }
    }

    // TODO TODO on destroy remove foreground
}
