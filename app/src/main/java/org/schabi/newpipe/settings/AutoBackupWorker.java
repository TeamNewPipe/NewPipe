package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.BackupRestoreHelper;

import java.io.File;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        BackupRestoreHelper backupRestoreHelper = new BackupRestoreHelper(ctx);
        String autoBackupPath = backupRestoreHelper.getAutoBackupPath();
        try {
            new File(autoBackupPath).mkdirs();
            String path = autoBackupPath + File.separator + "NewPipeData-" + Build.MODEL + ".zip";
            String password = PreferenceManager.getDefaultSharedPreferences(ctx).getString(ctx.getString(R.string.backup_password_key), null);
            if(TextUtils.isEmpty(password)) return Result.failure();
            backupRestoreHelper.exportDatabase(path, password.toCharArray());
        } catch (Exception e) {
            return Result.failure();
        }
        return Result.success();
    }
}
