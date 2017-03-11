package org.schabi.newpipe.download;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.util.Vector;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.AllMissionsFragment;
import us.shandian.giga.ui.fragment.MissionsFragment;
import us.shandian.giga.util.CrashHandler;
import us.shandian.giga.util.Utility;

public class DownloadActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    public static final String INTENT_DOWNLOAD = "us.shandian.giga.intent.DOWNLOAD";

    public static final String INTENT_LIST = "us.shandian.giga.intent.LIST";
    public static final String THREADS = "threads";
    private static final String TAG = DownloadActivity.class.toString();
    private MissionsFragment mFragment;


    private String mPendingUrl;
    private SharedPreferences mPrefs;

    @Override
    @TargetApi(21)
    protected void onCreate(Bundle savedInstanceState) {
        CrashHandler.init(this);
        CrashHandler.register();

        // Service
        Intent i = new Intent();
        i.setClass(this, DownloadManagerService.class);
        startService(i);

        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this, true);
        setContentView(R.layout.activity_downloader);

        //noinspection ConstantConditions

        // its ok if this fails, we will catch that error later, and send it as report
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.downloads_title);
        actionBar.setDisplayShowTitleEnabled(true);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Fragment
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateFragments();
                getWindow().getDecorView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });

        // Intent
        if (getIntent().getAction() != null && getIntent().getAction().equals(INTENT_DOWNLOAD)) {
            mPendingUrl = getIntent().getData().toString();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getAction().equals(INTENT_DOWNLOAD)) {
            mPendingUrl = intent.getData().toString();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPendingUrl != null) {
            showUrlDialog();
            mPendingUrl = null;
        }
    }

    private void updateFragments() {

        mFragment = new AllMissionsFragment();

        getFragmentManager().beginTransaction()
                .replace(R.id.frame, mFragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    private void showUrlDialog() {
        // Create the view
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.dialog_url, null);
        final EditText name = Utility.findViewById(v, R.id.file_name);
        final TextView tCount = Utility.findViewById(v, R.id.threads_count);
        final SeekBar threads = Utility.findViewById(v, R.id.threads);
        final Toolbar toolbar = Utility.findViewById(v, R.id.toolbar);
        final RadioButton audioButton = (RadioButton) Utility.findViewById(v, R.id.audio_button);


        threads.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                tCount.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar p1) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar p1) {

            }

        });

        int def = mPrefs.getInt(THREADS, 4);
        threads.setProgress(def - 1);
        tCount.setText(String.valueOf(def));

        name.setText(getIntent().getStringExtra("fileName"));

        toolbar.setTitle(R.string.add);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);

        // Show the dialog
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setCancelable(true)
                .setView(v)
                .create();

        dialog.show();

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.okay) {

                    String location;
                    if(audioButton.isChecked()) {
                        location = NewPipeSettings.getAudioDownloadPath(DownloadActivity.this);
                    } else {
                        location = NewPipeSettings.getVideoDownloadPath(DownloadActivity.this);
                    }

                    String fName = name.getText().toString().trim();

                    File f = new File(location, fName);
                    if (f.exists()) {
                        Toast.makeText(DownloadActivity.this, R.string.msg_exists, Toast.LENGTH_SHORT).show();
                    } else {
                        DownloadManagerService.startMission(
                                DownloadActivity.this,
                                getIntent().getData().toString(), location, fName,
                                audioButton.isChecked(), threads.getProgress() + 1);
                        mFragment.notifyChange();

                        mPrefs.edit().putInt(THREADS, threads.getProgress() + 1).commit();
                        mPendingUrl = null;
                        dialog.dismiss();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        });

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.download_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
