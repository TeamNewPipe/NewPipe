package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.schabi.newpipe.database.BackupRestoreHelper;

import java.io.File;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BackupRestoreHelper backupRestoreHelper = new BackupRestoreHelper(getApplicationContext());
            String autoBackupPath = backupRestoreHelper.getAutoBackupPath();
            new File(autoBackupPath).mkdirs();
            String path = autoBackupPath + File.separator + "NewPipeData-" + Build.MODEL + ".zip";
            backupRestoreHelper.exportDatabase(path);
        } catch (Exception e) {
            return Result.failure();
        }
        return Result.success();
    }
}
