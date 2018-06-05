package org.schabi.newpipe.local.subscription;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import icepick.Icepick;
import icepick.State;

public class ImportConfirmationDialog extends DialogFragment {
    @State
    protected Intent resultServiceIntent;

    public void setResultServiceIntent(Intent resultServiceIntent) {
        this.resultServiceIntent = resultServiceIntent;
    }

    public static void show(@NonNull Fragment fragment, @NonNull Intent resultServiceIntent) {
        if (fragment.getFragmentManager() == null) return;

        final ImportConfirmationDialog confirmationDialog = new ImportConfirmationDialog();
        confirmationDialog.setResultServiceIntent(resultServiceIntent);
        confirmationDialog.show(fragment.getFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext(), ThemeHelper.getDialogTheme(getContext()))
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    if (resultServiceIntent != null && getContext() != null) {
                        getContext().startService(resultServiceIntent);
                    }
                    dismiss();
                })
                .create();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (resultServiceIntent == null) throw new IllegalStateException("Result intent is null");

        Icepick.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }
}
