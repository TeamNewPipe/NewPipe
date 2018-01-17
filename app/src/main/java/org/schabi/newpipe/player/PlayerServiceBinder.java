package org.schabi.newpipe.player;

import android.os.Binder;
import android.support.annotation.NonNull;
import org.mozilla.javascript.tools.jsc.Main;

class PlayerServiceBinder extends Binder {
    private final MainPlayerService.VideoPlayerImpl mainPlayer;

    PlayerServiceBinder(@NonNull final MainPlayerService.VideoPlayerImpl mainPlayer) {
        this.mainPlayer = mainPlayer;
    }

    MainPlayerService.VideoPlayerImpl getPlayerInstance() {
        return mainPlayer;
    }
}
