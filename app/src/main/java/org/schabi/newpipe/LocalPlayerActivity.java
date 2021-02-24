package org.schabi.newpipe;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.player.LocalPlayer;
import org.schabi.newpipe.player.LocalPlayerListener;
import org.schabi.newpipe.util.VideoSegment;

import java.util.ArrayList;
import java.util.List;

public class LocalPlayerActivity extends AppCompatActivity implements Player.EventListener,
        LocalPlayerListener {
    private LocalPlayer localPlayer;
    public static final String TAG = "LocalPlayerActivity";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_player);

        hideSystemUi(isLandscape());

        final Intent intent = getIntent();

        final String uri = parseUriFromIntent(intent);
        final VideoSegment[] segments = parseSegmentsFromIntent(intent);

        localPlayer = new LocalPlayer(this);
        localPlayer.initialize(uri, segments);

        final PlayerView playerView = findViewById(R.id.player_view);
        playerView.setPlayer(localPlayer.getExoPlayer());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        localPlayer.destroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        hideSystemUi(isLandscape());
    }

    @Override
    public void onBlocked(final SimpleExoPlayer player) {

    }

    @Override
    public void onPlaying(final SimpleExoPlayer player) {
        setKeepScreenOn(true);
    }

    @Override
    public void onBuffering(final SimpleExoPlayer player) {

    }

    @Override
    public void onPaused(final SimpleExoPlayer player) {
        setKeepScreenOn(false);
    }

    @Override
    public void onPausedSeek(final SimpleExoPlayer player) {

    }

    @Override
    public void onCompleted(final SimpleExoPlayer player) {
        setKeepScreenOn(false);
    }

    private static String parseUriFromIntent(final Intent intent) {
        return intent.getDataString();
    }

    private static VideoSegment[] parseSegmentsFromIntent(final Intent intent) {
        final List<VideoSegment> result = new ArrayList<>();

        final String segmentsJson = intent.getStringExtra("segments");

        if (segmentsJson != null && segmentsJson.length() > 0) {
            try {
                final JsonObject obj = JsonParser.object().from(segmentsJson);

                for (final Object item : obj.getArray("segments")) {
                    final JsonObject itemObject = (JsonObject) item;

                    final double startTime = itemObject.getDouble("start");
                    final double endTime = itemObject.getDouble("end");
                    final String category = itemObject.getString("category");

                    final VideoSegment segment = new VideoSegment(startTime, endTime, category);
                    result.add(segment);
                }
            } catch (final Exception e) {
                Log.e(TAG, "Error initializing segments", e);
            }
        }

        return result.toArray(new VideoSegment[0]);
    }

    private void setKeepScreenOn(final boolean keepScreenOn) {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void hideSystemUi(final boolean isLandscape) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        int visibility;

        if (isLandscape) {
            visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        } else {
            visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        if (!isInMultiWindow()) {
            visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        getWindow().getDecorView().setSystemUiVisibility(visibility);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && (isInMultiWindow())) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private boolean isInMultiWindow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode();
    }

    boolean isLandscape() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.heightPixels < metrics.widthPixels;
    }
}
