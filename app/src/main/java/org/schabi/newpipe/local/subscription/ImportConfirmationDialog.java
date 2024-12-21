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
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.evernote.android.state.State;
import com.livefront.bridge.Bridge;

import org.schabi.newpipe.R;
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker;
import org.schabi.newpipe.util.Constants;

public class ImportConfirmationDialog extends DialogFragment {
    @State
    protected int mode;
    @State
    protected String value;
    @State
    protected int serviceId;

    public static void show(@NonNull final Fragment fragment, final int mode,
                            @Nullable final String value, final int serviceId) {
        final var confirmationDialog = new ImportConfirmationDialog();
        confirmationDialog.setData(mode, value, serviceId);
        confirmationDialog.show(fragment.getParentFragmentManager(), null);
    }

    @SuppressWarnings("HiddenField")
    public void setData(final int mode, final String value, final int serviceId) {
        this.mode = mode;
        this.value = value;
        this.serviceId = serviceId;
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
                    final var inputData = new Data.Builder()
                            .putString(SubscriptionImportWorker.KEY_VALUE, value)
                            .putInt(SubscriptionImportWorker.KEY_MODE, mode)
                            .putInt(Constants.KEY_SERVICE_ID, serviceId)
                            .build();
                    final var constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();

                    final var req = new OneTimeWorkRequest.Builder(SubscriptionImportWorker.class)
                            .setInputData(inputData)
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

        if (mode == 0 && value == null && serviceId == 0) {
            throw new IllegalStateException("Input data not provided");
        }

        Bridge.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Bridge.saveInstanceState(this, outState);
    }
}
