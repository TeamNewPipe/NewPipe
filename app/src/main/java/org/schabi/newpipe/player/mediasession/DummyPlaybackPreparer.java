package org.schabi.newpipe.player.mediasession;

import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

public class DummyPlaybackPreparer implements MediaSessionConnector.PlaybackPreparer {
    @Override
    public long getSupportedPrepareActions() {
        return 0;
    }

    @Override
    public void onPrepare() {

    }

    @Override
    public void onPrepareFromMediaId(String mediaId, Bundle extras) {

    }

    @Override
    public void onPrepareFromSearch(String query, Bundle extras) {

    }

    @Override
    public void onPrepareFromUri(Uri uri, Bundle extras) {

    }

    @Override
    public String[] getCommands() {
        return new String[0];
    }

    @Override
    public void onCommand(Player player, String command, Bundle extras, ResultReceiver cb) {

    }
}
