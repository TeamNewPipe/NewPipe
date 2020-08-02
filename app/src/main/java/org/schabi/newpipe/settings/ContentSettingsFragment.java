package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.SharpOutputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedInputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;
    private static final int REQUEST_ADD_EXTENSION = 32945;

    private File databasesDir;
    private File newpipeDb;
    private File newpipeDbJournal;
    private File newpipeDbShm;
    private File newpipeDbWal;
    private File newpipeSettings;

    private String thumbnailLoadToggleKey;
    private String youtubeRestrictedModeEnabledKey;

    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thumbnailLoadToggleKey = getString(R.string.download_thumbnail_key);
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = PreferenceManager
                .getDefaultSharedPreferences(getContext()).getString("app_language_key", "en");
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(thumbnailLoadToggleKey)) {
            final ImageLoader imageLoader = ImageLoader.getInstance();
            imageLoader.stop();
            imageLoader.clearDiskCache();
            imageLoader.clearMemoryCache();
            imageLoader.resume();
            Toast.makeText(preference.getContext(), R.string.thumbnail_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        if (preference.getKey().equals(youtubeRestrictedModeEnabledKey)) {
            Context context = getContext();
            if (context != null) {
                DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(context);
            } else {
                Log.w(TAG, "onPreferenceTreeClick: null context");
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final String homeDir = getActivity().getApplicationInfo().dataDir;
        databasesDir = new File(homeDir + "/databases");
        newpipeDb = new File(homeDir + "/databases/newpipe.db");
        newpipeDbJournal = new File(homeDir + "/databases/newpipe.db-journal");
        newpipeDbShm = new File(homeDir + "/databases/newpipe.db-shm");
        newpipeDbWal = new File(homeDir + "/databases/newpipe.db-wal");

        newpipeSettings = new File(homeDir + "/databases/newpipe.settings");
        newpipeSettings.delete();

        addPreferencesFromResource(R.xml.content_settings);

        final Preference importDataPreference = findPreference(getString(R.string.import_data));
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            startActivityForResult(StoredFileHelper.getPicker(getContext()), REQUEST_IMPORT_PATH);
            return true;
        });

        final Preference exportDataPreference = findPreference(getString(R.string.export_data));
        exportDataPreference.setOnPreferenceClickListener((final Preference p) -> {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            startActivityForResult(StoredFileHelper.getNewPicker(getContext(), null,
                    "NewPipeData-" + sdf.format(new Date()) + ".zip"), REQUEST_EXPORT_PATH);
            return true;
        });

        final Preference addExtensionPreference = findPreference(getString(R.string.add_extension));
        addExtensionPreference.setOnPreferenceClickListener((Preference p) -> {
            startActivityForResult(StoredFileHelper.getPicker(getContext()), REQUEST_ADD_EXTENSION);
            return true;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage = PreferenceManager
                .getDefaultSharedPreferences(getContext()).getString("app_language_key", "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 @NonNull final Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: "
                    + "requestCode = [" + requestCode + "], "
                    + "resultCode = [" + resultCode + "], "
                    + "data = [" + data + "]");
        }

        if ((requestCode == REQUEST_IMPORT_PATH || requestCode == REQUEST_EXPORT_PATH
                || requestCode == REQUEST_ADD_EXTENSION)
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
            final StoredFileHelper file = new StoredFileHelper(getContext(), data.getData(),
                    "application/zip");
            if (requestCode == REQUEST_EXPORT_PATH) {
                exportDatabase(file);
            } else if (requestCode == REQUEST_IMPORT_PATH) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.override_current_data)
                        .setPositiveButton(R.string.finish,
                                (DialogInterface d, int id) -> importDatabase(file))
                        .setNegativeButton(R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel());
                builder.create().show();
            } else if (requestCode == REQUEST_ADD_EXTENSION) {
                String name = null;
                String author = null;
                // check if file is supported
                try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                        new SharpInputStream(file.getStream())))) {
                    boolean hasDex = false;
                    boolean hasIcon = false;
                    ZipEntry zipEntry;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        switch (zipEntry.getName()) {
                            case "about.json":
                                final JsonObject jsonObject
                                        = JsonParser.object().from(zipInputStream);
                                if (!jsonObject.getString("version")
                                        .equals(BuildConfig.VERSION_NAME)) {
                                    throw new IOException(
                                            "Extension is for different NewPipe version");
                                }
                                if (jsonObject.has("replaces")
                                        && jsonObject.getInt("replaces")
                                        >= ServiceList.builtinServices) {
                                    throw new IOException(
                                            "Extension replaces not existing service");
                                }
                                name = jsonObject.getString("name");
                                author = jsonObject.getString("author");
                                break;
                            case "classes.dex":
                                hasDex = true;
                                break;
                            case "icon.png":
                                hasIcon = true;
                                break;
                        }
                        zipInputStream.closeEntry();
                    }
                    if (!hasDex || !hasIcon || name == null || author == null) {
                        throw new IOException("Invalid zip");
                    }
                } catch (IOException | JsonParserException e) {
                    Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                            .show();
                }
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(getString(R.string.add_extension_dialog, name, author))
                        .setPositiveButton(R.string.finish,
                                (DialogInterface d, int id) -> addExtension(file))
                        .setNegativeButton(R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel());
                builder.create().show();
            }
        }
    }

    private void exportDatabase(final StoredFileHelper file) {
        try {
            //checkpoint before export
            NewPipeDatabase.checkpoint();

            file.create();
            final ZipOutputStream outZip = new ZipOutputStream(new BufferedOutputStream(
                    new SharpOutputStream(file.getStream())));
            ZipHelper.addFileToZip(outZip, newpipeDb.getPath(), "newpipe.db");

            saveSharedPreferencesToFile(newpipeSettings);
            ZipHelper.addFileToZip(outZip, newpipeSettings.getPath(), "newpipe.settings");

            outZip.close();

            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT)
                    .show();
        } catch (Exception e) {
            onError(e);
        }
    }

    private void saveSharedPreferencesToFile(final File dst) {
        ObjectOutputStream output = null;
        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
            output.writeObject(pref.getAll());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
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

    private void importDatabase(final StoredFileHelper file) {
        // check if file is supported
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                new SharpInputStream(file.getStream())))) {
            if (zipInputStream.getNextEntry() == null) {
                throw new IOException("Empty zip");
            }
        } catch (IOException ioe) {
            Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        try {
            if (!databasesDir.exists() && !databasesDir.mkdir()) {
                throw new Exception("Could not create databases dir");
            }

            final boolean isDbFileExtracted = ZipHelper.extractFileFromZip(file,
                    newpipeDb.getPath(), "newpipe.db");

            if (isDbFileExtracted) {
                newpipeDbJournal.delete();
                newpipeDbWal.delete();
                newpipeDbShm.delete();
            } else {
                Toast.makeText(getContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                        .show();
            }

            //If settings file exist, ask if it should be imported.
            if (ZipHelper.extractFileFromZip(file, newpipeSettings.getPath(),
                    "newpipe.settings")) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
                alert.setTitle(R.string.import_settings);

                alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                    // restart app to properly load db
                    System.exit(0);
                });
                alert.setPositiveButton(getString(R.string.finish), (dialog, which) -> {
                    dialog.dismiss();
                    loadSharedPreferences(newpipeSettings);
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

    private void addExtension(final StoredFileHelper file) {
        final String path = getActivity().getApplicationInfo().dataDir + "/extensions/"
                + file.getName() + "/";

        final File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            ZipHelper.extractFileFromZip(file, path + "about.json", "about.json");
            ZipHelper.extractFileFromZip(file, path + "classes.dex", "classes.dex");
            ZipHelper.extractFileFromZip(file, path + "icon.png", "icon.png");

            Toast.makeText(getContext(), R.string.add_extension_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.add_extension_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSharedPreferences(final File src) {
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager
                    .getDefaultSharedPreferences(getContext()).edit();
            prefEdit.clear();
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    prefEdit.putBoolean(key, (Boolean) v);
                } else if (v instanceof Float) {
                    prefEdit.putFloat(key, (Float) v);
                } else if (v instanceof Integer) {
                    prefEdit.putInt(key, (Integer) v);
                } else if (v instanceof Long) {
                    prefEdit.putLong(key, (Long) v);
                } else if (v instanceof String) {
                    prefEdit.putString(key, (String) v);
                }
            }
            prefEdit.commit();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
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

    protected void onError(final Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e,
                activity.getClass(),
                null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash));
    }
}
