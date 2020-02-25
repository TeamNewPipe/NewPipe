package org.schabi.newpipe.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.CheckForNewAppVersionTask;
import org.schabi.newpipe.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * This is a fragment that checks for updates, downloads them, and offers them for install
 */
public class CheckForUpdatesFragment extends BaseFragment implements CheckForNewAppVersionTask.UpdateCallback {

    public static final String APK_NAME = "/newpipe.apk";

    private TextView mTitle;
    private TextView mSubtitle;
    private Button mActionButton;
    private ProgressBar mProgressBar;

    private boolean checked = false;

    private File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_update_check, container, false);
        mTitle = root.findViewById(R.id.check_for_updates_title);
        mSubtitle = root.findViewById(R.id.check_for_updates_subtitle);
        mActionButton = root.findViewById(R.id.check_for_updates_download);
        mProgressBar = root.findViewById(R.id.check_for_updates_progress);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkForUpdates();
    }

    /**
     * This performs a check for updates
     */
    private void checkForUpdates() {

        if (checked)
            return;

        mProgressBar.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);

        mTitle.setText(R.string.updates_checking_for_updates);
        mSubtitle.setText("");

        CheckForNewAppVersionTask task = new CheckForNewAppVersionTask(true);
        task.setUpdateCallback(this);
        task.execute();

    }

    /**
     * This is the callback from the version check
     */
    @Override
    public void updateAvailable(String versionName, String versionCode, String apkUrl) {
        if (versionCode == null)
            failedToCheck();
        else if (BuildConfig.VERSION_CODE < Integer.valueOf(versionCode))
            updatesAreAvailable(versionName, apkUrl);
        else if (BuildConfig.VERSION_CODE == Integer.valueOf(versionCode))
            applicationUpToDate(versionName);
        checked = true;
    }

    /**
     * This callback happens when the app is up to date
     */
    private void applicationUpToDate(String versionName) {

        mTitle.setText(R.string.updates_app_up_to_date);
        mSubtitle.setText(versionName);

        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setText(R.string.updates_check_for_updates);
        mActionButton.setOnClickListener((v) -> {
            checked = false;
            checkForUpdates();
        });
    }

    /**
     * The update check has failed... show that
     */
    private void failedToCheck() {
        mTitle.setText(R.string.updates_failed_to_check);
        mSubtitle.setText("");
        mProgressBar.setVisibility(View.GONE);

        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setText(R.string.updates_check_for_updates);
        mActionButton.setOnClickListener((v) -> {
            checked = false;
            checkForUpdates();
        });
    }

    /**
     * There are updates available!
     */
    private void updatesAreAvailable(String versionName, String apkUrl) {
        mTitle.setText(String.format(getText(R.string.updates_new_version_available).toString(), versionName));
        mSubtitle.setText(R.string.updates_click_to_download);

        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setText(R.string.download);
        mActionButton.setOnClickListener((v) -> {
            downloadNewVersion(apkUrl);
        });
    }

    /**
     * This downloads a new apk version to the cache and installs it
     */
    private void downloadNewVersion(String apkUrl) {

        mActionButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

        mTitle.setText(R.string.updates_downloading);
        mSubtitle.setText(R.string.updates_connecting);

        ApkDownloadTask task = new ApkDownloadTask(new ApkDownloadTask.DownloadListener() {

            @Override
            public void update(int progress, boolean done) {
                mProgressBar.setProgress(progress);
                mSubtitle.setText(String.format(Locale.ENGLISH,"%d %%", progress));
                if (done)
                    installApk();
            }

            @Override
            public void fail() {
                failedToCheck();
            }
        });

        task.execute(apkUrl, new File(downloads, APK_NAME)
                .getAbsolutePath());

    }

    /**
     * This installs the APK
     */
    private void installApk() {

        mProgressBar.setVisibility(View.GONE);
        mTitle.setText(R.string.updates_ready);
        mSubtitle.setText(R.string.updates_press_to_install);

        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setText(R.string.install);
        mActionButton.setOnClickListener((v) -> {

            File file = new File(downloads, APK_NAME);
            Uri fileUri = FileProvider.getUriForFile(getContext(),
                    BuildConfig.APPLICATION_ID + ".provider",
                    file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri,"application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            startActivity(intent);

        });

    }

    /**
     * This is a task that downloads the APK to the disk
     */
    private static class ApkDownloadTask extends AsyncTask<String, Integer, Integer> {

        private static final int OK = 0;
        private static final int FAILED = 1;
        private DownloadListener listener;

        public ApkDownloadTask(DownloadListener listener) {
            this.listener = listener;
        }

        @Override
        protected Integer doInBackground(String... strings) {

            try {
                URL url = new URL(strings[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    InputStream in = connection.getInputStream();
                    FileOutputStream fos = new FileOutputStream(strings[1]);
                    int length = connection.getContentLength();
                    float totalRead = 0;
                    int previous = 0;
                    int current;
                    int b;
                    byte[] buf = new byte[8192];
                    while ((b = in.read(buf)) != -1) {
                        totalRead += b;
                        current = Math.round(totalRead / length * 100.0f);
                        /*
                         * with this we limit sub-perc micro-updating of the UI
                         */
                        if (current != previous) {
                            previous = current;
                            publishProgress(current);
                        }
                        fos.write(buf, 0, b);
                    }
                    in.close();
                    fos.close();
                    return OK;
                }

            } catch (IOException e) {
                e.printStackTrace();
                return FAILED;
            }

            return OK;
        }

        @Override
        protected void onPostExecute(Integer aVoid) {
            super.onPostExecute(aVoid);
            System.out.println(aVoid);
            if (aVoid == FAILED) {
                listener.fail();
                return;
            }
            listener.update(100, true);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            listener.update(values[0], false);

        }

        public interface DownloadListener {
            void update(int progress, boolean done);
            void fail();
        }
    }


}
