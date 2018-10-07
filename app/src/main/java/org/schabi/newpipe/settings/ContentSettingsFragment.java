package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.nononsenseapps.filepicker.Utils;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ContentSettingsFragment extends BasePreferenceFragment {

    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;

    private File databasesDir;
    private File newpipe_db;
    private File newpipe_db_journal;
    private File newpipe_db_shm;
    private File newpipe_db_wal;
    private File newpipe_settings;

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

        String homeDir = getActivity().getApplicationInfo().dataDir;
        databasesDir = new File(homeDir + "/databases");
        newpipe_db = new File(homeDir + "/databases/newpipe.db");
        newpipe_db_journal = new File(homeDir + "/databases/newpipe.db-journal");
        newpipe_db_shm = new File(homeDir + "/databases/newpipe.db-shm");
        newpipe_db_wal = new File(homeDir + "/databases/newpipe.db-wal");

        newpipe_settings = new File(homeDir + "/databases/newpipe.settings");
        newpipe_settings.delete();

        addPreferencesFromResource(R.xml.content_settings);

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

            saveSharedPreferencesToFile(newpipe_settings);
            ZipHelper.addFileToZip(outZip, newpipe_settings.getPath(), "newpipe.settings");

            outZip.close();

            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            onError(e);
        }
    }

    private void saveSharedPreferencesToFile(File dst) {
        ObjectOutputStream output = null;
        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
            output.writeObject(pref.getAll());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
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
            } catch (Exception ignored){}
        }

        try {
            if (!databasesDir.exists() && !databasesDir.mkdir()) {
                throw new Exception("Could not create databases dir");
            }

            final boolean isDbFileExtracted = ZipHelper.extractFileFromZip(filePath,
                    newpipe_db.getPath(), "newpipe.db");

            if (isDbFileExtracted) {
                newpipe_db_journal.delete();
                newpipe_db_wal.delete();
                newpipe_db_shm.delete();

            } else {

                Toast.makeText(getContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                        .show();
            }

            //If settings file exist, ask if it should be imported.
            if(ZipHelper.extractFileFromZip(filePath, newpipe_settings.getPath(), "newpipe.settings")) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
                alert.setTitle(R.string.import_settings);

                alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                    // restart app to properly load db
                    System.exit(0);
                });
                alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    loadSharedPreferences(newpipe_settings);
                    // restart app to properly load db
                    System.exit(0);
                });
                alert.show();
            } else {
                // restart app to properly load db
                System.exit(0);
            }

        } catch (Exception e) {
            onError(e);
        }
    }

    private void loadSharedPreferences(File src) {
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            prefEdit.clear();
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean)
                    prefEdit.putBoolean(key, (Boolean) v);
                else if (v instanceof Float)
                    prefEdit.putFloat(key, (Float) v);
                else if (v instanceof Integer)
                    prefEdit.putInt(key, (Integer) v);
                else if (v instanceof Long)
                    prefEdit.putLong(key, (Long) v);
                else if (v instanceof String)
                    prefEdit.putString(key, ((String) v));
            }
            prefEdit.apply();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e,
                activity.getClass(),
                null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash));
    }
}
