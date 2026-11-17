package org.schabi.newpipe.settings.export;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.recommendations.LocalRecommendations;
import org.schabi.newpipe.streams.io.StoredFileHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ScheduledBackupWorker extends Worker {
    public static final String INTERVAL_KEY = "automatic_backup_interval";
    public static final String DIRECTORY_KEY = "automatic_backup_directory";
    public static final String LAST_SUCCESS_KEY = "automatic_backup_last_success";
    public static final String FAILED_KEY = "automatic_backup_failed";
    private static final String WORK_NAME = "automatic-backup";
    private static final int RETAINED_BACKUPS = 7;

    public ScheduledBackupWorker(@NonNull final Context context,
                                 @NonNull final WorkerParameters parameters) {
        super(context, parameters);
    }

    public static int intervalDays(final String value) {
        return "daily".equals(value) ? 1 : "weekly".equals(value) ? 7 : 0;
    }

    public static boolean isManagedBackup(final String name) {
        return name != null && name.matches("WizeStreamAuto-\\d{8}_\\d{9}\\.zip");
    }

    public static void schedule(final Context context) {
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        final int days = intervalDays(preferences.getString(INTERVAL_KEY, "off"));
        final WorkManager workManager = WorkManager.getInstance(context);
        if (days == 0 || preferences.getString(DIRECTORY_KEY, "").isEmpty()) {
            workManager.cancelUniqueWork(WORK_NAME);
            return;
        }
        final PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(ScheduledBackupWorker.class, days, TimeUnit.DAYS)
                        .setConstraints(new Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true).build())
                        .build();
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context context = getApplicationContext();
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        if (intervalDays(preferences.getString(INTERVAL_KEY, "off")) == 0) {
            return Result.success();
        }
        File snapshot = null;
        DocumentFile output = null;
        try {
            final Uri uri = Uri.parse(preferences.getString(DIRECTORY_KEY, ""));
            final DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
            if (directory == null || !directory.canWrite()) {
                throw new IOException("The backup folder is no longer writable");
            }
            snapshot = File.createTempFile("automatic-backup-", ".db", context.getCacheDir());
            DatabaseSnapshot.create(NewPipeDatabase.getInstance(context)
                    .getOpenHelper().getWritableDatabase(), snapshot);
            if (isStopped()) {
                return Result.success();
            }
            final String name = "WizeStreamAuto-"
                    + new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date())
                    + ".zip";
            output = directory.createFile("application/zip", name);
            if (output == null) {
                throw new IOException("Could not create the backup file");
            }
            final SharedPreferences localRecommendations = context.getSharedPreferences(
                    LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE);
            new ImportExportManager(new BackupFileLocator(context)).exportDatabase(preferences,
                    new StoredFileHelper(context, output.getUri(), "application/zip"),
                    snapshot.toPath(), localRecommendations);
            // Keep the new backup even if an unavailable provider prevents retention cleanup.
            output = null;
            preferences.edit().putLong(LAST_SUCCESS_KEY, System.currentTimeMillis())
                    .putBoolean(FAILED_KEY, false).apply();
            pruneBackups(directory);
            return Result.success();
        } catch (final Exception error) {
            preferences.edit().putBoolean(FAILED_KEY, true).apply();
            return getRunAttemptCount() < 2 ? Result.retry() : Result.failure();
        } finally {
            if (output != null) {
                try {
                    output.delete();
                } catch (final RuntimeException ignored) {
                    // A revoked provider permission must not escape the worker's failure result.
                }
            }
            if (snapshot != null) {
                android.database.sqlite.SQLiteDatabase.deleteDatabase(snapshot);
            }
        }
    }

    static void pruneBackups(final DocumentFile directory) {
        try {
            final DocumentFile[] backups = Arrays.stream(directory.listFiles())
                    .filter(file -> file.isFile() && isManagedBackup(file.getName()))
                    .sorted(Comparator.comparing(DocumentFile::getName).reversed())
                    .toArray(DocumentFile[]::new);
            for (int i = RETAINED_BACKUPS; i < backups.length; i++) {
                backups[i].delete();
            }
        } catch (final RuntimeException ignored) {
            // A successful backup must not be retried just because cleanup failed.
        }
    }

}
