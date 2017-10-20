package org.schabi.newpipe.export_subscriptions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SubscriptionExporter;
import org.schabi.newpipe.util.ThemeHelper;

/**
 * Created by gui on 20/10/17.
 */

public class ExportActivity extends AppCompatActivity {

    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);

        setContentView(R.layout.activity_export);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_export, menu);
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

    public static class ExportFragment extends Fragment {

        public Activity activity;

        public static ExportActivity newInstance() {
            return new ExportActivity();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_export, container, false);

            final Button button = rootView.findViewById(R.id.button_export);
            button.setOnClickListener(new onImportClickListener());
            activity = getActivity();
            return rootView;
        }

        private class onImportClickListener implements View.OnClickListener {
            @Override
            public void onClick(final View view) {
                Intent i = new Intent(activity, FilePickerActivityHelper.class)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                        .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_NEW_FILE)
                        .putExtra(FilePickerActivityHelper.EXTRA_SINGLE_CLICK, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_EXISTING_FILE, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(i, 0);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            String path = data.getData().getPath();
            new SubscriptionExporter().execute(path);
        }
    }
}
