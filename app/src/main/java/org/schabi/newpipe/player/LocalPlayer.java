package org.schabi.newpipe.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.VideoSegment;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.schabi.newpipe.player.Player.STATE_BLOCKED;
import static org.schabi.newpipe.player.Player.STATE_BUFFERING;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED_SEEK;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;

public class LocalPlayer implements EventListener {
    private static final String TAG = "LocalPlayer";
    private static final int PROGRESS_LOOP_INTERVAL_MILLIS = 500;

    private final Context context;
    private final SharedPreferences mPrefs;
    private SimpleExoPlayer simpleExoPlayer;
    private SerialDisposable progressUpdateReactor;
    private VideoSegment[] videoSegments;
    private LocalPlayerListener listener;
    private int lastCurrentProgress = -1;

    public LocalPlayer(final Context context) {
        this.context = context;
        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void initialize(final String uri, final VideoSegment[] segments) {
        this.videoSegments = segments;
        this.progressUpdateReactor = new SerialDisposable();

        simpleExoPlayer = new SimpleExoPlayer
                .Builder(context)
                .build();
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));
        simpleExoPlayer.setHandleAudioBecomingNoisy(true);
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);

        final PlaybackParameters playbackParameters = simpleExoPlayer.getPlaybackParameters();
        final float speed = mPrefs.getFloat(context.getString(
                R.string.playback_speed_key), playbackParameters.speed);
        final float pitch = mPrefs.getFloat(context.getString(
                R.string.playback_pitch_key), playbackParameters.pitch);
        final boolean skipSilence = mPrefs.getBoolean(context.getString(
                R.string.playback_skip_silence_key), playbackParameters.skipSilence);

        setPlaybackParameters(speed, pitch, skipSilence);

        final String autoPlayStr =
                mPrefs.getString(context.getString(R.string.autoplay_key), "");
        final boolean autoPlay =
                !autoPlayStr.equals(context.getString(R.string.autoplay_never_key));

        simpleExoPlayer.setPlayWhenReady(autoPlay);

        if (uri == null || uri.length() == 0) {
            return;
        }

        final MediaSource videoSource = new ProgressiveMediaSource
                .Factory(new DefaultDataSourceFactory(context, DownloaderImpl.USER_AGENT))
                .createMediaSource(Uri.parse(uri));
        simpleExoPlayer.prepare(videoSource);
    }

    public SimpleExoPlayer getExoPlayer() {
        return this.simpleExoPlayer;
    }

    public void setListener(final LocalPlayerListener listener) {
        this.listener = listener;
    }

    public void destroy() {
        simpleExoPlayer.removeListener(this);
        simpleExoPlayer.stop();
        simpleExoPlayer.release();
        progressUpdateReactor.set(null);
    }

    public void setPlaybackParameters(final float speed, final float pitch,
                                      final boolean skipSilence) {
        final float roundedSpeed = Math.round(speed * 100.0f) / 100.0f;
        final float roundedPitch = Math.round(pitch * 100.0f) / 100.0f;

        mPrefs.edit()
                .putFloat(context.getString(R.string.playback_speed_key), speed)
                .putFloat(context.getString(R.string.playback_pitch_key), pitch)
                .putBoolean(context.getString(R.string.playback_skip_silence_key), skipSilence)
                .apply();
        simpleExoPlayer.setPlaybackParameters(
                new PlaybackParameters(roundedSpeed, roundedPitch, skipSilence));
    }

    @Override
    public void onPlayerStateChanged(final boolean playWhenReady, final int playbackState) {
        switch (playbackState) {
            case com.google.android.exoplayer2.Player.STATE_IDLE:
                break;
            case com.google.android.exoplayer2.Player.STATE_BUFFERING:
                break;
            case com.google.android.exoplayer2.Player.STATE_READY:
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case com.google.android.exoplayer2.Player.STATE_ENDED:
                changeState(STATE_COMPLETED);
                break;
        }
    }

    private boolean isProgressLoopRunning() {
        return progressUpdateReactor.get() != null;
    }

    private void startProgressLoop() {
        progressUpdateReactor.set(getProgressReactor());
    }

    private void stopProgressLoop() {
        progressUpdateReactor.set(null);
    }

    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
    }

    private void changeState(final int state) {
        switch (state) {
            case STATE_BLOCKED:
                onBlocked();
                break;
            case STATE_PLAYING:
                onPlaying();
                break;
            case STATE_BUFFERING:
                onBuffering();
                break;
            case STATE_PAUSED:
                onPaused();
                break;
            case STATE_PAUSED_SEEK:
                onPausedSeek();
                break;
            case STATE_COMPLETED:
                onCompleted();
                break;
        }
    }

    private void onBlocked() {
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        if (listener != null) {
            listener.onBlocked(simpleExoPlayer);
        }
    }

    private void onPlaying() {
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        if (listener != null) {
            listener.onPlaying(simpleExoPlayer);
        }
    }

    private void onBuffering() {
        if (listener != null) {
            listener.onBuffering(simpleExoPlayer);
        }
    }

    private void onPaused() {
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        if (listener != null) {
            listener.onPaused(simpleExoPlayer);
        }
    }

    private void onPausedSeek() {
        if (listener != null) {
            listener.onPausedSeek(simpleExoPlayer);
        }
    }

    private void onCompleted() {
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        if (listener != null) {
            listener.onCompleted(simpleExoPlayer);
        }
    }

    private void triggerProgressUpdate() {
        if (simpleExoPlayer == null) {
            return;
        }
        final int currentProgress = Math.max((int) simpleExoPlayer.getCurrentPosition(), 0);

        final boolean isRewind = currentProgress < lastCurrentProgress;

        lastCurrentProgress = currentProgress;

        if (!mPrefs.getBoolean(
                context.getString(R.string.sponsor_block_enable_key), false)) {
            return;
        }

        final VideoSegment segment = getSkippableSegment(currentProgress);
        if (segment == null) {
            return;
        }

        int skipTarget = isRewind
                ? (int) Math.ceil((segment.startTime)) - 1
                : (int) Math.ceil((segment.endTime));

        if (skipTarget < 0) {
            skipTarget = 0;
        }

        // temporarily force EXACT seek parameters to prevent infinite skip looping
        final SeekParameters seekParams = simpleExoPlayer.getSeekParameters();
        simpleExoPlayer.setSeekParameters(SeekParameters.EXACT);

        seekTo(skipTarget);

        simpleExoPlayer.setSeekParameters(seekParams);

        if (mPrefs.getBoolean(
                context.getString(R.string.sponsor_block_notifications_key), false)) {
            String toastText = "";

            switch (segment.category) {
                case "sponsor":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_sponsor_toast);
                    break;
                case "intro":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_intro_toast);
                    break;
                case "outro":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_outro_toast);
                    break;
                case "interaction":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_interaction_toast);
                    break;
                case "selfpromo":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_self_promo_toast);
                    break;
                case "music_offtopic":
                    toastText = context
                            .getString(R.string.sponsor_block_skip_non_music_toast);
                    break;
            }

            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
        }
    }

    private void seekTo(final long positionMillis) {
        if (simpleExoPlayer != null) {
            long normalizedPositionMillis = positionMillis;
            if (normalizedPositionMillis < 0) {
                normalizedPositionMillis = 0;
            } else if (normalizedPositionMillis > simpleExoPlayer.getDuration()) {
                normalizedPositionMillis = simpleExoPlayer.getDuration();
            }

            simpleExoPlayer.seekTo(normalizedPositionMillis);
        }
    }

    private VideoSegment getSkippableSegment(final int progress) {
        if (videoSegments == null) {
            return null;
        }

        for (final VideoSegment segment : videoSegments) {
            if (progress < segment.startTime) {
                continue;
            }

            if (progress > segment.endTime) {
                continue;
            }

            return segment;
        }

        return null;
    }
}
