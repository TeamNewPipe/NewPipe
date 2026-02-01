package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.AudioManagerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;

public class AudioReactor implements AnalyticsListener {

    private static final String TAG = "AudioFocusReactor";

    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final ExoPlayer player;
    private final Context context;
    private final AudioManager audioManager;

    public AudioReactor(@NonNull final Context context,
                        @NonNull final ExoPlayer player) {
        this.player = player;
        this.context = context;
        this.audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        player.addAnalyticsListener(this);
    }

    public void dispose() {
        player.removeAnalyticsListener(this);
        notifyAudioSessionUpdate(false, player.getAudioSessionId());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Manager
    //////////////////////////////////////////////////////////////////////////*/

    public int getVolume() {
        return audioManager.getStreamVolume(STREAM_TYPE);
    }

    public void setVolume(final int volume) {
        audioManager.setStreamVolume(STREAM_TYPE, volume, 0);
    }

    public int getMaxVolume() {
        return AudioManagerCompat.getStreamMaxVolume(audioManager, STREAM_TYPE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioSessionIdChanged(@NonNull final EventTime eventTime,
                                        final int audioSessionId) {
        notifyAudioSessionUpdate(true, audioSessionId);
    }
    private void notifyAudioSessionUpdate(final boolean active, final int audioSessionId) {
        final Intent intent = new Intent(active
                ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }
}
