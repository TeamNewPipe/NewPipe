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
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.fragments.OnItemSelectedListener;
import org.schabi.newpipe.fragments.channel.ChannelFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.search.SearchFragment;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class MainActivity extends AppCompatActivity implements OnItemSelectedListener {
    private static final String TAG = MainActivity.class.toString();

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.setTheme(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (savedInstanceState == null) initFragments();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (fragment instanceof VideoDetailFragment) if (((VideoDetailFragment) fragment).onActivityBackPressed()) return;

        if (getSupportFragmentManager().getBackStackEntryCount() >= 2) {
            getSupportFragmentManager().popBackStackImmediate();
        } else {
            if (fragment instanceof SearchFragment) {
                SearchFragment searchFragment = (SearchFragment) fragment;
                if (!searchFragment.isMainBgVisible()) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
                    NavigationHelper.openMainActivity(this);
                    return;
                }
            }
            finish();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home: {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
                if (fragment instanceof VideoDetailFragment) ((VideoDetailFragment) fragment).clearHistory();

                NavigationHelper.openMainActivity(this);
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_show_downloads: {
                if (!PermissionHelper.checkStoragePermissions(this)) {
                    return false;
                }
                Intent intent = new Intent(this, DownloadActivity.class);
                startActivity(intent);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (getIntent() != null && getIntent().hasExtra(Constants.KEY_URL)) {
            handleIntent(getIntent());
        } else openSearchFragment();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnItemSelectedListener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onItemSelected(StreamingService.LinkType linkType, int serviceId, String url, String name) {
        switch (linkType) {
            case STREAM:
                openVideoDetailFragment(serviceId, url, name, false);
                break;
            case CHANNEL:
                openChannelFragment(serviceId, url, name);
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void handleIntent(Intent intent) {
        if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
            String url = intent.getStringExtra(Constants.KEY_URL);
            int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            try {
                switch (((StreamingService.LinkType) intent.getSerializableExtra(Constants.KEY_LINK_TYPE))) {
                    case STREAM:
                        handleVideoDetailIntent(serviceId, url, intent);
                        break;
                    case CHANNEL:
                        handleChannelIntent(serviceId, url, intent);
                        break;
                    case NONE:
                        throw new Exception("Url not known to service. service=" + Integer.toString(serviceId) + " url=" + url);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getSupportFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            openSearchFragment();
        }
    }

    private void openSearchFragment() {
        ImageLoader.getInstance().clearMemoryCache();
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_holder, new SearchFragment())
                .addToBackStack(null)
                .commit();
    }

    private void openVideoDetailFragment(int serviceId, String url, String title, boolean autoPlay) {
        ImageLoader.getInstance().clearMemoryCache();

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (title == null) title = "";

        if (fragment instanceof VideoDetailFragment && fragment.isVisible()) {
            VideoDetailFragment detailFragment = (VideoDetailFragment) fragment;
            detailFragment.setAutoplay(autoPlay);
            detailFragment.selectAndLoadVideo(serviceId, url, title);
            return;
        }

        VideoDetailFragment instance = VideoDetailFragment.getInstance(serviceId, url, title);
        instance.setAutoplay(autoPlay);

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_holder, instance)
                .addToBackStack(null)
                .commit();
    }

    private void openChannelFragment(int serviceId, String url, String name) {
        ImageLoader.getInstance().clearMemoryCache();
        if (name == null) name = "";
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_holder, ChannelFragment.newInstance(serviceId, url, name))
                .addToBackStack(null)
                .commit();
    }

    private void handleVideoDetailIntent(int serviceId, String url, Intent intent) {
        boolean autoPlay = intent.getBooleanExtra(VideoDetailFragment.AUTO_PLAY, false);
        String title = intent.getStringExtra(Constants.KEY_TITLE);
        openVideoDetailFragment(serviceId, url, title, autoPlay);
    }

    private void handleChannelIntent(int serviceId, String url, Intent intent) {
        String name = intent.getStringExtra(Constants.KEY_TITLE);
        openChannelFragment(serviceId, url, name);
    }

}
