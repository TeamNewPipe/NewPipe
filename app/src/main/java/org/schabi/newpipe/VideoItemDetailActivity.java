package org.schabi.newpipe;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;


/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * VideoItemDetailActivity.java is part of NewPipe.
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

public class VideoItemDetailActivity extends AppCompatActivity {

    private static final String TAG = VideoItemDetailActivity.class.toString();

    private VideoItemDetailFragment fragment;

    private String videoUrl;
    private int currentStreamingService = -1;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videoitem_detail);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Show the Up button in the action bar.
        try {
            //noinspection ConstantConditions
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch(Exception e) {
            Log.d(TAG, "Could not get SupportActionBar");
            e.printStackTrace();
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //

        Bundle arguments = new Bundle();
        if (savedInstanceState == null) {
            // this means the video was called though another app
            if (getIntent().getData() != null) {
                videoUrl = getIntent().getData().toString();
                StreamingService[] serviceList = ServiceList.getServices();
                //StreamExtractor videoExtractor = null;
                for (int i = 0; i < serviceList.length; i++) {
                    if (serviceList[i].getUrlIdHandlerInstance().acceptUrl(videoUrl)) {
                        arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, i);
                        currentStreamingService = i;
                        //videoExtractor = ServiceList.getService(i).getExtractorInstance();
                        break;
                    }
                }
                if(currentStreamingService == -1) {
                    Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG)
                            .show();
                }
                //arguments.putString(VideoItemDetailFragment.VIDEO_URL,
                //        videoExtractor.getVideoUrl(videoExtractor.getVideoId(videoUrl)));//cleans URL
                arguments.putString(VideoItemDetailFragment.VIDEO_URL, videoUrl);

                arguments.putBoolean(VideoItemDetailFragment.AUTO_PLAY,
                        PreferenceManager.getDefaultSharedPreferences(this)
                                .getBoolean(getString(R.string.autoplay_through_intent_key), false));
            } else {
                videoUrl = getIntent().getStringExtra(VideoItemDetailFragment.VIDEO_URL);
                currentStreamingService = getIntent().getIntExtra(VideoItemDetailFragment.STREAMING_SERVICE, -1);
                arguments.putString(VideoItemDetailFragment.VIDEO_URL, videoUrl);
                arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingService);
                arguments.putBoolean(VideoItemDetailFragment.AUTO_PLAY, false);
            }

        } else {
            videoUrl = savedInstanceState.getString(VideoItemDetailFragment.VIDEO_URL);
            currentStreamingService = savedInstanceState.getInt(VideoItemDetailFragment.STREAMING_SERVICE);
            arguments = savedInstanceState;
        }

        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        fragment = new VideoItemDetailFragment();
        fragment.setArguments(arguments);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.videoitem_detail_container, fragment)
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        App.checkStartTor(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(VideoItemDetailFragment.VIDEO_URL, videoUrl);
        outState.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingService);
        outState.putBoolean(VideoItemDetailFragment.AUTO_PLAY, false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:

            // http://developer.android.com/design/patterns/navigation.html#up-vs-back

            Intent intent = new Intent(this, VideoItemListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            NavUtils.navigateUpTo(this, intent);
            return true;
        } else {
            return fragment.onOptionsItemSelected(item) ||
                    super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        fragment.onCreateOptionsMenu(menu, getMenuInflater());
        return true;
    }
}
