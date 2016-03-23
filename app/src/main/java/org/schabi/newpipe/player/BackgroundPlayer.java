package org.schabi.newpipe.player;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.VideoItemDetailActivity;
import org.schabi.newpipe.VideoItemDetailFragment;

import java.io.IOException;

/**
 * Created by Adam Howard on 08/11/15.
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

/**Plays the audio stream of videos in the background.*/
public class BackgroundPlayer extends Service /*implements MediaPlayer.OnPreparedListener*/ {

    private static final String TAG = BackgroundPlayer.class.toString();
    private static final String ACTION_STOP = TAG + ".STOP";
    private static final String ACTION_PLAYPAUSE = TAG + ".PLAYPAUSE";

    // Extra intent arguments
    public static final String TITLE = "title";
    public static final String WEB_URL = "web_url";
    public static final String SERVICE_ID = "service_id";
    public static final String CHANNEL_NAME = "channel_name";

    private volatile String webUrl = "";
    private volatile int serviceId = -1;
    private volatile String channelName = "";

    // Determines if the service is already running.
    // Prevents launching the service twice.
    public static volatile boolean isRunning = false;

    public BackgroundPlayer() {
        super();
    }

    @Override
    public void onCreate() {
        /*PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);*/
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, R.string.background_player_playing_toast,
                Toast.LENGTH_SHORT).show();

        String source = intent.getDataString();
        //Log.i(TAG, "backgroundPLayer source:"+source);
        String videoTitle = intent.getStringExtra(TITLE);
        webUrl = intent.getStringExtra(WEB_URL);
        serviceId = intent.getIntExtra(SERVICE_ID, -1);
        channelName = intent.getStringExtra(CHANNEL_NAME);

        //do nearly everything in a separate thread
        PlayerThread player = new PlayerThread(source, videoTitle, this);
        player.start();

        isRunning = true;

        // If we get killed after returning here, don't restart
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding (yet?), so return null
        return null;
    }

    @Override
    public void onDestroy() {
        //Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        isRunning = false;
    }

    private class PlayerThread extends Thread {
        MediaPlayer mediaPlayer;
        private String source;
        private String title;
        private int noteID = TAG.hashCode();
        private BackgroundPlayer owner;
        private NotificationManager noteMgr;
        private WifiManager.WifiLock wifiLock;
        private Bitmap videoThumbnail = null;
        private NotificationCompat.Builder noteBuilder;
        private Notification note;

        public PlayerThread(String src, String title, BackgroundPlayer owner) {
            this.source = src;
            this.title = title;
            this.owner = owner;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        @Override
        public void run() {
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);//cpu lock
            try {
                mediaPlayer.setDataSource(source);
                //We are already in a separate worker thread,
                //so calling the blocking prepare() method should be ok
                mediaPlayer.prepare();

            } catch (IOException ioe) {
                ioe.printStackTrace();
                Log.e(TAG, "video source:" + source);
                Log.e(TAG, "video title:" + title);
                //can't do anything useful without a file to play; exit early
                return;
            }

            try {
                videoThumbnail = ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail;
            } catch (Exception e) {
                Log.e(TAG, "Could not get video thumbnail from ActivityCommunicator");
                e.printStackTrace();
            }

            WifiManager wifiMgr = (WifiManager)getSystemService(Context.WIFI_SERVICE);
            wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);

            //listen for end of video
            mediaPlayer.setOnCompletionListener(new EndListener(wifiLock));

            //get audio focus
            /*
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // could not get audio focus.
            }*/
            wifiLock.acquire();
            mediaPlayer.start();

            IntentFilter filter = new IntentFilter();
            filter.setPriority(Integer.MAX_VALUE);
            filter.addAction(ACTION_PLAYPAUSE);
            filter.addAction(ACTION_STOP);
            registerReceiver(broadcastReceiver, filter);

            note = buildNotification();

            startForeground(noteID, note);

            //currently decommissioned progressbar looping update code - works, but doesn't fit inside
            //Notification.MediaStyle Notification layout.
            noteMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            /*
            //update every 2s or 4 times in the video, whichever is shorter
            int sleepTime = Math.min(2000, (int)((double)vidLength/4));
            while(mediaPlayer.isPlaying()) {
                noteBuilder.setProgress(vidLength, mediaPlayer.getCurrentPosition(), false);
                noteMgr.notify(noteID, noteBuilder.build());
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Log.d(TAG, "sleep failure");
                }
            }*/
        }

        /**Handles button presses from the notification. */
        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                //Log.i(TAG, "received broadcast action:"+action);
                if(action.equals(ACTION_PLAYPAUSE)) {
                    if(mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        note.contentView.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_play_circle_filled_white_24dp);
                        if(android.os.Build.VERSION.SDK_INT >=16){
                            note.bigContentView.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_play_circle_filled_white_24dp);
                        }
                        noteMgr.notify(noteID, note);
                    }
                    else {
                        //reacquire CPU lock after auto-releasing it on pause
                        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                        mediaPlayer.start();
                        note.contentView.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_pause_white_24dp);
                        if(android.os.Build.VERSION.SDK_INT >=16){
                            note.bigContentView.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_pause_white_24dp);
                        }
                        noteMgr.notify(noteID, note);
                    }
                }
                else if(action.equals(ACTION_STOP)) {
                    //this auto-releases CPU lock
                    mediaPlayer.stop();
                    afterPlayCleanup();
                }
            }
        };

        private void afterPlayCleanup() {
            //remove progress bar
            //noteBuilder.setProgress(0, 0, false);

            //remove notification
            noteMgr.cancel(noteID);
            unregisterReceiver(broadcastReceiver);
            //release mediaPlayer's system resources
            mediaPlayer.release();

            //release wifilock
            wifiLock.release();
            //remove foreground status of service; make BackgroundPlayer killable
            stopForeground(true);

            stopSelf();
        }

        private class EndListener implements MediaPlayer.OnCompletionListener {
            private WifiManager.WifiLock wl;
            public EndListener(WifiManager.WifiLock wifiLock) {
                this.wl = wifiLock;
            }

            @Override
            public void onCompletion(MediaPlayer mp) {
                afterPlayCleanup();
            }
        }

        private Notification buildNotification() {
            Notification note;
            Resources res = getApplicationContext().getResources();
            noteBuilder = new NotificationCompat.Builder(owner);

            PendingIntent playPI = PendingIntent.getBroadcast(owner, noteID,
                    new Intent(ACTION_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stopPI = PendingIntent.getBroadcast(owner, noteID,
                    new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
            /*
            NotificationCompat.Action pauseButton = new NotificationCompat.Action.Builder
                    (R.drawable.ic_pause_white_24dp, "Pause", playPI).build();
            */

            //build intent to return to video, on tapping notification
            Intent openDetailViewIntent = new Intent(getApplicationContext(),
                    VideoItemDetailActivity.class);
            openDetailViewIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, serviceId);
            openDetailViewIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, webUrl);
            openDetailViewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openDetailView = PendingIntent.getActivity(owner, noteID,
                    openDetailViewIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            noteBuilder
                    .setOngoing(true)
                    .setDeleteIntent(stopPI)
                            //doesn't fit with Notification.MediaStyle
                            //.setProgress(vidLength, 0, false)
                    .setSmallIcon(R.drawable.ic_play_circle_filled_white_24dp)
                    .setTicker(
                            String.format(res.getString(
                                    R.string.background_player_time_text), title))
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(),
                            noteID, openDetailViewIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .setContentIntent(openDetailView);


            RemoteViews view =
                    new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification);
            view.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
            view.setTextViewText(R.id.notificationSongName, title);
            view.setTextViewText(R.id.notificationArtist, channelName);
            view.setOnClickPendingIntent(R.id.notificationStop, stopPI);
            view.setOnClickPendingIntent(R.id.notificationPlayPause, playPI);
            view.setOnClickPendingIntent(R.id.notificationContent, openDetailView);

            //possibly found the expandedView problem,
            //but can't test it as I don't have a 5.0 device. -medavox
            RemoteViews expandedView =
                    new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded);
                expandedView.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
            expandedView.setTextViewText(R.id.notificationSongName, title);
                expandedView.setTextViewText(R.id.notificationArtist, channelName);
            expandedView.setOnClickPendingIntent(R.id.notificationStop, stopPI);
            expandedView.setOnClickPendingIntent(R.id.notificationPlayPause, playPI);
            expandedView.setOnClickPendingIntent(R.id.notificationContent, openDetailView);


            noteBuilder.setCategory(Notification.CATEGORY_TRANSPORT);

            //Make notification appear on lockscreen
            noteBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);

            note = noteBuilder.build();
            note.contentView = view;

            if (android.os.Build.VERSION.SDK_INT > 16) {
                note.bigContentView = expandedView;
            }

            return note;
        }
    }
}
