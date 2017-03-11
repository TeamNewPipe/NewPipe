package org.schabi.newpipe.detail;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.NavStack;
import org.schabi.newpipe.util.ThemeHelper;


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
        ThemeHelper.setTheme(this, true);
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

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            videoUrl = savedInstanceState.getString(NavStack.URL);
            currentStreamingService = savedInstanceState.getInt(NavStack.SERVICE_ID);
            NavStack.getInstance()
                    .restoreSavedInstanceState(savedInstanceState);
            addFragment(savedInstanceState);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Bundle arguments = new Bundle();
        boolean autoplay = false;

        videoUrl = intent.getStringExtra(NavStack.URL);
        currentStreamingService = intent.getIntExtra(NavStack.SERVICE_ID, -1);
        if(intent.hasExtra(VideoItemDetailFragment.AUTO_PLAY)) {
            arguments.putBoolean(VideoItemDetailFragment.AUTO_PLAY,
                    intent.getBooleanExtra(VideoItemDetailFragment.AUTO_PLAY, false));
        }
        arguments.putString(NavStack.URL, videoUrl);
        arguments.putInt(NavStack.SERVICE_ID, currentStreamingService);
        addFragment(arguments);
    }

    private void addFragment(final Bundle arguments) {
        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        fragment = new VideoItemDetailFragment();
        fragment.setArguments(arguments);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.videoitem_detail_container, fragment)
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        App.checkStartTor(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(NavStack.URL, videoUrl);
        outState.putInt(NavStack.SERVICE_ID, currentStreamingService);
        outState.putBoolean(VideoItemDetailFragment.AUTO_PLAY, false);
        NavStack.getInstance()
                .onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:

            // http://developer.android.com/design/patterns/navigation.html#up-vs-back

            NavStack.getInstance()
                    .openMainActivity(this);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            NavStack.getInstance()
                    .navBack(this);
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }
}
