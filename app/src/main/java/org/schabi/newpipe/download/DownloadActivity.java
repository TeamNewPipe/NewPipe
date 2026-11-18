package org.schabi.newpipe.download;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityDownloaderBinding;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.EdgeToEdgeHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.MissionsFragment;

public class DownloadActivity extends AppCompatActivity {

    public static final String EXTRA_DOWNLOAD_SEARCH = "download_search";
    private static final String MISSIONS_FRAGMENT_TAG = "fragment_tag";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // Service
        final Intent i = new Intent();
        i.setClass(this, DownloadManagerService.class);
        startService(i);

        ThemeHelper.setTheme(this);

        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);

        final ActivityDownloaderBinding downloaderBinding =
                ActivityDownloaderBinding.inflate(getLayoutInflater());
        setContentView(downloaderBinding.getRoot());
        EdgeToEdgeHelper.applySystemBarPadding(downloaderBinding.getRoot());

        setSupportActionBar(downloaderBinding.toolbarLayout.toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.downloads_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateFragments();
                getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
    }

    private void updateFragments() {
        final Fragment currentFragment = getSupportFragmentManager()
                .findFragmentByTag(MISSIONS_FRAGMENT_TAG);
        if (currentFragment instanceof MissionsFragment) {
            return;
        }

        final MissionsFragment fragment = new MissionsFragment();
        final String search = getIntent().getStringExtra(EXTRA_DOWNLOAD_SEARCH);
        if (search != null) {
            fragment.setSearchQuery(search);
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, fragment, MISSIONS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
