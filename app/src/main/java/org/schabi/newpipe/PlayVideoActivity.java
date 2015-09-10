package org.schabi.newpipe;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * PlayVideoActivity.java is part of NewPipe.
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

public class PlayVideoActivity extends AppCompatActivity {

    private static final String TAG = PlayVideoActivity.class.toString();
    public static final String VIDEO_URL = "video_url";
    public static final String STREAM_URL = "stream_url";
    public static final String VIDEO_TITLE = "video_title";
    private static final String POSITION = "position";

    private static final long HIDING_DELAY = 3000;

    private String videoUrl = "";

    private ActionBar actionBar;
    private VideoView videoView;
    private int position = 0;
    private MediaController mediaController;
    private ProgressBar progressBar;
    private View decorView;
    private boolean uiIsHidden = false;
    private static long lastUiShowTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_play_video);

        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        Intent intent = getIntent();
        if(mediaController == null) {
            mediaController = new MediaController(this);
        }

        videoView = (VideoView) findViewById(R.id.video_view);
        progressBar = (ProgressBar) findViewById(R.id.play_video_progress_bar);
        try {
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.parse(intent.getStringExtra(STREAM_URL)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        videoView.requestFocus();
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                progressBar.setVisibility(View.GONE);
                videoView.seekTo(position);
                if (position == 0) {
                    videoView.start();
                } else {
                    videoView.pause();
                }
            }
        });
        videoUrl = intent.getStringExtra(VIDEO_URL);

        Button button = (Button) findViewById(R.id.content_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(uiIsHidden) {
                    showUi();
                } else {
                    hideUi();
                }
            }
        });
        decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    uiIsHidden = false;
                    showUi();
                } else {
                    uiIsHidden = true;
                    hideUi();
                }
            }
        });
        showUi();
    }

    @Override
    public boolean onCreatePanelMenu(int featured, Menu menu) {
        super.onCreatePanelMenu(featured, menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.video_player, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_item_share:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, videoUrl);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.shareDialogTitle)));
                break;
            case R.id.menu_item_screen_rotation:
                Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
                if(display.getRotation() == Surface.ROTATION_0
                        || display.getRotation() == Surface.ROTATION_180) {
                    setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else if(display.getRotation() == Surface.ROTATION_90
                        || display.getRotation() == Surface.ROTATION_270) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                break;
            default:
                Log.e(TAG, "Error: MenuItem not known");
                return false;
        }
        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        //savedInstanceState.putInt(POSITION, videoView.getCurrentPosition());
        //videoView.pause();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        position = savedInstanceState.getInt(POSITION);
        //videoView.seekTo(position);
    }

    private void showUi() {
        try {
            uiIsHidden = false;
            mediaController.show();
            actionBar.show();
            //decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if ((System.currentTimeMillis() - lastUiShowTime) > HIDING_DELAY) {
                        hideUi();
                    }
                }
            }, HIDING_DELAY);
            lastUiShowTime = System.currentTimeMillis();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void hideUi() {
        uiIsHidden = true;
        actionBar.hide();
        mediaController.hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        //| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}
