package org.schabi.newpipe.local.subscription;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput;
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker;

public class ImportConfirmationDialog extends DialogFragment {
    private static final String INPUT = "input";

    public static void show(@NonNull final Fragment fragment, final SubscriptionImportInput input) {
        final var confirmationDialog = new ImportConfirmationDialog();
        final var arguments = new Bundle();
        arguments.putParcelable(INPUT, input);
        confirmationDialog.setArguments(arguments);
        confirmationDialog.show(fragment.getParentFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final var context = requireContext();
        return new AlertDialog.Builder(context)
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    final var constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();
                    final var input = BundleCompat.getParcelable(requireArguments(), INPUT,
                            SubscriptionImportInput.class);

                    final var req = new OneTimeWorkRequest.Builder(SubscriptionImportWorker.class)
                            .setInputData(input.toData())
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setConstraints(constraints)
                            .build();

                    WorkManager.getInstance(context)
                            .enqueueUniqueWork(SubscriptionImportWorker.WORK_NAME,
                                    ExistingWorkPolicy.APPEND_OR_REPLACE, req);

                    dismiss();
                })
                .create();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bridge.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
}
