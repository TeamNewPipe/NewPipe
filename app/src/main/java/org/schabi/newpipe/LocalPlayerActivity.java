package org.schabi.newpipe;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.VideoSegment;

import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.schabi.newpipe.player.BasePlayer.STATE_BLOCKED;
import static org.schabi.newpipe.player.BasePlayer.STATE_BUFFERING;
import static org.schabi.newpipe.player.BasePlayer.STATE_COMPLETED;
import static org.schabi.newpipe.player.BasePlayer.STATE_PAUSED;
import static org.schabi.newpipe.player.BasePlayer.STATE_PAUSED_SEEK;
import static org.schabi.newpipe.player.BasePlayer.STATE_PLAYING;

public class LocalPlayerActivity extends AppCompatActivity implements Player.EventListener {
    private SimpleExoPlayer simpleExoPlayer;
    private SerialDisposable progressUpdateReactor;
    private int lastCurrentProgress = -1;
    protected static final int PROGRESS_LOOP_INTERVAL_MILLIS = 500;
    public static final String TAG = "LocalPlayer";
    private VideoSegment[] segments;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_player);

        this.mPrefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());

        initPlayer();
        initSegments();
    }

    private void initPlayer() {
        final String uri = getIntent().getDataString();

        if (uri == null) {
            return;
        }

        simpleExoPlayer = new SimpleExoPlayer
                .Builder(this)
                .build();
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(this));
        simpleExoPlayer.setHandleAudioBecomingNoisy(true);
        final MediaSource videoSource = new ProgressiveMediaSource
                .Factory(new DefaultDataSourceFactory(this, DownloaderImpl.USER_AGENT))
                .createMediaSource(Uri.parse(uri));
        simpleExoPlayer.prepare(videoSource);
        final PlayerView playerView = findViewById(R.id.player_view);
        playerView.setPlayer(simpleExoPlayer);

        this.progressUpdateReactor = new SerialDisposable();
    }

    private void initSegments() {
        final String segmentsJson = getIntent().getStringExtra("segments");

        if (segmentsJson != null && segmentsJson.length() > 0) {
            try {
                final ArrayList<VideoSegment> segmentsArrayList = new ArrayList<>();
                final JsonObject obj = JsonParser.object().from(segmentsJson);

                for (final Object item : obj.getArray("segments")) {
                    final JsonObject itemObject = (JsonObject) item;

                    final double startTime = itemObject.getDouble("start");
                    final double endTime = itemObject.getDouble("end");
                    final String category = itemObject.getString("category");

                    final VideoSegment segment = new VideoSegment(startTime, endTime, category);
                    segmentsArrayList.add(segment);
                }

                segments = segmentsArrayList.toArray(new VideoSegment[0]);
            } catch (final Exception e) {
                Log.e(TAG, "Error initializing segments", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        simpleExoPlayer.removeListener(this);
        simpleExoPlayer.stop();
        simpleExoPlayer.release();
        progressUpdateReactor.set(null);
    }

    @Override
    public void onPlayerStateChanged(final boolean playWhenReady, final int playbackState) {
        switch (playbackState) {
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                break;
            case Player.STATE_READY:
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case Player.STATE_ENDED:
                changeState(STATE_COMPLETED);
                break;
        }
    }

    public void changeState(final int state) {
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
    }

    private void onPlaying() {
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    private void onBuffering() {
    }

    private void onPaused() {
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
    }

    private void onPausedSeek() {
    }

    private void onCompleted() {
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
    }

    public boolean isProgressLoopRunning() {
        return progressUpdateReactor.get() != null;
    }

    protected void startProgressLoop() {
        progressUpdateReactor.set(getProgressReactor());
    }

    protected void stopProgressLoop() {
        progressUpdateReactor.set(null);
    }

    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
    }

    private void triggerProgressUpdate() {
        if (simpleExoPlayer == null) {
            return;
        }
        final int currentProgress = Math.max((int) simpleExoPlayer.getCurrentPosition(), 0);

        final boolean isRewind = currentProgress < lastCurrentProgress;

        lastCurrentProgress = currentProgress;

        if (!mPrefs.getBoolean(
                this.getString(R.string.sponsor_block_enable_key), false)) {
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
                this.getString(R.string.sponsor_block_notifications_key), false)) {
            String toastText = "";

            switch (segment.category) {
                case "sponsor":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_sponsor_toast);
                    break;
                case "intro":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_intro_toast);
                    break;
                case "outro":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_outro_toast);
                    break;
                case "interaction":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_interaction_toast);
                    break;
                case "selfpromo":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_self_promo_toast);
                    break;
                case "music_offtopic":
                    toastText = this
                            .getString(R.string.sponsor_block_skip_non_music_toast);
                    break;
            }

            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
        }
    }

    public void seekTo(final long positionMillis) {
        if (simpleExoPlayer != null) {
            // prevent invalid positions when fast-forwarding/-rewinding
            long normalizedPositionMillis = positionMillis;
            if (normalizedPositionMillis < 0) {
                normalizedPositionMillis = 0;
            } else if (normalizedPositionMillis > simpleExoPlayer.getDuration()) {
                normalizedPositionMillis = simpleExoPlayer.getDuration();
            }

            simpleExoPlayer.seekTo(normalizedPositionMillis);
        }
    }

    public VideoSegment getSkippableSegment(final int progress) {
        if (segments == null) {
            return null;
        }

        for (final VideoSegment segment : segments) {
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
