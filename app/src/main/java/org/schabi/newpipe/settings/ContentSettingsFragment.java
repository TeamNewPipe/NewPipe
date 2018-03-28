package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.nononsenseapps.filepicker.Utils;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ContentSettingsFragment extends BasePreferenceFragment {

    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;

    private String homeDir;
    private File databasesDir;
    private File newpipe_db;
    private File newpipe_db_journal;

    private String thumbnailLoadToggleKey;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thumbnailLoadToggleKey = getString(R.string.download_thumbnail_key);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(thumbnailLoadToggleKey)) {
            final ImageLoader imageLoader = ImageLoader.getInstance();
            imageLoader.stop();
            imageLoader.clearDiskCache();
            imageLoader.clearMemoryCache();
            imageLoader.resume();
            Toast.makeText(preference.getContext(), R.string.thumbnail_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        homeDir = getActivity().getApplicationInfo().dataDir;
        databasesDir = new File(homeDir + "/databases");
        newpipe_db = new File(homeDir + "/databases/newpipe.db");
        newpipe_db_journal = new File(homeDir + "/databases/newpipe.db-journal");

        addPreferencesFromResource(R.xml.content_settings);

        final ListPreference mainPageContentPref =  (ListPreference) findPreference(getString(R.string.main_page_content_key));
        mainPageContentPref.setOnPreferenceChangeListener((Preference preference, Object newValueO) -> {
            final String newValue = newValueO.toString();

            final String mainPrefOldValue =
                    defaultPreferences.getString(getString(R.string.main_page_content_key), "blank_page");
            final String mainPrefOldSummary = getMainPagePrefSummery(mainPrefOldValue, mainPageContentPref);

            if(newValue.equals(getString(R.string.kiosk_page_key))) {
                SelectKioskFragment selectKioskFragment = new SelectKioskFragment();
                selectKioskFragment.setOnSelectedLisener((String kioskId, int service_id) -> {
                    defaultPreferences.edit()
                            .putInt(getString(R.string.main_page_selected_service), service_id).apply();
                    defaultPreferences.edit()
                            .putString(getString(R.string.main_page_selectd_kiosk_id), kioskId).apply();
                    String serviceName = "";
                    try {
                        serviceName = NewPipe.getService(service_id).getServiceInfo().getName();
                    } catch (ExtractionException e) {
                        onError(e);
                    }
                    String kioskName = KioskTranslator.getTranslatedKioskName(kioskId,
                            getContext());

                    String summary =
                            String.format(getString(R.string.service_kiosk_string),
                                    serviceName,
                                    kioskName);

                    mainPageContentPref.setSummary(summary);
                });
                selectKioskFragment.setOnCancelListener(() -> {
                    mainPageContentPref.setSummary(mainPrefOldSummary);
                    mainPageContentPref.setValue(mainPrefOldValue);
                });
                selectKioskFragment.show(getFragmentManager(), "select_kiosk");
            } else if(newValue.equals(getString(R.string.channel_page_key))) {
                SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
                selectChannelFragment.setOnSelectedLisener((String url, String name, int service) -> {
                    defaultPreferences.edit()
                            .putInt(getString(R.string.main_page_selected_service), service).apply();
                    defaultPreferences.edit()
                            .putString(getString(R.string.main_page_selected_channel_url), url).apply();
                    defaultPreferences.edit()
                            .putString(getString(R.string.main_page_selected_channel_name), name).apply();

                    mainPageContentPref.setSummary(name);
                });
                selectChannelFragment.setOnCancelListener(() -> {
                    mainPageContentPref.setSummary(mainPrefOldSummary);
                    mainPageContentPref.setValue(mainPrefOldValue);
                });
                selectChannelFragment.show(getFragmentManager(), "select_channel");
            } else {
                mainPageContentPref.setSummary(getMainPageSummeryByKey(newValue));
            }

            defaultPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, true).apply();

            return true;
        });

        Preference importDataPreference = findPreference(getString(R.string.import_data));
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_FILE);
            startActivityForResult(i, REQUEST_IMPORT_PATH);
            return true;
        });

        Preference exportDataPreference = findPreference(getString(R.string.export_data));
        exportDataPreference.setOnPreferenceClickListener((Preference p) -> {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
            startActivityForResult(i, REQUEST_EXPORT_PATH);
            return true;
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        }

        if ((requestCode == REQUEST_IMPORT_PATH || requestCode == REQUEST_EXPORT_PATH)
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
                String path = Utils.getFileForUri(data.getData()).getAbsolutePath();
                if (requestCode == REQUEST_EXPORT_PATH) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                    exportDatabase(path + "/NewPipeData-" + sdf.format(new Date()) + ".zip");
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(R.string.override_current_data)
                            .setPositiveButton(android.R.string.ok,
                                    (DialogInterface d, int id) -> importDatabase(path))
                            .setNegativeButton(android.R.string.cancel,
                                    (DialogInterface d, int id) -> d.cancel());
                    builder.create().show();
                }
        }
    }

    private void exportDatabase(String path) {
        try {
            ZipOutputStream outZip = new ZipOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(path)));
            ZipHelper.addFileToZip(outZip, newpipe_db.getPath(), "newpipe.db");
            ZipHelper.addFileToZip(outZip, newpipe_db_journal.getPath(), "newpipe.db-journal");

            outZip.close();

            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            onError(e);
        }
    }

    private void importDatabase(String filePath) {
        // check if file is supported
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(filePath);
        } catch (IOException ioe) {
            Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                    .show();
            return;
        } finally {
            try {
                zipFile.close();
            } catch (Exception e){}
        }

        try {
            ZipInputStream zipIn = new ZipInputStream(
                    new BufferedInputStream(
                            new FileInputStream(filePath)));

            if (!databasesDir.exists() && !databasesDir.mkdir()) {
                throw new Exception("Could not create databases dir");
            }

            if(!(ZipHelper.extractFileFromZip(zipIn, newpipe_db.getPath(), "newpipe.db")
                && ZipHelper.extractFileFromZip(zipIn, newpipe_db_journal.getPath(), "newpipe.db-journal"))) {
               Toast.makeText(getContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                       .show();
            }

            zipIn.close();

            // restart app to properly load db
            //App.restart(getContext());
            System.exit(0);
        } catch (Exception e) {
            onError(e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final String mainPageContentKey = getString(R.string.main_page_content_key);
        final Preference mainPagePref = findPreference(getString(R.string.main_page_content_key));
        final String bpk = getString(R.string.blank_page_key);
        if(defaultPreferences.getString(mainPageContentKey, bpk)
                .equals(getString(R.string.channel_page_key))) {
            mainPagePref.setSummary(defaultPreferences.getString(getString(R.string.main_page_selected_channel_name), "error"));
        } else if(defaultPreferences.getString(mainPageContentKey, bpk)
                .equals(getString(R.string.kiosk_page_key))) {
            try {
                StreamingService service = NewPipe.getService(
                        defaultPreferences.getInt(
                                getString(R.string.main_page_selected_service), 0));

                String kioskName = KioskTranslator.getTranslatedKioskName(
                        defaultPreferences.getString(
                                getString(R.string.main_page_selectd_kiosk_id), "Trending"),
                        getContext());

                String summary =
                        String.format(getString(R.string.service_kiosk_string),
                                service.getServiceInfo().getName(),
                                kioskName);

                mainPagePref.setSummary(summary);
            } catch (Exception e) {
                onError(e);
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/
    private String getMainPagePrefSummery(final String mainPrefOldValue, final ListPreference mainPageContentPref) {
        if(mainPrefOldValue.equals(getString(R.string.channel_page_key))) {
            return defaultPreferences.getString(getString(R.string.main_page_selected_channel_name), "error");
        } else {
            return mainPageContentPref.getSummary().toString();
        }
    }

    private int getMainPageSummeryByKey(final String key) {
        if(key.equals(getString(R.string.blank_page_key))) {
            return R.string.blank_page_summary;
        } else if(key.equals(getString(R.string.kiosk_page_key))) {
            return R.string.kiosk_page_summary;
        } else if(key.equals(getString(R.string.feed_page_key))) {
            return R.string.feed_page_summary;
        } else if(key.equals(getString(R.string.subscription_page_key))) {
            return R.string.subscription_page_summary;
        } else if(key.equals(getString(R.string.channel_page_key))) {
            return R.string.channel_page_summary;
        }
        return R.string.blank_page_summary;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected boolean onError(Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e,
                activity.getClass(),
                null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash));
        return true;
    }
}
