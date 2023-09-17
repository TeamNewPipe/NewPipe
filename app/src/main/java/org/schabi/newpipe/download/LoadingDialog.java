package org.schabi.newpipe.download;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DownloadLoadingDialogBinding;

/**
 * This class contains a dialog which shows a loading indicator and has a customizable title.
 */
public class LoadingDialog extends DialogFragment {
    private static final String TAG = "LoadingDialog";
    private static final boolean DEBUG = MainActivity.DEBUG;
    private DownloadLoadingDialogBinding dialogLoadingBinding;
    private final @StringRes int title;

    /**
     * Create a new LoadingDialog.
     *
     * <p>
     *     The dialog contains a loading indicator and has a customizable title.
     *     <br/>
     *     Use {@code show()} to display the dialog to the user.
     * </p>
     *
     * @param title an informative title shown in the dialog's toolbar
     */
    public LoadingDialog(final @StringRes int title) {
        this.title = title;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        this.setCancelable(false);
    }

    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        return inflater.inflate(R.layout.download_loading_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dialogLoadingBinding = DownloadLoadingDialogBinding.bind(view);
        initToolbar(dialogLoadingBinding.toolbarLayout.toolbar);
    }

    private void initToolbar(final Toolbar toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        }
        toolbar.setTitle(requireContext().getString(title));
        toolbar.setNavigationOnClickListener(v -> dismiss());

    }

    @Override
    public void onDestroyView() {
        dialogLoadingBinding = null;
        super.onDestroyView();
    }
}
