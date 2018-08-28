package org.schabi.newpipe.download;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class ExtSDDownloadFailedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        new AlertDialog.Builder(this)
                .setTitle(R.string.download_to_sdcard_error_title)
                .setMessage(R.string.download_to_sdcard_error_message)
                .setPositiveButton(R.string.yes, (DialogInterface dialogInterface, int i) -> {
                    NewPipeSettings.resetDownloadFolders(this);
                    finish();
                })
                .setNegativeButton(R.string.cancel, (DialogInterface dialogInterface, int i) -> {
                    dialogInterface.dismiss();
                    finish();
                })
                .create()
                .show();
    }
}
