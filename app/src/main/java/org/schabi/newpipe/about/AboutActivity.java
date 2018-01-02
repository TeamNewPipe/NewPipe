package org.schabi.newpipe.about;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class AboutActivity extends AppCompatActivity {

    /**
     * List of all software components
     */
    private static final SoftwareComponent[] SOFTWARE_COMPONENTS = new SoftwareComponent[]{
            new SoftwareComponent("Giga Get", "2014", "Peter Cai", "https://github.com/PaperAirplane-Dev-Team/GigaGet", StandardLicenses.GPL2),
            new SoftwareComponent("NewPipe Extractor", "2017", "Christian Schabesberger", "https://github.com/TeamNewPipe/NewPipeExtractor", StandardLicenses.GPL3),
            new SoftwareComponent("Jsoup", "2017", "Jonathan Hedley", "https://github.com/jhy/jsoup", StandardLicenses.MIT),
            new SoftwareComponent("Google Gson", "2008", "Google Inc", "https://github.com/google/gson", StandardLicenses.APACHE2),
            new SoftwareComponent("Rhino", "2015", "Mozilla", "https://www.mozilla.org/rhino/", StandardLicenses.MPL2),
            new SoftwareComponent("ACRA", "2013", "Kevin Gaudin", "http://www.acra.ch", StandardLicenses.APACHE2),
            new SoftwareComponent("Universal Image Loader", "2011 - 2015", "Sergey Tarasevich", "https://github.com/nostra13/Android-Universal-Image-Loader", StandardLicenses.APACHE2),
            new SoftwareComponent("CircleImageView", "2014 - 2017", "Henning Dodenhof", "https://github.com/hdodenhof/CircleImageView", StandardLicenses.APACHE2),
            new SoftwareComponent("ParalaxScrollView", "2014", "Nir Hartmann", "https://github.com/nirhart/ParallaxScroll", StandardLicenses.MIT),
            new SoftwareComponent("NoNonsense-FilePicker", "2016", "Jonas Kalderstam", "https://github.com/spacecowboy/NoNonsense-FilePicker", StandardLicenses.MPL2),
            new SoftwareComponent("ExoPlayer", "2014-2017", "Google Inc", "https://github.com/google/ExoPlayer", StandardLicenses.APACHE2),
            new SoftwareComponent("RxAndroid", "2015", "The RxAndroid authors", "https://github.com/ReactiveX/RxAndroid", StandardLicenses.APACHE2),
            new SoftwareComponent("RxJava", "2016-present", "RxJava Contributors", "https://github.com/ReactiveX/RxJava", StandardLicenses.APACHE2),
            new SoftwareComponent("RxBinding", "2015", "Jake Wharton", "https://github.com/JakeWharton/RxBinding", StandardLicenses.APACHE2)
    };

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_settings:
                NavigationHelper.openSettings(this);
                return true;
            case R.id.action_show_downloads:
                return NavigationHelper.openDownloads(this);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class AboutFragment extends Fragment {

        public AboutFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static AboutFragment newInstance() {
            return new AboutFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_about, container, false);
            TextView version = rootView.findViewById(R.id.app_version);
            version.setText(BuildConfig.VERSION_NAME);

            View githubLink = rootView.findViewById(R.id.github_link);
            githubLink.setOnClickListener(new OnGithubLinkClickListener());

            View donationLink = rootView.findViewById(R.id.donation_link);
            donationLink.setOnClickListener(new OnDonationLinkClickListener());

            View websiteLink = rootView.findViewById(R.id.website_link);
            websiteLink.setOnClickListener(new OnWebsiteLinkClickListener());

            return rootView;
        }

        private static class OnGithubLinkClickListener implements View.OnClickListener {
            @Override
            public void onClick(final View view) {
                final Context context = view.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.github_url)));
                context.startActivity(intent);
            }
        }

        private static class OnDonationLinkClickListener implements View.OnClickListener {
            @Override
            public void onClick(final View view) {
                final Context context = view.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.donation_url)));
                context.startActivity(intent);
            }
        }

        private static class OnWebsiteLinkClickListener implements View.OnClickListener {
            @Override
            public void onClick(final View view) {
                final Context context = view.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.website_url)));
                context.startActivity(intent);
            }
        }
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return AboutFragment.newInstance();
                case 1:
                    return LicenseFragment.newInstance(SOFTWARE_COMPONENTS);
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.tab_about);
                case 1:
                    return getString(R.string.tab_licenses);
            }
            return null;
        }
    }
}
