package org.schabi.newpipe.detail;

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

import org.schabi.newpipe.App;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.Themer;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;

import java.util.Collection;
import java.util.HashSet;


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

public class VideoItemDetailActivity extends Themer {

    /**
     * Removes invisible separators (\p{Z}) and punctuation characters including
     * brackets (\p{P}). See http://www.regular-expressions.info/unicode.html for
     * more details.
     */
    private final static String REGEX_REMOVE_FROM_URL = "[\\p{Z}\\p{P}]";

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

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            videoUrl = savedInstanceState.getString(VideoItemDetailFragment.VIDEO_URL);
            currentStreamingService = savedInstanceState.getInt(VideoItemDetailFragment.STREAMING_SERVICE);
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
        if (intent.getData() != null) {
            // this means the video was called though another app
            videoUrl = intent.getData().toString();
            currentStreamingService = getServiceIdByUrl(videoUrl);
            if(currentStreamingService == -1) {
                Toast.makeText(this, R.string.url_not_supported_toast, Toast.LENGTH_LONG)
                        .show();
            }
            autoplay = PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean(getString(R.string.autoplay_through_intent_key), false);
        } else if(intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
            //this means that vidoe was called through share menu
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            videoUrl = getUris(extraText)[0];
            currentStreamingService = getServiceIdByUrl(videoUrl);
        } else {
            //this is if the video was called through another NewPipe activity
            videoUrl = intent.getStringExtra(VideoItemDetailFragment.VIDEO_URL);
            currentStreamingService = intent.getIntExtra(VideoItemDetailFragment.STREAMING_SERVICE, -1);
        }
        arguments.putBoolean(VideoItemDetailFragment.AUTO_PLAY, autoplay);
        arguments.putString(VideoItemDetailFragment.VIDEO_URL, videoUrl);
        arguments.putInt(VideoItemDetailFragment.STREAMING_SERVICE, currentStreamingService);
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

            Intent intent = new Intent(this, MainActivity.class);
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


    /**
     * Retrieves all Strings which look remotely like URLs from a text.
     * Used if NewPipe was called through share menu.
     *
     * @param sharedText text to scan for URLs.
     * @return potential URLs
     */
    private String[] getUris(final String sharedText) {
        final Collection<String> result = new HashSet<>();
        if (sharedText != null) {
            final String[] array = sharedText.split("\\p{Space}");
            for (String s : array) {
                s = trim(s);
                if (s.length() != 0) {
                    if (s.matches(".+://.+")) {
                        result.add(removeHeadingGibberish(s));
                    } else if (s.matches(".+\\..+")) {
                        result.add("http://" + s);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    private static String removeHeadingGibberish(final String input) {
        int start = 0;
        for (int i = input.indexOf("://") - 1; i >= 0; i--) {
            if (!input.substring(i, i + 1).matches("\\p{L}")) {
                start = i + 1;
                break;
            }
        }
        return input.substring(start, input.length());
    }

    private static String trim(final String input) {
        if (input == null || input.length() < 1) {
            return input;
        } else {
            String output = input;
            while (output.length() > 0 && output.substring(0, 1).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(1);
            }
            while (output.length() > 0
                    && output.substring(output.length() - 1, output.length()).matches(REGEX_REMOVE_FROM_URL)) {
                output = output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    private int getServiceIdByUrl(String url) {
        StreamingService[] serviceList = NewPipe.getServices();
        int service = -1;
        for (int i = 0; i < serviceList.length; i++) {
            if (serviceList[i].getUrlIdHandlerInstance().acceptUrl(videoUrl)) {
                service = i;
                //videoExtractor = ServiceList.getService(i).getExtractorInstance();
                break;
            }
        }
        return service;
    }
}
