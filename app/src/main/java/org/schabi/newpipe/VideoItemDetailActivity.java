package org.schabi.newpipe;

import android.content.ContentProviderOperation;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.schabi.newpipe.youtube.YoutubeExtractor;


/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ActionBarHandler.java is part of NewPipe.
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

    private String videoUrl;
    private int currentStreamingService = -1;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videoitem_detail);


        // Show the Up button in the action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ActionBarHandler.getHandler().setupNavMenu(this);

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
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            if (getIntent().getData() != null) {
                videoUrl = getIntent().getData().toString();
                StreamingService[] serviceList = ServiceList.getServices();
                Extractor extractor = null;
                for (int i = 0; i < serviceList.length; i++) {
                    if (serviceList[i].acceptUrl(videoUrl)) {
                        arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, i);
                        try {
                            currentStreamingService = i;
                            extractor = (Extractor) ServiceList.getService(i)
                                    .getExtractorClass().newInstance();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
                arguments.putString(VideoItemDetailFragment.VIDEO_URL,
                        extractor.getVideoUrl(extractor.getVideoId(videoUrl)));

            } else {
                videoUrl = getIntent().getStringExtra(VideoItemDetailFragment.VIDEO_URL);
                currentStreamingService = getIntent().getIntExtra(VideoItemDetailFragment.STREAMING_SERVICE, -1);
                arguments.putString(VideoItemDetailFragment.VIDEO_URL, videoUrl);
                arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingService);
            }
            VideoItemDetailFragment fragment = new VideoItemDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.videoitem_detail_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            Intent intent = new Intent(this, VideoItemListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            NavUtils.navigateUpTo(this, intent);
            return true;
        } else {
            ActionBarHandler.getHandler().onItemSelected(item, this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreatePanelMenu(int featured, Menu menu) {
        super.onCreatePanelMenu(featured, menu);
        MenuInflater inflater = getMenuInflater();
        ActionBarHandler.getHandler().setupMenu(menu, inflater, this);

        return true;
    }
}
