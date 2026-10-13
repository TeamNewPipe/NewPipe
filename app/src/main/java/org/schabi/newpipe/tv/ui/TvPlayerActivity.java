/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerIntentType;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.tv.player.TvPlaybackSupportFragment;
import org.schabi.newpipe.util.NavigationHelper;

public class TvPlayerActivity extends FragmentActivity {

    private static PlayQueue pendingPlayQueue = null;

    public static void setPendingPlayQueue(@Nullable final PlayQueue queue) {
        pendingPlayQueue = queue;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_tv_main);

        if (pendingPlayQueue != null) {
            final Intent serviceIntent = NavigationHelper.getPlayerIntent(
                    this, PlayerService.class, pendingPlayQueue, PlayerIntentType.AllOthers)
                    .putExtra(Player.PLAYER_TYPE, PlayerType.MAIN)
                    .putExtra(Player.RESUME_PLAYBACK, true);
            ContextCompat.startForegroundService(this, serviceIntent);
            setPendingPlayQueue(null);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tv_main_container, new TvPlaybackSupportFragment())
                    .commit();
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (PlayerHolder.getInstance().isPlaying()) {
                    PlayerHolder.getInstance().pause();
                } else {
                    PlayerHolder.getInstance().play();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                PlayerHolder.getInstance().play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                PlayerHolder.getInstance().pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                PlayerHolder.getInstance().playNext();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                PlayerHolder.getInstance().playPrevious();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                PlayerHolder.getInstance().seekTo(
                        PlayerHolder.getInstance().getCurrentPosition() + 10000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                PlayerHolder.getInstance().seekTo(
                        Math.max(0, PlayerHolder.getInstance().getCurrentPosition() - 10000));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
