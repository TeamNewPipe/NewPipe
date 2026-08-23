package org.schabi.newpipe.player.mediasession;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import androidx.annotation.Nullable;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer;

public interface PlayerServiceInterface{


    public void onCreate();
    public int onStartCommand(final Intent intent, final int flags, final int startId);

    public void stopForImmediateReusing();

    public void onTaskRemoved(final Intent rootIntent);

    public void onDestroy();

    public void stopService();

    public IBinder onBind(final Intent intent);

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    boolean isLandscape();

    @Nullable
    public View getView();

    public void removeViewFromParent();

    public MediaSessionCompat getMediaSession();

    public MediaBrowserPlaybackPreparer getMediaBrowserPlaybackPreparer();

    public Service getInstance();

}