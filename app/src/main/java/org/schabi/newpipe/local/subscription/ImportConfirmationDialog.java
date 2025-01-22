package org.schabi.newpipe.local.subscription;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput;
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker;

public class ImportConfirmationDialog extends DialogFragment {
    @State
    protected SubscriptionImportInput input;

    public static void show(@NonNull final Fragment fragment, final SubscriptionImportInput input) {
        final var confirmationDialog = new ImportConfirmationDialog();
        confirmationDialog.input = input;
        confirmationDialog.show(fragment.getParentFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final var context = requireContext();
        assureCorrectAppLanguage(context);
        return new AlertDialog.Builder(context)
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    final var constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();

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
