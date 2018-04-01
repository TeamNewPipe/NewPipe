package org.schabi.newpipe.player;

import android.os.Binder;
import android.support.annotation.NonNull;
import org.mozilla.javascript.tools.jsc.Main;

class PlayerServiceBinder extends Binder {
    private final VideoPlayerImpl mainPlayer;

    PlayerServiceBinder(@NonNull final VideoPlayerImpl mainPlayer) {
        this.mainPlayer = mainPlayer;
    }

    VideoPlayerImpl getPlayerInstance() {
        return mainPlayer;
    }
}
