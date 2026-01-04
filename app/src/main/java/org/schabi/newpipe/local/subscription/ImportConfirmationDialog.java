package org.schabi.newpipe.local.subscription;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;

public class ImportConfirmationDialog extends DialogFragment {
    @State
    protected Intent resultServiceIntent;
    private static final String EXTRA_RESULT_SERVICE_INTENT = "extra_result_service_intent";

    public static void show(@NonNull final Fragment fragment,
                            @NonNull final Intent resultServiceIntent) {
        final ImportConfirmationDialog confirmationDialog = new ImportConfirmationDialog();
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_RESULT_SERVICE_INTENT, resultServiceIntent);
        confirmationDialog.setArguments(args);
        confirmationDialog.show(fragment.getParentFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    if (resultServiceIntent != null && getContext() != null) {
                        getContext().startService(resultServiceIntent);
                    }
                    dismiss();
                })
                .create();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resultServiceIntent = requireArguments().getParcelable(EXTRA_RESULT_SERVICE_INTENT);

        Bridge.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
}
