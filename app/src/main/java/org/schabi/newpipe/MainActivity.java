/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.history.dao.HistoryDAO;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.WatchHistoryDAO;
import org.schabi.newpipe.database.history.model.HistoryEntry;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.WatchHistoryEntry;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.history.HistoryListener;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Date;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity implements HistoryListener {
    private static final String TAG = "MainActivity";
    public static final boolean DEBUG = false;
    private SharedPreferences sharedPreferences;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportFragmentManager() != null && getSupportFragmentManager().getBackStackEntryCount() == 0) {
            initFragments();
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        initHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles();
        }

        disposeHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "Theme has changed, recreating activity...");
            sharedPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            // https://stackoverflow.com/questions/10844112/runtimeexception-performing-pause-of-activity-that-is-not-resumed
            // Briefly, let the activity resume properly posting the recreate call to end of the message queue
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.recreate();
                }
            });
        }

        if(sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) Log.d(TAG, "main page has changed, recreating main fragment...");
            sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(this);
        }

    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            String action = intent.getAction();
            if ((action != null && action.equals(Intent.ACTION_MAIN)) && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return;
        }

        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        // If current fragment implements BackPressable (i.e. can/wanna handle back press) delegate the back press to it
        if (fragment instanceof BackPressable) {
            if (((BackPressable) fragment).onBackPressed()) return;
        }


        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else super.onBackPressed();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        super.onCreateOptionsMenu(menu);

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!(fragment instanceof VideoDetailFragment)) {
            findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner).setVisibility(View.GONE);
        }

        if (!(fragment instanceof SearchFragment)) {
            findViewById(R.id.toolbar).findViewById(R.id.toolbar_search_container).setVisibility(View.GONE);

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                NavigationHelper.gotoMainFragment(getSupportFragmentManager());
                return true;
            case R.id.action_settings:
                NavigationHelper.openSettings(this);
                return true;
            case R.id.action_show_downloads:
                return NavigationHelper.openDownloads(this);
            case R.id.action_about:
                NavigationHelper.openAbout(this);
                return true;
            case R.id.action_history:
                NavigationHelper.openHistory(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (DEBUG) Log.d(TAG, "initFragments() called");
        StateSaver.clearStateFiles();
        if (getIntent() != null && getIntent().hasExtra(Constants.KEY_LINK_TYPE)) {
            handleIntent(getIntent());
        } else NavigationHelper.gotoMainFragment(getSupportFragmentManager());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");

        if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
            String url = intent.getStringExtra(Constants.KEY_URL);
            int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            String title = intent.getStringExtra(Constants.KEY_TITLE);
            switch (((StreamingService.LinkType) intent.getSerializableExtra(Constants.KEY_LINK_TYPE))) {
                case STREAM:
                    boolean autoPlay = intent.getBooleanExtra(VideoDetailFragment.AUTO_PLAY, false);
                    NavigationHelper.openVideoDetailFragment(getSupportFragmentManager(), serviceId, url, title, autoPlay);
                    break;
                case CHANNEL:
                    NavigationHelper.openChannelFragment(getSupportFragmentManager(), serviceId, url, title);
                    break;
                case PLAYLIST:
                    NavigationHelper.openPlaylistFragment(getSupportFragmentManager(), serviceId, url, title);
                    break;
            }
        } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
            String searchQuery = intent.getStringExtra(Constants.KEY_QUERY);
            if (searchQuery == null) searchQuery = "";
            int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            NavigationHelper.openSearchFragment(getSupportFragmentManager(), serviceId, searchQuery);
        } else {
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // History
    //////////////////////////////////////////////////////////////////////////*/

    private WatchHistoryDAO watchHistoryDAO;
    private SearchHistoryDAO searchHistoryDAO;
    private PublishSubject<HistoryEntry> historyEntrySubject;
    private Disposable disposable;

    private void initHistory() {
        final AppDatabase database = NewPipeDatabase.getInstance();
        watchHistoryDAO = database.watchHistoryDAO();
        searchHistoryDAO = database.searchHistoryDAO();
        historyEntrySubject = PublishSubject.create();
        disposable = historyEntrySubject
                .observeOn(Schedulers.io())
                .subscribe(getHistoryEntryConsumer());
    }

    private void disposeHistory() {
        if (disposable != null) disposable.dispose();
        watchHistoryDAO = null;
        searchHistoryDAO = null;
    }

    @NonNull
    private Consumer<HistoryEntry> getHistoryEntryConsumer() {
        return new Consumer<HistoryEntry>() {
            @Override
            public void accept(HistoryEntry historyEntry) throws Exception {
                //noinspection unchecked
                HistoryDAO<HistoryEntry> historyDAO = (HistoryDAO<HistoryEntry>)
                        (historyEntry instanceof SearchHistoryEntry ? searchHistoryDAO : watchHistoryDAO);

                HistoryEntry latestEntry = historyDAO.getLatestEntry();
                if (historyEntry.hasEqualValues(latestEntry)) {
                    latestEntry.setCreationDate(historyEntry.getCreationDate());
                    historyDAO.update(latestEntry);
                } else {
                    historyDAO.insert(historyEntry);
                }
            }
        };
    }

    private void addWatchHistoryEntry(StreamInfo streamInfo) {
        if (sharedPreferences.getBoolean(getString(R.string.enable_watch_history_key), true)) {
            WatchHistoryEntry entry = new WatchHistoryEntry(streamInfo);
            historyEntrySubject.onNext(entry);
        }
    }

    @Override
    public void onVideoPlayed(StreamInfo streamInfo, VideoStream videoStream) {
        addWatchHistoryEntry(streamInfo);
    }

    @Override
    public void onAudioPlayed(StreamInfo streamInfo, AudioStream audioStream) {
        addWatchHistoryEntry(streamInfo);
    }

    @Override
    public void onSearch(int serviceId, String query) {
        // Add search history entry
        if (sharedPreferences.getBoolean(getString(R.string.enable_search_history_key), true)) {
            SearchHistoryEntry searchHistoryEntry = new SearchHistoryEntry(new Date(), serviceId, query);
            historyEntrySubject.onNext(searchHistoryEntry);
        }
    }
}
