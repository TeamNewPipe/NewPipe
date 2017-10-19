package org.schabi.newpipe.import_subscriptions;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
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
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SubscriptionImporter;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.OPMLParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

public class ImportActivity extends AppCompatActivity implements ImportConfirmDialogFragment.NoticeDialogListener{

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    public List<SubscriptionEntity> subscriptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);

        setContentView(R.layout.activity_import);

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
        getMenuInflater().inflate(R.menu.menu_import, menu);
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

    @Override
    public void onDialogPositiveClick(DialogFragment dialog){
        new SubscriptionImporter().execute(subscriptions);
        dialog.dismiss();
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    public static class ImportFragment extends Fragment {

        public List<SubscriptionEntity> subscriptions;
        public Activity activity;
        ProgressDialog progressDialog;

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static ImportFragment newInstance() {
            return new ImportFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_import, container, false);

            final Button button = rootView.findViewById(R.id.button_import);
            button.setOnClickListener(new onImportClickListener());
            activity = getActivity();
            return rootView;
        }

        private class onImportClickListener implements View.OnClickListener {
            @Override
            public void onClick(final View view) {
                    Intent i = new Intent(activity, FilePickerActivityHelper.class)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, false)
                        .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_FILE)
                        .putExtra(FilePickerActivityHelper.EXTRA_SINGLE_CLICK, true)
                        .putExtra(FilePickerActivityHelper.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(i, 0);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == Activity.RESULT_OK) {
                String path = data.getData().getPath();
                FileInputStream in = null;
                if (path.split("\\.(?=[^\\.]+$)")[1].equals("opml")) {
                    try {
                        in = new FileInputStream(path);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    new OPMLParser(new OPMLParser.AsyncResponse() {
                        @Override
                        public void processFinish(List<SubscriptionEntity> subscriptions) {
                            progressDialog.dismiss();
                            if (subscriptions != null) {
                                ImportConfirmDialogFragment dialogConfirm = new ImportConfirmDialogFragment();
                                FragmentManager fragmentManager = getChildFragmentManager();
                                ImportActivity importActivity = (ImportActivity) getActivity();
                                importActivity.subscriptions = subscriptions;
                                dialogConfirm.show(fragmentManager, "dialogConfirm");
                            } else {
                                ImportFailDialogFragment dialogFail = new ImportFailDialogFragment();
                                FragmentManager fragmentManager = getChildFragmentManager();
                                dialogFail.show(fragmentManager, "dialogFail");
                            }
                        }

                        @Override
                        public void processPreExecute() {
                            Context context = getContext();
                            progressDialog = new ProgressDialog(context);
                            progressDialog.setMessage("Loading file...");
                            progressDialog.show();
                        }
                    }).execute(in);
                }
            }
        }
    }
}

