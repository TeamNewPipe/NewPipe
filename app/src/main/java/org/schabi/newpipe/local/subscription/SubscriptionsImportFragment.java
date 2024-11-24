package org.schabi.newpipe.local.subscription;

import static org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.ContentSource.CHANNEL_URL;
import static org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.CHANNEL_URL_MODE;
import static org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.INPUT_STREAM_MODE;
import static org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_MODE;
import static org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_VALUE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.core.text.util.LinkifyCompat;

import com.evernote.android.state.State;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Collections;
import java.util.List;

public class SubscriptionsImportFragment extends BaseFragment {
    @State
    int currentServiceId = Constants.NO_SERVICE_ID;

    private List<SubscriptionExtractor.ContentSource> supportedSources;
    private String relatedUrl;

    @StringRes
    private int instructionsString;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private TextView infoTextView;
    private EditText inputText;
    private Button inputButton;

    private final ActivityResultLauncher<Intent> requestImportFileLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestImportFileResult);

    public static SubscriptionsImportFragment getInstance(final int serviceId) {
        final SubscriptionsImportFragment instance = new SubscriptionsImportFragment();
        instance.setInitialData(serviceId);
        return instance;
    }

    private void setInitialData(final int serviceId) {
        this.currentServiceId = serviceId;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupServiceVariables();
        if (supportedSources.isEmpty() && currentServiceId != Constants.NO_SERVICE_ID) {
            ErrorUtil.showSnackbar(activity,
                    new ErrorInfo(new String[]{}, UserAction.SUBSCRIPTION_IMPORT_EXPORT,
                            ServiceHelper.getNameOfServiceById(currentServiceId),
                            "Service does not support importing subscriptions",
                            R.string.general_error));
            activity.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.import_title));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import, container, false);
    }

    /*/////////////////////////////////////////////////////////////////////////
    // Fragment Views
    /////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        inputButton = rootView.findViewById(R.id.input_button);
        inputText = rootView.findViewById(R.id.input_text);

        infoTextView = rootView.findViewById(R.id.info_text_view);

        // TODO: Support services that can import from more than one source
        //  (show the option to the user)
        if (supportedSources.contains(CHANNEL_URL)) {
            inputButton.setText(R.string.import_title);
            inputText.setVisibility(View.VISIBLE);
            inputText.setHint(ServiceHelper.getImportInstructionsHint(currentServiceId));
        } else {
            inputButton.setText(R.string.import_file_title);
        }

        if (instructionsString != 0) {
            if (TextUtils.isEmpty(relatedUrl)) {
                setInfoText(getString(instructionsString));
            } else {
                setInfoText(getString(instructionsString, relatedUrl));
            }
        } else {
            setInfoText("");
        }

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            setTitle(getString(R.string.import_title));
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        inputButton.setOnClickListener(v -> onImportClicked());
    }

    private void onImportClicked() {
        if (inputText.getVisibility() == View.VISIBLE) {
            final String value = inputText.getText().toString();
            if (!value.isEmpty()) {
                onImportUrl(value);
            }
        } else {
            onImportFile();
        }
    }

    public void onImportUrl(final String value) {
        ImportConfirmationDialog.show(this, new Intent(activity, SubscriptionsImportService.class)
                .putExtra(KEY_MODE, CHANNEL_URL_MODE)
                .putExtra(KEY_VALUE, value)
                .putExtra(Constants.KEY_SERVICE_ID, currentServiceId));
    }

    public void onImportFile() {
        NoFileManagerSafeGuard.launchSafe(
                requestImportFileLauncher,
                // leave */* mime type to support all services
                // with different mime types and file extensions
                StoredFileHelper.getPicker(activity, "*/*"),
                TAG,
                getContext()
        );
    }

    private void requestImportFileResult(final ActivityResult result) {
        if (result.getData() == null) {
            return;
        }

        if (result.getResultCode() == Activity.RESULT_OK && result.getData().getData() != null) {
            ImportConfirmationDialog.show(this,
                    new Intent(activity, SubscriptionsImportService.class)
                            .putExtra(KEY_MODE, INPUT_STREAM_MODE)
                            .putExtra(KEY_VALUE, result.getData().getData())
                            .putExtra(Constants.KEY_SERVICE_ID, currentServiceId));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions
    ///////////////////////////////////////////////////////////////////////////

    private void setupServiceVariables() {
        if (currentServiceId != Constants.NO_SERVICE_ID) {
            try {
                final SubscriptionExtractor extractor = NewPipe.getService(currentServiceId)
                        .getSubscriptionExtractor();
                supportedSources = extractor.getSupportedSources();
                relatedUrl = extractor.getRelatedUrl();
                instructionsString = ServiceHelper.getImportInstructions(currentServiceId);
                return;
            } catch (final ExtractionException ignored) {
            }
        }

        supportedSources = Collections.emptyList();
        relatedUrl = null;
        instructionsString = 0;
    }

    private void setInfoText(final String infoString) {
        infoTextView.setText(infoString);
        LinkifyCompat.addLinks(infoTextView, Linkify.WEB_URLS);
    }
}
