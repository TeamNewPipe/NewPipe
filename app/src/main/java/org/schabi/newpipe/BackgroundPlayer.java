package org.schabi.newpipe;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;

import java.io.IOException;

/**
 * Created by Adam Howard on 08/11/15.
 *
 * Copyright (c) Adam Howard <achdisposable1@gmail.com> 2015
 *
 * BackgroundPlayer.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
public class BackgroundPlayer extends IntentService /*implements MediaPlayer.OnPreparedListener*/ {

    private static final String TAG = BackgroundPlayer.class.toString();
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public BackgroundPlayer() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String source = intent.getDataString();

        MediaPlayer mediaPlayer = new MediaPlayer();
        //mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);//cpu lock apparently
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(source);
            mediaPlayer.prepare(); // IntentService already puts us in a separate worker thread,
            //so calling the blocking prepare() method should be ok
        } catch (IOException ioe) {
            ioe.printStackTrace();
            //can't really do anything useful without a file to play; exit early
            return;
        }
        //mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.start();
/*
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification();
        notification.tickerText = text;
        notification.icon = R.drawable.play0;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(getApplicationContext(), "MusicPlayerSample",
                "Playing: " + songName, pi);
        startForeground(NOTIFICATION_ID, notification);*/
    }
}
