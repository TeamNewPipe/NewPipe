package org.schabi.newpipe.player;

import android.os.Binder;

import androidx.annotation.NonNull;

class PlayerServiceBinder extends Binder {
    private final BasePlayer basePlayer;

    PlayerServiceBinder(@NonNull final BasePlayer basePlayer) {
        this.basePlayer = basePlayer;
    }

    BasePlayer getPlayerInstance() {
        return basePlayer;
    }
}
