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
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.detail.VideoItemDetailFragment;

import java.io.IOException;
import java.util.Arrays;

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

    private static final String TAG = "BackgroundPlayer";
    private static final String CLASSNAME = "org.schabi.newpipe.player.BackgroundPlayer";
    private static final String ACTION_STOP = CLASSNAME + ".STOP";
    private static final String ACTION_PLAYPAUSE = CLASSNAME + ".PLAYPAUSE";
    private static final String ACTION_REWIND = CLASSNAME + ".REWIND";
    private static final String ACTION_PLAYBACK_STATE = CLASSNAME + ".PLAYBACK_STATE";
    private static final String EXTRA_PLAYBACK_STATE = CLASSNAME + ".extras.EXTRA_PLAYBACK_STATE";

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
    public static volatile boolean isRunning;

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
        private Bitmap videoThumbnail;
        private NoteBuilder noteBuilder;
        private volatile boolean donePlaying = false;

        public PlayerThread(String src, String title, BackgroundPlayer owner) {
            this.source = src;
            this.title = title;
            this.owner = owner;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        public boolean isDonePlaying() {
            return donePlaying;
        }

        private boolean isPlaying() {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Unable to retrieve playing state", e);
                return false;
            }
        }

        private void setDonePlaying() {
            donePlaying = true;
            synchronized (PlayerThread.this) {
                PlayerThread.this.notifyAll();
            }
        }

        private PlaybackState getPlaybackState() {
            try {
                return new PlaybackState(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), isPlaying());
            } catch (IllegalStateException e) {
                // This isn't that nice way to handle this.
                // maybe there is a better way
                return PlaybackState.UNPREPARED;
            }
        }

        private void broadcastState() {
            PlaybackState state = getPlaybackState();
            Intent intent = new Intent(ACTION_PLAYBACK_STATE);
            intent.putExtra(EXTRA_PLAYBACK_STATE, state);
            sendBroadcast(intent);
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
            filter.addAction(ACTION_REWIND);
            filter.addAction(ACTION_PLAYBACK_STATE);
            registerReceiver(broadcastReceiver, filter);

            initNotificationBuilder();
            startForeground(noteID, noteBuilder.build());

            //currently decommissioned progressbar looping update code - works, but doesn't fit inside
            //Notification.MediaStyle Notification layout.
            noteMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

            //update every 2s or 4 times in the video, whichever is shorter
            int vidLength = mediaPlayer.getDuration();
            int sleepTime = Math.min(2000, (int)(vidLength / 4));
            while(!isDonePlaying()) {
                broadcastState();
                try {
                    synchronized (this) {
                        wait(sleepTime);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "sleep failure", e);
                }
            }
        }

        /**Handles button presses from the notification. */
        private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                //Log.i(TAG, "received broadcast action:"+action);
                switch (action) {
                    case ACTION_PLAYPAUSE: {
                        boolean isPlaying = mediaPlayer.isPlaying();
                        if(isPlaying) {
                            mediaPlayer.pause();
                        } else {
                            //reacquire CPU lock after auto-releasing it on pause
                            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                            mediaPlayer.start();
                        }
                        noteBuilder.setIsPlaying(isPlaying);
                        noteMgr.notify(noteID, noteBuilder.build());
                        break;
                    }
                    case ACTION_REWIND:
                        mediaPlayer.seekTo(0);
                        synchronized (PlayerThread.this) {
                            PlayerThread.this.notifyAll();
                        }
//                    noteMgr.notify(noteID, note);
                        break;
                    case ACTION_STOP:
                        //this auto-releases CPU lock
                        mediaPlayer.stop();
                        afterPlayCleanup();
                        break;
                    case ACTION_PLAYBACK_STATE: {
                        PlaybackState playbackState = intent.getParcelableExtra(EXTRA_PLAYBACK_STATE);
                        Log.d(TAG, "playback state recieved: " + playbackState);
                        Log.d(TAG, "is unprepared: " + playbackState.equals(PlaybackState.UNPREPARED));
                        Log.d(TAG, "playing: " + playbackState.getPlayedTime());
                        if(!playbackState.equals(PlaybackState.UNPREPARED)) {
                            noteBuilder.setProgress(playbackState.getDuration(), playbackState.getPlayedTime(), false);
                            noteBuilder.setIsPlaying(playbackState.isPlaying());
                        } else {
                            noteBuilder.setProgress(0, 0, true);
                        }
                        noteMgr.notify(noteID, noteBuilder.build());
                        break;
                    }
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
                setDonePlaying();
                afterPlayCleanup();

            }
        }

        private void initNotificationBuilder() {
            Notification note;
            Resources res = getApplicationContext().getResources();

            /*
            NotificationCompat.Action pauseButton = new NotificationCompat.Action.Builder
                    (R.drawable.ic_pause_white_24dp, "Pause", playPI).build();
            */

            PendingIntent playPI = PendingIntent.getBroadcast(owner, noteID,
                    new Intent(ACTION_PLAYPAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stopPI = PendingIntent.getBroadcast(owner, noteID,
                    new Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent rewindPI = PendingIntent.getBroadcast(owner, noteID,
                    new Intent(ACTION_REWIND), PendingIntent.FLAG_UPDATE_CURRENT);

            //build intent to return to video, on tapping notification
            Intent openDetailViewIntent = new Intent(getApplicationContext(),
                    VideoItemDetailActivity.class);
            openDetailViewIntent.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, serviceId);
            openDetailViewIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, webUrl);
            openDetailViewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openDetailView = PendingIntent.getActivity(owner, noteID,
                    openDetailViewIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            noteBuilder = new NoteBuilder(owner, playPI, stopPI, rewindPI, openDetailView);
            noteBuilder
                    .setTitle(title)
                    .setArtist(channelName)
                    .setOngoing(true)
                    .setDeleteIntent(stopPI)
                            //doesn't fit with Notification.MediaStyle
                            //.setProgress(vidLength, 0, false)
                    .setSmallIcon(R.drawable.ic_play_circle_filled_white_24dp)
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(),
                            noteID, openDetailViewIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .setContentIntent(openDetailView)
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    //Make notification appear on lockscreen
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
        }


        /**
         * Notification builder which works like the real builder but uses a custom view.
         */
        class NoteBuilder extends NotificationCompat.Builder {

            /**
             * @param context
             * @inheritDoc
             */
            public NoteBuilder(Context context, PendingIntent playPI, PendingIntent stopPI,
                               PendingIntent rewindPI, PendingIntent openDetailView) {
                super(context);
                setCustomContentView(createCustomContentView(playPI, stopPI, rewindPI, openDetailView));
                setCustomBigContentView(createCustomBigContentView(playPI, stopPI, rewindPI, openDetailView));
            }

            private RemoteViews createCustomBigContentView(PendingIntent playPI,
                                                           PendingIntent stopPI,
                                                           PendingIntent rewindPI,
                                                           PendingIntent openDetailView) {
                //possibly found the expandedView problem,
                //but can't test it as I don't have a 5.0 device. -medavox
                RemoteViews expandedView =
                        new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded);
                expandedView.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
                expandedView.setOnClickPendingIntent(R.id.notificationStop, stopPI);
                expandedView.setOnClickPendingIntent(R.id.notificationPlayPause, playPI);
                expandedView.setOnClickPendingIntent(R.id.notificationRewind, rewindPI);
                expandedView.setOnClickPendingIntent(R.id.notificationContent, openDetailView);
                return expandedView;
            }

            private RemoteViews createCustomContentView(PendingIntent playPI, PendingIntent stopPI,
                                                        PendingIntent rewindPI,
                                                        PendingIntent openDetailView) {
                RemoteViews view = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification);
                view.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
                view.setOnClickPendingIntent(R.id.notificationStop, stopPI);
                view.setOnClickPendingIntent(R.id.notificationPlayPause, playPI);
                view.setOnClickPendingIntent(R.id.notificationRewind, rewindPI);
                view.setOnClickPendingIntent(R.id.notificationContent, openDetailView);
                return view;
            }

            /**
             * Set the title of the stream
             * @param title the title of the stream
             * @return this builder for chaining
             */
            NoteBuilder setTitle(String title) {
                setContentTitle(title);
                getContentView().setTextViewText(R.id.notificationSongName, title);
                getBigContentView().setTextViewText(R.id.notificationSongName, title);
                setTicker(String.format(getBaseContext().getString(
                        R.string.background_player_time_text), title));
                return this;
            }

            /**
             * Set the artist of the stream
             * @param artist the artist of the stream
             * @return this builder for chaining
             */
            NoteBuilder setArtist(String artist) {
                setSubText(artist);
                getContentView().setTextViewText(R.id.notificationArtist, artist);
                getBigContentView().setTextViewText(R.id.notificationArtist, artist);
                return this;
            }

            @Override
            public android.support.v4.app.NotificationCompat.Builder setProgress(int max, int progress, boolean indeterminate) {
                super.setProgress(max, progress, indeterminate);
                getBigContentView().setProgressBar(R.id.playbackProgress, max, progress, indeterminate);
                return this;
            }

            /**
             * Set the isPlaying state
             * @param isPlaying the is playing state
             */
            public void setIsPlaying(boolean isPlaying) {
                RemoteViews views = getContentView(), bigViews = getBigContentView();
                int imageSrc;
                if(isPlaying) {
                    imageSrc = R.drawable.ic_pause_white_24dp;
                } else {
                    imageSrc = R.drawable.ic_play_circle_filled_white_24dp;
                }
                views.setImageViewResource(R.id.notificationPlayPause, imageSrc);
                bigViews.setImageViewResource(R.id.notificationPlayPause, imageSrc);

            }

        }
    }

    /**
     * Represents the state of the player.
     */
    public static class PlaybackState implements Parcelable {

        private static final int INDEX_IS_PLAYING = 0;
        private static final int INDEX_IS_PREPARED= 1;
        private static final int INDEX_HAS_ERROR  = 2;
        private final int duration;
        private final int played;
        private final boolean[] booleanValues = new boolean[3];

        static final PlaybackState UNPREPARED = new PlaybackState(false, false, false);
        static final PlaybackState FAILED = new PlaybackState(false, false, true);


        PlaybackState(Parcel in) {
            duration = in.readInt();
            played = in.readInt();
            in.readBooleanArray(booleanValues);
        }

        PlaybackState(int duration, int played, boolean isPlaying) {
            this.played = played;
            this.duration = duration;
            this.booleanValues[INDEX_IS_PLAYING] = isPlaying;
            this.booleanValues[INDEX_IS_PREPARED] = true;
            this.booleanValues[INDEX_HAS_ERROR] = false;
        }

        private PlaybackState(boolean isPlaying, boolean isPrepared, boolean hasErrors) {
            this.played = 0;
            this.duration = 0;
            this.booleanValues[INDEX_IS_PLAYING] = isPlaying;
            this.booleanValues[INDEX_IS_PREPARED] = isPrepared;
            this.booleanValues[INDEX_HAS_ERROR] = hasErrors;
        }

        int getDuration() {
            return duration;
        }

        int getPlayedTime() {
            return played;
        }

        boolean isPlaying() {
            return booleanValues[INDEX_IS_PLAYING];
        }

        boolean isPrepared() {
            return booleanValues[INDEX_IS_PREPARED];
        }

        boolean hasErrors() {
            return booleanValues[INDEX_HAS_ERROR];
        }


        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(duration);
            dest.writeInt(played);
            dest.writeBooleanArray(booleanValues);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PlaybackState> CREATOR = new Creator<PlaybackState>() {
            @Override
            public PlaybackState createFromParcel(Parcel in) {
                return new PlaybackState(in);
            }

            @Override
            public PlaybackState[] newArray(int size) {
                return new PlaybackState[size];
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PlaybackState that = (PlaybackState) o;

            if (duration != that.duration) return false;
            if (played != that.played) return false;
            return Arrays.equals(booleanValues, that.booleanValues);

        }

        @Override
        public int hashCode() {
            if(this == UNPREPARED) return 1;
            if(this == FAILED) return 2;
            int result = duration;
            result = 31 * result + played;
            result = 31 * result + Arrays.hashCode(booleanValues);
            return result + 2;
        }
    }
}
