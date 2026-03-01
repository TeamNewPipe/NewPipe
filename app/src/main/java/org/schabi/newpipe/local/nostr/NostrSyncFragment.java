package org.schabi.newpipe.local.nostr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NostrSyncFragment extends BaseFragment {
    private static final String PREF_NOSTR_SYNC_ENABLED = "nostr_sync_enabled";
    private static final String PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED =
            "nostr_sync_watch_history_enabled";
    private static final String PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED =
            "nostr_sync_subscriptions_enabled";
    private static final String PREF_NOSTR_ENABLED_RELAYS = "nostr_enabled_relays";
    private static final String PREF_NOSTR_RELAYS = "nostr_relays";
    private static final String PREF_NOSTR_NSEC = "nostr_nsec";
    private static final String PREF_NOSTR_NPUB = "nostr_npub";
    private static final String PREF_NOSTR_EXTERNAL_SIGNER = "nostr_external_signer";
    private static final String PREF_NOSTR_SIGNER_PACKAGE = "nostr_signer_package";
    private static final String PREF_NOSTR_PROFILE_NAME = "nostr_profile_name";
    private static final String PREF_NOSTR_PROFILE_DISPLAY_NAME = "nostr_profile_display_name";
    private static final String PREF_NOSTR_PROFILE_PICTURE_URL = "nostr_profile_picture_url";
    private static final String AMBER_PACKAGE_NAME = "com.greenart7c3.nostrsigner";
    private static final String PRIMAL_PACKAGE_NAME = "net.primal.android";
    private static final String NIP55_URI = "nostrsigner:";
    private static final String NIP55_TYPE = "type";
    private static final String NIP55_TYPE_GET_PUBLIC_KEY = "get_public_key";
    private static final String NIP55_PERMISSIONS = "permissions";
    private static final String NIP55_RESULT = "result";
    private static final String NIP55_SIGNATURE = "signature";
    private static final String NIP55_PUBKEY = "pubkey";
    private static final String NIP55_NPUB = "npub";
    private static final String NIP55_PACKAGE = "package";
    private static final String NIP55_RESULTS = "results";
    private static final String NIP55_PERMISSION_TYPE = "type";
    private static final String NIP55_PERMISSION_KIND = "kind";
    private static final String NIP55_PERMISSION_SIGN_EVENT = "sign_event";
    private static final String NIP55_PERMISSION_NIP44_ENCRYPT = "nip44_encrypt";
    private static final String NIP55_PERMISSION_NIP44_DECRYPT = "nip44_decrypt";
    private static final int NOSTR_SYNC_APP_DATA_KIND = 30078;
    private static final Set<String> DEFAULT_ENABLED_RELAYS = new HashSet<>(Arrays.asList(
            "wss://relay.primal.net",
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://nostr.oxtr.dev",
            "wss://nos.lol",
            "wss://nostr.bitcoiner.social",
            "wss://nostr.semisol.dev"
    ));
    private static final String MASKED_NSEC =
            "\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF";
    private static final List<String> DEFAULT_RELAYS = Arrays.asList(
            "wss://relay.primal.net",
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://nostr.oxtr.dev",
            "wss://nos.lol",
            "wss://nostr.bitcoiner.social",
            "wss://nostr.semisol.dev",
            "wss://shu01.shugur.net",
            "wss://shu02.shugur.net",
            "wss://shu03.shugur.net",
            "wss://shu04.shugur.net",
            "wss://shu05.shugur.net"
    );

    private SharedPreferences preferences;
    private SwitchCompat syncWatchHistorySwitch;
    private SwitchCompat syncSubscriptionsSwitch;
    private Button signInButton;
    private Button showIdentityButton;
    private Button clearIdentityButton;
    private Button addRelayButton;
    private Button resetRelaysButton;
    private LinearLayout identityActionsContainer;
    private LinearLayout relaysContainer;
    @Nullable
    private AlertDialog signInDialog;
    @Nullable
    private String pendingSignerPackage;

    private final ActivityResultLauncher<ScanOptions> scanNsecLauncher =
            registerForActivityResult(new ScanContract(), this::onNsecScanResult);
    private final ActivityResultLauncher<Intent> nip55RequestLauncher =
            registerForActivityResult(new StartActivityForResult(), this::onNip55SignerResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nostr_sync, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        syncWatchHistorySwitch = rootView.findViewById(R.id.nostr_sync_watch_history_switch);
        syncSubscriptionsSwitch = rootView.findViewById(R.id.nostr_sync_subscriptions_switch);
        signInButton = rootView.findViewById(R.id.nostr_sign_in_button);
        identityActionsContainer = rootView.findViewById(R.id.nostr_identity_actions_container);
        showIdentityButton = rootView.findViewById(R.id.nostr_show_identity_button);
        clearIdentityButton = rootView.findViewById(R.id.nostr_clear_identity_button);
        addRelayButton = rootView.findViewById(R.id.nostr_add_relay_button);
        resetRelaysButton = rootView.findViewById(R.id.nostr_reset_relays_button);
        relaysContainer = rootView.findViewById(R.id.nostr_relays_container);

        final boolean legacyDefault = preferences.getBoolean(PREF_NOSTR_SYNC_ENABLED, false);
        syncWatchHistorySwitch.setChecked(preferences.getBoolean(
                PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED, legacyDefault));
        syncSubscriptionsSwitch.setChecked(preferences.getBoolean(
                PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED, legacyDefault));

        syncWatchHistorySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(
                    PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED, isChecked).apply();
            requestSyncIfEligible();
        });
        syncSubscriptionsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(
                    PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED, isChecked).apply();
            requestSyncIfEligible();
        });

        signInButton.setOnClickListener(v -> showSignInDialog());
        showIdentityButton.setOnClickListener(v -> showIdentityDialog());
        clearIdentityButton.setOnClickListener(v -> showClearIdentityDialog());
        addRelayButton.setOnClickListener(v -> showAddRelayDialog());
        resetRelaysButton.setOnClickListener(v -> resetRelaysToDefault());

        populateRelays();
        updateIdentityButtons();
    }

    @Override
    public void onDestroyView() {
        if (signInDialog != null) {
            signInDialog.dismiss();
            signInDialog = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.nostr_sync));
        requestSyncIfEligible();
    }

    private void populateRelays() {
        relaysContainer.removeAllViews();
        final List<String> relays = getRelayList();
        final Set<String> enabledRelays = getEnabledRelays(relays);

        for (final String relay : relays) {
            final LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            final CheckBox relayCheckBox = new CheckBox(requireContext());
            relayCheckBox.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            relayCheckBox.setText(relay);
            relayCheckBox.setChecked(enabledRelays.contains(relay));
            relayCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                final Set<String> updatedRelays = getEnabledRelays(getRelayList());
                if (isChecked) {
                    updatedRelays.add(relay);
                } else {
                    updatedRelays.remove(relay);
                }
                saveEnabledRelays(updatedRelays);
            });

            final ImageButton removeRelayButton = new ImageButton(requireContext());
            final int buttonSizePx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    40,
                    getResources().getDisplayMetrics()
            );
            removeRelayButton.setLayoutParams(new LinearLayout.LayoutParams(
                    buttonSizePx,
                    buttonSizePx
            ));
            removeRelayButton.setImageResource(R.drawable.ic_delete);
            removeRelayButton.setBackgroundResource(android.R.color.transparent);
            removeRelayButton.setContentDescription(getString(R.string.nostr_remove_relay));
            removeRelayButton.setOnClickListener(v -> removeRelay(relay));

            row.addView(relayCheckBox);
            row.addView(removeRelayButton);
            relaysContainer.addView(row);
        }
    }

    @NonNull
    private Set<String> getEnabledRelays(@NonNull final List<String> relays) {
        final Set<String> saved = preferences.getStringSet(PREF_NOSTR_ENABLED_RELAYS, null);
        final Set<String> allowedRelays = new HashSet<>(relays);
        if (saved != null) {
            final Set<String> filtered = new HashSet<>(saved);
            filtered.retainAll(allowedRelays);
            return filtered;
        }
        final Set<String> defaults = new HashSet<>(DEFAULT_ENABLED_RELAYS);
        defaults.retainAll(allowedRelays);
        return defaults;
    }

    @NonNull
    private List<String> getRelayList() {
        final String raw = preferences.getString(PREF_NOSTR_RELAYS, null);
        if (TextUtils.isEmpty(raw)) {
            return DEFAULT_RELAYS;
        }

        final List<String> relays = new java.util.ArrayList<>();
        final Set<String> seen = new HashSet<>();
        try {
            final JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                final String relay = jsonArray.optString(i, "").trim();
                if (TextUtils.isEmpty(relay) || !seen.add(relay)) {
                    continue;
                }
                relays.add(relay);
            }
        } catch (final JSONException ignored) {
            return DEFAULT_RELAYS;
        }
        return relays;
    }

    private void saveRelayList(@NonNull final List<String> relays) {
        final JSONArray jsonArray = new JSONArray();
        for (final String relay : relays) {
            jsonArray.put(relay);
        }
        preferences.edit().putString(PREF_NOSTR_RELAYS, jsonArray.toString()).apply();
    }

    private void saveEnabledRelays(@NonNull final Set<String> enabledRelays) {
        preferences.edit()
                .putStringSet(PREF_NOSTR_ENABLED_RELAYS, new HashSet<>(enabledRelays))
                .apply();
    }

    private void showAddRelayDialog() {
        final EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.nostr_add_relay_hint);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nostr_add_relay_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nostr_add_relay, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String relay = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(relay)
                    || (!relay.startsWith("wss://") && !relay.startsWith("ws://"))) {
                Toast.makeText(requireContext(), R.string.nostr_invalid_relay_url,
                        Toast.LENGTH_LONG).show();
                return;
            }

            final List<String> relays = new java.util.ArrayList<>(getRelayList());
            for (final String existingRelay : relays) {
                if (relay.equalsIgnoreCase(existingRelay)) {
                    Toast.makeText(requireContext(), R.string.nostr_relay_already_exists,
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            relays.add(relay);
            saveRelayList(relays);

            final Set<String> enabledRelays = getEnabledRelays(relays);
            enabledRelays.add(relay);
            saveEnabledRelays(enabledRelays);

            populateRelays();
            dialog.dismiss();
        });
    }

    private void removeRelay(@NonNull final String relay) {
        final List<String> relays = new java.util.ArrayList<>(getRelayList());
        if (!relays.remove(relay)) {
            return;
        }
        saveRelayList(relays);

        final Set<String> enabledRelays = getEnabledRelays(relays);
        enabledRelays.remove(relay);
        saveEnabledRelays(enabledRelays);
        populateRelays();
    }

    private void resetRelaysToDefault() {
        saveRelayList(DEFAULT_RELAYS);
        saveEnabledRelays(DEFAULT_ENABLED_RELAYS);
        populateRelays();
        requestSyncIfEligible();
    }

    private void showSignInDialog() {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_nostr_sign_in, null, false);
        final TextView messageView = view.findViewById(R.id.nostr_sign_in_message);
        final Button useSignerButton = view.findViewById(R.id.nostr_use_signer_button);
        final TextView installAccountManagerLabel =
                view.findViewById(R.id.nostr_install_account_manager_label);
        final LinearLayout installAccountManagerRow =
                view.findViewById(R.id.nostr_install_account_manager_row);
        final Button installPrimalButton = view.findViewById(R.id.nostr_install_primal_button);
        final Button installAmberButton = view.findViewById(R.id.nostr_install_amber_button);
        final TextView orLabel = view.findViewById(R.id.nostr_sign_in_or_label);
        final Button createAccountButton = view.findViewById(R.id.nostr_create_account_button);
        final TextView onboardingNote = view.findViewById(R.id.nostr_sign_in_onboarding_note);
        final View advancedToggle = view.findViewById(R.id.nostr_advanced_toggle);
        final View advancedContent = view.findViewById(R.id.nostr_advanced_content);
        final ImageView advancedChevron = view.findViewById(R.id.nostr_advanced_chevron);
        final Button generateKeypairButton =
                view.findViewById(R.id.nostr_generate_keys_button);
        final ImageButton scanNsecButton = view.findViewById(R.id.nostr_scan_nsec_button);
        final EditText nsecInput = view.findViewById(R.id.nostr_nsec_input);

        final boolean[] advancedVisible = {false};
        advancedContent.setVisibility(View.GONE);
        advancedChevron.setImageResource(R.drawable.ic_arrow_drop_down);
        advancedToggle.setOnClickListener(v -> {
            advancedVisible[0] = !advancedVisible[0];
            advancedContent.setVisibility(advancedVisible[0] ? View.VISIBLE : View.GONE);
            advancedChevron.setImageResource(advancedVisible[0]
                    ? R.drawable.ic_arrow_drop_up
                    : R.drawable.ic_arrow_drop_down);
        });

        final boolean hasNip55Signer = hasNip55SignerApp();

        useSignerButton.setVisibility(View.GONE);
        installAccountManagerLabel.setVisibility(View.GONE);
        installAccountManagerRow.setVisibility(View.GONE);
        orLabel.setVisibility(View.GONE);
        createAccountButton.setVisibility(View.GONE);
        onboardingNote.setVisibility(View.GONE);
        messageView.setGravity(Gravity.START);

        if (hasNip55Signer) {
            messageView.setText(R.string.nostr_sign_in_with_signer_message);
            useSignerButton.setVisibility(View.VISIBLE);
            useSignerButton.setText(buildTwoLineButtonText(R.string.nostr_log_in_with_nip55));
            useSignerButton.setOnClickListener(v -> requestNip55PublicKey());
        } else {
            messageView.setText(R.string.nostr_sign_in_no_account_message);
            messageView.setGravity(Gravity.CENTER_HORIZONTAL);
            installAccountManagerLabel.setVisibility(View.VISIBLE);
            installAccountManagerRow.setVisibility(View.VISIBLE);
            installPrimalButton.setOnClickListener(v -> ShareUtils.installApp(
                    requireContext(), PRIMAL_PACKAGE_NAME
            ));
            installAmberButton.setOnClickListener(v -> ShareUtils.installApp(
                    requireContext(), AMBER_PACKAGE_NAME
            ));
            orLabel.setVisibility(View.VISIBLE);
            createAccountButton.setVisibility(View.VISIBLE);
            onboardingNote.setVisibility(View.VISIBLE);
            createAccountButton.setOnClickListener(v -> startCreateAccountFlow());
        }

        generateKeypairButton.setOnClickListener(v -> {
            try {
                final NostrKeyUtils.NostrIdentity identity = NostrKeyUtils.generateIdentity();
                saveIdentity(identity);
                Toast.makeText(requireContext(),
                        R.string.nostr_identity_generated, Toast.LENGTH_SHORT).show();
            } catch (final RuntimeException e) {
                Toast.makeText(requireContext(),
                        R.string.nostr_identity_failed, Toast.LENGTH_LONG).show();
            }
        });
        scanNsecButton.setOnClickListener(v -> launchNsecScanner());

        signInDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nostr_sign_in)
                .setView(view)
                .setPositiveButton(R.string.done, null)
                .create();
        signInDialog.show();
        final Button doneButton = signInDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        doneButton.setOnClickListener(v -> {
            final CharSequence nsecInputText = nsecInput.getText();
            final String enteredNsec = nsecInputText == null
                    ? ""
                    : nsecInputText.toString().trim();
            if (!TextUtils.isEmpty(enteredNsec)) {
                importNsecIdentity(enteredNsec, R.string.nostr_invalid_nsec);
                return;
            }

            signInDialog.dismiss();
            signInDialog = null;
        });
    }

    private void startCreateAccountFlow() {
        final NostrKeyUtils.NostrIdentity identity;
        try {
            identity = NostrKeyUtils.generateIdentity();
        } catch (final RuntimeException e) {
            Toast.makeText(requireContext(),
                    R.string.nostr_identity_failed, Toast.LENGTH_LONG).show();
            return;
        }
        showCreateAccountDialog(identity);
    }

    private void showCreateAccountDialog(@NonNull final NostrKeyUtils.NostrIdentity identity) {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_nostr_create_account, null, false);
        final EditText nameInput = view.findViewById(R.id.nostr_profile_name_input);
        final EditText displayNameInput =
                view.findViewById(R.id.nostr_profile_display_name_input);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nostr_create_account_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = trimmedValue(nameInput);
            String displayName = trimmedValue(displayNameInput);

            if (TextUtils.isEmpty(name) && TextUtils.isEmpty(displayName)) {
                Toast.makeText(requireContext(),
                        R.string.nostr_profile_info_required, Toast.LENGTH_LONG).show();
                return;
            }
            if (TextUtils.isEmpty(name)) {
                name = displayName;
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = name;
            }
            if (!TextUtils.isEmpty(name) && containsWhitespace(name)) {
                Toast.makeText(requireContext(),
                        R.string.nostr_profile_name_no_spaces, Toast.LENGTH_LONG).show();
                return;
            }

            final LocalProfileMetadata profileMetadata = new LocalProfileMetadata(
                    name,
                    displayName
            );
            saveIdentity(identity, profileMetadata);
            NostrSyncManager.publishProfileMetadata(
                    requireContext(),
                    identity.nsec,
                    profileMetadata.name,
                    profileMetadata.displayName,
                    null
            );
            Toast.makeText(requireContext(),
                    R.string.nostr_identity_generated, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private boolean hasNip55SignerApp() {
        final Intent signerIntent = buildNip55GetPublicKeyIntent();
        return !requireContext().getPackageManager()
                .queryIntentActivities(signerIntent, 0)
                .isEmpty();
    }

    @NonNull
    private Intent buildNip55GetPublicKeyIntent() {
        final Intent signerIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(NIP55_URI));
        signerIntent.putExtra(NIP55_TYPE, NIP55_TYPE_GET_PUBLIC_KEY);
        final String permissions = buildNip55SyncPermissions();
        if (!TextUtils.isEmpty(permissions)) {
            signerIntent.putExtra(NIP55_PERMISSIONS, permissions);
        }
        return signerIntent;
    }

    @Nullable
    private String buildNip55SyncPermissions() {
        try {
            final JSONArray permissions = new JSONArray();
            permissions.put(new JSONObject()
                    .put(NIP55_PERMISSION_TYPE, NIP55_PERMISSION_SIGN_EVENT)
                    .put(NIP55_PERMISSION_KIND, NOSTR_SYNC_APP_DATA_KIND));
            permissions.put(new JSONObject()
                    .put(NIP55_PERMISSION_TYPE, NIP55_PERMISSION_NIP44_ENCRYPT));
            permissions.put(new JSONObject()
                    .put(NIP55_PERMISSION_TYPE, NIP55_PERMISSION_NIP44_DECRYPT));
            return permissions.toString();
        } catch (final JSONException e) {
            return null;
        }
    }

    private void requestNip55PublicKey() {
        requestNip55PublicKey(null);
    }

    private void requestNip55PublicKey(@Nullable final String signerPackage) {
        final Intent signerIntent = buildNip55GetPublicKeyIntent();
        if (!TextUtils.isEmpty(signerPackage)) {
            signerIntent.setPackage(signerPackage);
        }
        if (signerIntent.resolveActivity(requireContext().getPackageManager()) == null) {
            pendingSignerPackage = null;
            Toast.makeText(requireContext(),
                    R.string.nostr_signer_app_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingSignerPackage = signerPackage;
        nip55RequestLauncher.launch(signerIntent);
    }

    private void onNip55SignerResult(
            @NonNull final androidx.activity.result.ActivityResult activityResult) {
        if (activityResult.getResultCode() != Activity.RESULT_OK) {
            pendingSignerPackage = null;
            Toast.makeText(requireContext(), R.string.nostr_signer_request_cancelled,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent data = activityResult.getData();
        if (data == null) {
            pendingSignerPackage = null;
            Toast.makeText(requireContext(), R.string.nostr_signer_invalid_response,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final String signerResult = extractSignerPublicKey(data);
        if (TextUtils.isEmpty(signerResult)) {
            pendingSignerPackage = null;
            Toast.makeText(requireContext(), R.string.nostr_signer_invalid_response,
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            final String npub = NostrKeyUtils.toNpub(signerResult);
            String signerPackage = extractNip55Value(data, NIP55_PACKAGE);
            if (TextUtils.isEmpty(signerPackage)) {
                signerPackage = pendingSignerPackage;
            }
            if (TextUtils.isEmpty(signerPackage)) {
                signerPackage = resolvePreferredSignerPackage();
            }
            saveSignerIdentity(npub, signerPackage);
            pendingSignerPackage = null;
            Toast.makeText(requireContext(), R.string.nostr_signer_identity_connected,
                    Toast.LENGTH_SHORT).show();
        } catch (final RuntimeException e) {
            pendingSignerPackage = null;
            Toast.makeText(requireContext(), R.string.nostr_signer_invalid_response,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private String extractSignerPublicKey(@NonNull final Intent data) {
        final String[] candidateKeys = {
                NIP55_RESULT,
                NIP55_SIGNATURE,
                NIP55_PUBKEY,
                NIP55_NPUB
        };
        for (final String key : candidateKeys) {
            final String value = extractNip55Value(data, key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private String resolvePreferredSignerPackage() {
        final Intent signerIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(NIP55_URI));
        signerIntent.putExtra(NIP55_TYPE, NIP55_TYPE_GET_PUBLIC_KEY);
        final List<android.content.pm.ResolveInfo> candidates = requireContext().getPackageManager()
                .queryIntentActivities(signerIntent, 0);
        if (candidates.isEmpty()) {
            return null;
        }

        String selectedPackage = null;
        for (final android.content.pm.ResolveInfo candidate : candidates) {
            if (candidate.activityInfo == null
                    || TextUtils.isEmpty(candidate.activityInfo.packageName)) {
                continue;
            }
            final String packageName = candidate.activityInfo.packageName;
            if (AMBER_PACKAGE_NAME.equals(packageName)) {
                selectedPackage = packageName;
                break;
            }
            if (selectedPackage == null) {
                selectedPackage = packageName;
            }
        }
        return selectedPackage;
    }

    @Nullable
    private String extractNip55Value(@NonNull final Intent data, @NonNull final String key) {
        final String directExtra = data.getStringExtra(key);
        if (!TextUtils.isEmpty(directExtra)) {
            return directExtra;
        }

        final Uri dataUri = data.getData();
        if (dataUri != null) {
            final String queryParam = dataUri.getQueryParameter(key);
            if (!TextUtils.isEmpty(queryParam)) {
                return queryParam;
            }

            final String resultsQuery = dataUri.getQueryParameter(NIP55_RESULTS);
            if (!TextUtils.isEmpty(resultsQuery)) {
                final String jsonValue = readJsonResultValue(resultsQuery, key);
                if (!TextUtils.isEmpty(jsonValue)) {
                    return jsonValue;
                }
            }
        }

        final String resultsExtra = data.getStringExtra(NIP55_RESULTS);
        if (!TextUtils.isEmpty(resultsExtra)) {
            return readJsonResultValue(resultsExtra, key);
        }
        return null;
    }

    @Nullable
    private String readJsonResultValue(@NonNull final String rawJson, @NonNull final String key) {
        try {
            final JSONArray array = new JSONArray(rawJson);
            if (array.length() == 0) {
                return null;
            }
            final JSONObject firstResult = array.optJSONObject(0);
            if (firstResult == null) {
                return null;
            }
            final String value = firstResult.optString(key, null);
            return TextUtils.isEmpty(value) ? null : value;
        } catch (final JSONException ignored) {
            return null;
        }
    }

    private void launchNsecScanner() {
        final ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.nostr_scan_nsec_prompt));
        options.setBeepEnabled(false);
        options.setCaptureActivity(PortraitCaptureActivity.class);
        options.setOrientationLocked(true);
        scanNsecLauncher.launch(options);
    }

    private void onNsecScanResult(final ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            return;
        }
        importNsecIdentity(result.getContents(), R.string.nostr_invalid_nsec_qr);
    }

    private boolean importNsecIdentity(@Nullable final String rawNsec,
                                       final int invalidMessageResId) {
        if (TextUtils.isEmpty(rawNsec)) {
            return false;
        }
        try {
            final NostrKeyUtils.NostrIdentity identity = NostrKeyUtils
                    .fromScannedNsec(rawNsec.trim());
            saveIdentity(identity);
            Toast.makeText(requireContext(),
                    R.string.nostr_identity_imported, Toast.LENGTH_SHORT).show();
            return true;
        } catch (final RuntimeException e) {
            Toast.makeText(requireContext(), invalidMessageResId,
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void saveIdentity(@NonNull final NostrKeyUtils.NostrIdentity identity) {
        saveIdentity(identity, null);
    }

    private void saveIdentity(@NonNull final NostrKeyUtils.NostrIdentity identity,
                              @Nullable final LocalProfileMetadata profileMetadata) {
        final SharedPreferences.Editor editor = preferences.edit()
                .putString(PREF_NOSTR_NSEC, identity.nsec)
                .putString(PREF_NOSTR_NPUB, identity.npub)
                .putBoolean(PREF_NOSTR_EXTERNAL_SIGNER, false)
                .remove(PREF_NOSTR_SIGNER_PACKAGE);
        if (profileMetadata == null) {
            editor.remove(PREF_NOSTR_PROFILE_NAME)
                    .remove(PREF_NOSTR_PROFILE_DISPLAY_NAME)
                    .remove(PREF_NOSTR_PROFILE_PICTURE_URL);
        } else {
            putOrRemove(editor, PREF_NOSTR_PROFILE_NAME, profileMetadata.name);
            putOrRemove(editor, PREF_NOSTR_PROFILE_DISPLAY_NAME, profileMetadata.displayName);
            editor.remove(PREF_NOSTR_PROFILE_PICTURE_URL);
        }
        editor.apply();
        updateIdentityButtons();

        if (signInDialog != null) {
            signInDialog.dismiss();
            signInDialog = null;
        }
        requestSyncIfEligible();
    }

    private void saveSignerIdentity(@NonNull final String npub,
                                    @Nullable final String signerPackage) {
        final SharedPreferences.Editor editor = preferences.edit()
                .remove(PREF_NOSTR_NSEC)
                .putString(PREF_NOSTR_NPUB, npub)
                .putBoolean(PREF_NOSTR_EXTERNAL_SIGNER, true)
                .remove(PREF_NOSTR_PROFILE_NAME)
                .remove(PREF_NOSTR_PROFILE_DISPLAY_NAME)
                .remove(PREF_NOSTR_PROFILE_PICTURE_URL);
        if (TextUtils.isEmpty(signerPackage)) {
            editor.remove(PREF_NOSTR_SIGNER_PACKAGE);
        } else {
            editor.putString(PREF_NOSTR_SIGNER_PACKAGE, signerPackage);
        }
        editor.apply();
        updateIdentityButtons();

        if (signInDialog != null) {
            signInDialog.dismiss();
            signInDialog = null;
        }
        requestSyncIfEligible();
    }

    private void updateIdentityButtons() {
        final String nsec = preferences.getString(PREF_NOSTR_NSEC, null);
        final String npub = preferences.getString(PREF_NOSTR_NPUB, null);
        final boolean hasExternalSigner =
                preferences.getBoolean(PREF_NOSTR_EXTERNAL_SIGNER, false);
        final boolean hasIdentity = !TextUtils.isEmpty(npub)
                && (!TextUtils.isEmpty(nsec) || hasExternalSigner);

        signInButton.setVisibility(hasIdentity ? View.GONE : View.VISIBLE);
        identityActionsContainer.setVisibility(hasIdentity ? View.VISIBLE : View.GONE);
    }

    private void showClearIdentityDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.nostr_clear_identity_title)
                .setMessage(R.string.nostr_clear_identity_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nostr_clear, (dialog, which) -> clearIdentity())
                .show();
    }

    private void clearIdentity() {
        preferences.edit()
                .remove(PREF_NOSTR_NSEC)
                .remove(PREF_NOSTR_NPUB)
                .remove(PREF_NOSTR_EXTERNAL_SIGNER)
                .remove(PREF_NOSTR_SIGNER_PACKAGE)
                .remove(PREF_NOSTR_PROFILE_NAME)
                .remove(PREF_NOSTR_PROFILE_DISPLAY_NAME)
                .remove(PREF_NOSTR_PROFILE_PICTURE_URL)
                .apply();
        updateIdentityButtons();
        Toast.makeText(
                requireContext(),
                R.string.nostr_identity_cleared,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showIdentityDialog() {
        final String nsec = preferences.getString(PREF_NOSTR_NSEC, null);
        final String npub = preferences.getString(PREF_NOSTR_NPUB, null);
        final boolean hasExternalSigner = preferences.getBoolean(PREF_NOSTR_EXTERNAL_SIGNER, false);
        final boolean hasLocalNsec = !TextUtils.isEmpty(nsec);
        if (TextUtils.isEmpty(npub) || (!hasLocalNsec && !hasExternalSigner)) {
            return;
        }

        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_nostr_identity, null, false);

        final ImageView profileImage = view.findViewById(R.id.nostr_profile_image);
        final TextView npubValue = view.findViewById(R.id.nostr_npub_value);
        final ImageButton npubCopy = view.findViewById(R.id.nostr_npub_copy_button);
        final ImageView npubQr = view.findViewById(R.id.nostr_npub_qr);
        final TextView nsecLabel = view.findViewById(R.id.nostr_nsec_label);
        final LinearLayout nsecRow = view.findViewById(R.id.nostr_nsec_row);
        final TextView nsecValue = view.findViewById(R.id.nostr_nsec_value);
        final ImageButton nsecVisibilityToggle =
                view.findViewById(R.id.nostr_nsec_visibility_button);
        final ImageButton nsecCopy = view.findViewById(R.id.nostr_nsec_copy_button);
        final ImageView nsecQr = view.findViewById(R.id.nostr_nsec_qr);
        final TextView signerManagedMessage =
                view.findViewById(R.id.nostr_signer_managed_message);

        final String profilePictureUrl = preferences.getString(
                PREF_NOSTR_PROFILE_PICTURE_URL,
                null
        );
        if (TextUtils.isEmpty(profilePictureUrl)) {
            profileImage.setImageResource(R.drawable.placeholder_person);
        } else {
            CoilHelper.INSTANCE.loadAvatar(profileImage, profilePictureUrl);
        }

        npubValue.setText(npub);
        npubCopy.setOnClickListener(v -> ShareUtils.copyToClipboard(requireContext(), npub));

        final int qrSize = (int) (220 * getResources().getDisplayMetrics().density);
        try {
            npubQr.setImageBitmap(NostrKeyUtils.generateQrCode(npub, qrSize));
            if (hasLocalNsec) {
                nsecQr.setImageBitmap(NostrKeyUtils.generateQrCode(nsec, qrSize));
            }
        } catch (final RuntimeException e) {
            Toast.makeText(requireContext(), R.string.nostr_qr_generation_failed,
                    Toast.LENGTH_LONG).show();
        }

        if (hasLocalNsec) {
            signerManagedMessage.setVisibility(View.GONE);
            nsecLabel.setVisibility(View.VISIBLE);
            nsecRow.setVisibility(View.VISIBLE);
            nsecQr.setVisibility(View.VISIBLE);

            final boolean[] nsecVisible = {false};
            nsecValue.setText(MASKED_NSEC);
            nsecVisibilityToggle.setImageResource(R.drawable.ic_visibility_off);
            nsecVisibilityToggle.setOnClickListener(v -> {
                nsecVisible[0] = !nsecVisible[0];
                nsecValue.setText(nsecVisible[0] ? nsec : MASKED_NSEC);
                nsecVisibilityToggle.setImageResource(
                        nsecVisible[0]
                                ? R.drawable.ic_visibility_on
                                : R.drawable.ic_visibility_off
                );
            });
            nsecCopy.setOnClickListener(v -> ShareUtils.copyToClipboard(requireContext(), nsec));
        } else {
            nsecLabel.setVisibility(View.VISIBLE);
            nsecRow.setVisibility(View.GONE);
            nsecQr.setVisibility(View.GONE);
            signerManagedMessage.setVisibility(View.VISIBLE);
            signerManagedMessage.setText(R.string.nostr_nsec_managed_by_signer);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(buildIdentityDialogTitle())
                .setView(view)
                .setPositiveButton(R.string.done, null)
                .show();
    }

    @NonNull
    private String buildIdentityDialogTitle() {
        final String username = normalizeUsername(
                preferences.getString(PREF_NOSTR_PROFILE_NAME, null));
        final String displayName = trimmedPreferenceValue(
                preferences.getString(PREF_NOSTR_PROFILE_DISPLAY_NAME, null));
        if (!TextUtils.isEmpty(username)) {
            final String usernameWithPrefix = "@" + username;
            if (!TextUtils.isEmpty(displayName)) {
                return displayName + " (" + usernameWithPrefix + ")";
            }
            return getString(R.string.nostr_identity_title_with_username, usernameWithPrefix);
        }
        return getString(R.string.nostr_identity_title);
    }

    private void requestSyncIfEligible() {
        final boolean syncWatchHistory = preferences.getBoolean(
                PREF_NOSTR_SYNC_WATCH_HISTORY_ENABLED, false
        );
        final boolean syncSubscriptions = preferences.getBoolean(
                PREF_NOSTR_SYNC_SUBSCRIPTIONS_ENABLED, false
        );
        if (!syncWatchHistory && !syncSubscriptions) {
            return;
        }
        NostrSyncManager.requestSync(requireContext());
    }

    @NonNull
    private CharSequence buildTwoLineButtonText(@StringRes final int textResId) {
        final String rawText = getString(textResId);
        final int lineBreakIndex = rawText.indexOf('\n');
        if (lineBreakIndex < 0 || lineBreakIndex >= rawText.length() - 1) {
            return rawText;
        }

        final SpannableString spannable = new SpannableString(rawText);
        spannable.setSpan(
                new RelativeSizeSpan(0.82f),
                lineBreakIndex + 1,
                rawText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return spannable;
    }

    @Nullable
    private static String trimmedValue(@NonNull final EditText input) {
        final CharSequence value = input.getText();
        if (value == null) {
            return null;
        }
        final String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void putOrRemove(@NonNull final SharedPreferences.Editor editor,
                                    @NonNull final String key,
                                    @Nullable final String value) {
        if (TextUtils.isEmpty(value)) {
            editor.remove(key);
            return;
        }
        editor.putString(key, value);
    }

    private static boolean containsWhitespace(@NonNull final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String normalizeUsername(@Nullable final String username) {
        final String normalized = trimmedPreferenceValue(username);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        if (normalized.startsWith("@")) {
            final String withoutPrefix = normalized.substring(1).trim();
            return withoutPrefix.isEmpty() ? null : withoutPrefix;
        }
        return normalized;
    }

    @Nullable
    private static String trimmedPreferenceValue(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class LocalProfileMetadata {
        @Nullable
        private final String name;
        @Nullable
        private final String displayName;

        LocalProfileMetadata(@Nullable final String name,
                             @Nullable final String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }

}
