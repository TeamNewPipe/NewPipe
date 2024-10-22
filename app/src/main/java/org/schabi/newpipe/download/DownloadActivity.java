package org.schabi.newpipe.download;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityDownloaderBinding;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.MissionsFragment;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import com.marcinorlowski.fonty.Fonty;

public class DownloadActivity extends AppCompatActivity {

    private static final String MISSIONS_FRAGMENT_TAG = "fragment_tag";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // Service
        final Intent i = new Intent();
        i.setClass(this, DownloadManagerService.class);
        startService(i);

        assureCorrectAppLanguage(this);
        ThemeHelper.setTheme(this);

        super.onCreate(savedInstanceState);

        final ActivityDownloaderBinding downloaderBinding =
                ActivityDownloaderBinding.inflate(getLayoutInflater());
        setContentView(downloaderBinding.getRoot());

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
        final String preferredFont = getPreferredFont(this);
        if (!preferredFont.equals(getString(R.string.default_font_key))) {
            Fonty.setFonts(this);
        }
    }

    private void updateFragments() {
        final MissionsFragment fragment = new MissionsFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, fragment, MISSIONS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }
    public String getPreferredFont(final Context context) {
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return preferences.getString("preferred_font", getString(R.string.default_font_key));
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        final MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.download_menu, menu);

        return true;
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
