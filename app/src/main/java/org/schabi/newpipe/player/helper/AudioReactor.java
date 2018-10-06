package org.schabi.newpipe.player.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;

public class AudioReactor implements AudioManager.OnAudioFocusChangeListener,
        AudioRendererEventListener {

    private static final String TAG = "AudioFocusReactor";

    private static final boolean SHOULD_BUILD_FOCUS_REQUEST =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    private static final int DUCK_DURATION = 1500;
    private static final float DUCK_AUDIO_TO = .2f;

    private static final int FOCUS_GAIN_TYPE = AudioManager.AUDIOFOCUS_GAIN;
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final SimpleExoPlayer player;
    private final Context context;
    private final AudioManager audioManager;

    private final AudioFocusRequest request;

    public AudioReactor(@NonNull final Context context,
                        @NonNull final SimpleExoPlayer player) {
        this.player = player;
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        player.addAudioDebugListener(this);

        if (SHOULD_BUILD_FOCUS_REQUEST) {
            request = new AudioFocusRequest.Builder(FOCUS_GAIN_TYPE)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
        } else {
            request = null;
        }
    }

    public void dispose() {
        abandonAudioFocus();
        player.removeAudioDebugListener(this);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Manager
    //////////////////////////////////////////////////////////////////////////*/

    public void requestAudioFocus() {
        if (SHOULD_BUILD_FOCUS_REQUEST) {
            audioManager.requestAudioFocus(request);
        } else {
            audioManager.requestAudioFocus(this, STREAM_TYPE, FOCUS_GAIN_TYPE);
        }
    }

    public void abandonAudioFocus() {
        if (SHOULD_BUILD_FOCUS_REQUEST) {
            audioManager.abandonAudioFocusRequest(request);
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    public int getVolume() {
        return audioManager.getStreamVolume(STREAM_TYPE);
    }

    public int getMaxVolume() {
        return audioManager.getStreamMaxVolume(STREAM_TYPE);
    }

    public void setVolume(final int volume) {
        audioManager.setStreamVolume(STREAM_TYPE, volume, 0);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // AudioFocus
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange() called with: focusChange = [" + focusChange + "]");
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                onAudioFocusGain();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                onAudioFocusLossCanDuck();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                onAudioFocusLoss();
                break;
        }
    }

    private void onAudioFocusGain() {
        Log.d(TAG, "onAudioFocusGain() called");
        player.setVolume(DUCK_AUDIO_TO);
        animateAudio(DUCK_AUDIO_TO, 1f);

        if (PlayerHelper.isResumeAfterAudioFocusGain(context)) {
            player.setPlayWhenReady(true);
        }
    }

    private void onAudioFocusLoss() {
        Log.d(TAG, "onAudioFocusLoss() called");
        player.setPlayWhenReady(false);
    }

    private void onAudioFocusLossCanDuck() {
        Log.d(TAG, "onAudioFocusLossCanDuck() called");
        // Set the volume to 1/10 on ducking
        animateAudio(player.getVolume(), DUCK_AUDIO_TO);
    }

    private void animateAudio(final float from, final float to) {
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setFloatValues(from, to);
        valueAnimator.setDuration(AudioReactor.DUCK_DURATION);
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                player.setVolume(from);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                player.setVolume(to);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                player.setVolume(to);
            }
        });
        valueAnimator.addUpdateListener(animation ->
                player.setVolume(((float) animation.getAnimatedValue())));
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioSessionId(int i) {
        if (!PlayerHelper.isUsingDSP(context)) return;

        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, i);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    public void onAudioEnabled(DecoderCounters decoderCounters) {}

    @Override
    public void onAudioDecoderInitialized(String s, long l, long l1) {}

    @Override
    public void onAudioInputFormatChanged(Format format) {}

    @Override
    public void onAudioSinkUnderrun(int bufferSize,
                                    long bufferSizeMs,
                                    long elapsedSinceLastFeedMs) {}

    @Override
    public void onAudioDisabled(DecoderCounters decoderCounters) {}
}
