package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.profiles.KidsMode;
import org.schabi.newpipe.profiles.KidsModeDialogs;
import org.schabi.newpipe.profiles.ProfileDeletionManager;
import org.schabi.newpipe.profiles.ProfileIcon;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.profiles.ProfilePolicy;
import org.schabi.newpipe.profiles.ProfileRecord;
import org.schabi.newpipe.profiles.ProfileTransferDialog;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class ProfilesSettingsFragment extends BasePreferenceFragment {
    private static final String KEY_ACTIVE = "profile_active";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_DESCRIPTION = "profile_description";
    private static final String KEY_ICON = "profile_icon";
    private static final String KEY_CREATE = "profile_create";
    private static final String KEY_ACTIONS = "profile_actions";

    private ListPreference activePreference;
    private EditTextPreference namePreference;
    private EditTextPreference descriptionPreference;
    private ListPreference iconPreference;
    private Preference actionsPreference;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        activePreference = findPreference(KEY_ACTIVE);
        namePreference = findPreference(KEY_NAME);
        descriptionPreference = findPreference(KEY_DESCRIPTION);
        iconPreference = findPreference(KEY_ICON);
        actionsPreference = findPreference(KEY_ACTIONS);
        final Preference createPreference = findPreference(KEY_CREATE);

        configureNonPersistentPreferences();
        configureIconPreference();
        requirePreference(R.string.learning_profile_included_key)
                .setOnPreferenceChangeListener((preference, value) -> {
                    LearningMode.setProfileIncluded(requireContext(),
                            ProfileManager.getActiveProfileId(requireContext()), (Boolean) value);
                    return true;
                });
        final SwitchPreferenceCompat kidsMode = findPreference("profile_kids_mode");
        kidsMode.setOnPreferenceChangeListener((preference, value) -> {
            KidsModeDialogs.configure(requireContext(), (Boolean) value, this::refreshPreferences);
            return false;
        });
        findPreference("profile_parent_pin").setOnPreferenceClickListener(preference -> {
            KidsModeDialogs.changePin(requireContext());
            return true;
        });
        bindListeners(createPreference);
        refreshPreferences();
    }

    private void configureNonPersistentPreferences() {
        activePreference.setPersistent(false);
        namePreference.setPersistent(false);
        descriptionPreference.setPersistent(false);
        iconPreference.setPersistent(false);

        namePreference.setOnBindEditTextListener(editText -> editText.setFilters(
                new InputFilter[] {new InputFilter.LengthFilter(ProfilePolicy.MAX_NAME_LENGTH)}));
        descriptionPreference.setOnBindEditTextListener(editText -> editText.setFilters(
                new InputFilter[] {
                    new InputFilter.LengthFilter(ProfilePolicy.MAX_DESCRIPTION_LENGTH)
                }));
    }

    private void configureIconPreference() {
        final ProfileIcon[] icons = ProfileIcon.values();
        final CharSequence[] entries = new CharSequence[icons.length];
        final CharSequence[] values = new CharSequence[icons.length];
        for (int i = 0; i < icons.length; i++) {
            entries[i] = getString(iconLabel(icons[i]));
            values[i] = icons[i].getKey();
        }
        iconPreference.setEntries(entries);
        iconPreference.setEntryValues(values);
    }

    private void bindListeners(@NonNull final Preference createPreference) {
        activePreference.setOnPreferenceChangeListener((preference, value) -> {
            final String profileId = String.valueOf(value);
            if (!ProfileManager.setActiveProfile(requireContext(), profileId)) {
                return false;
            }
            if (org.schabi.newpipe.util.PluginProfileSession.restartIfChanged(requireActivity())) {
                return true;
            }
            refreshPreferences();
            final ProfileRecord active = ProfileManager.getActiveProfile(requireContext());
            Toast.makeText(
                    requireContext(),
                    getString(
                            R.string.profile_switched,
                            ProfileManager.getDisplayName(requireContext(), active)),
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        });

        namePreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(String.valueOf(value), null, null));

        descriptionPreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(null, String.valueOf(value), null));

        iconPreference.setOnPreferenceChangeListener((preference, value) ->
                updateCurrentProfile(null, null, String.valueOf(value)));

        createPreference.setOnPreferenceClickListener(preference -> {
            showCreateProfileDialog();
            return true;
        });

        actionsPreference.setOnPreferenceClickListener(preference -> {
            showProfileActionsDialog();
            return true;
        });
    }

    private boolean updateCurrentProfile(final String name,
                                         final String description,
                                         final String iconKey) {
        final ProfileRecord current = ProfileManager.getActiveProfile(requireContext());
        final String nextName = name == null ? current.getName() : name;
        final String nextDescription = description == null
                ? current.getDescription() : description;
        final String nextIcon = iconKey == null ? current.getIconKey() : iconKey;
        final boolean updated = ProfileManager.updateProfile(
                requireContext(),
                current.getId(),
                nextName,
                nextDescription,
                nextIcon
        );
        if (!updated) {
            Toast.makeText(
                    requireContext(),
                    R.string.profile_name_invalid,
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }
        refreshPreferences();
        return true;
    }

    private void showCreateProfileDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.profile_create_hint);
        input.setSingleLine(true);
        input.setFilters(
                new InputFilter[] {
                    new InputFilter.LengthFilter(ProfilePolicy.MAX_NAME_LENGTH)
                });
        final int horizontalPadding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(horizontalPadding, input.getPaddingTop(),
                horizontalPadding, input.getPaddingBottom());

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_create_dialog_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, null);

        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                    final ProfileRecord created = ProfileManager.createProfile(
                            requireContext(),
                            input.getText().toString(),
                            "",
                            ProfileIcon.PERSON.getKey()
                    );
                    if (created == null) {
                        input.setError(getString(R.string.profile_name_invalid));
                        return;
                    }
                    dialog.dismiss();
                    if (org.schabi.newpipe.util.PluginProfileSession
                            .restartIfChanged(requireActivity())) {
                        return;
                    }
                    refreshPreferences();
                    Toast.makeText(
                            requireContext(),
                            R.string.profile_created,
                            Toast.LENGTH_SHORT
                    ).show();
                }));
        dialog.show();
    }

    private void showProfileActionsDialog() {
        final ProfileRecord current = ProfileManager.getActiveProfile(requireContext());
        final String displayName = ProfileManager.getDisplayName(requireContext(), current);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.profile_actions_dialog_title, displayName))
                .setItems(
                        new CharSequence[] {getString(R.string.profile_make_default),
                            getString(R.string.profile_transfer_action),
                            getString(R.string.profile_delete_title)},
                        (dialog, which) -> {
                            if (which == 0) {
                                ProfileManager.setDefaultProfile(requireContext(), current.getId());
                                refreshPreferences();
                                Toast.makeText(requireContext(), R.string.profile_default_changed,
                                        Toast.LENGTH_SHORT).show();
                            } else if (which == 1) {
                                ProfileTransferDialog.show(this, disposables,
                                        this::refreshPreferences);
                            } else {
                                showDeleteProfileDialog();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteProfileDialog() {
        final ProfileRecord current = ProfileManager.getActiveProfile(requireContext());
        if (!ProfileManager.canDeleteProfile(requireContext(), current.getId())) {
            Toast.makeText(
                    requireContext(),
                    R.string.profile_delete_choose_default,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final String displayName = ProfileManager.getDisplayName(requireContext(), current);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.profile_delete_confirmation_title, displayName))
                .setMessage(R.string.profile_delete_confirmation_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        disposables.add(
                                ProfileDeletionManager.deleteProfile(
                                                requireContext(),
                                                current.getId())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(
                                                () -> {
                                                    if (!isAdded()) {
                                                        return;
                                                    }
                                                    if (org.schabi.newpipe.util.PluginProfileSession
                                                            .restartIfChanged(requireActivity())) {
                                                        return;
                                                    }
                                                    refreshPreferences();
                                                    Toast.makeText(
                                                            requireContext(),
                                                            R.string.profile_deleted,
                                                            Toast.LENGTH_SHORT
                                                    ).show();
                                                },
                                                error -> {
                                                    if (isAdded()) {
                                                        Toast.makeText(
                                                                requireContext(),
                                                                error.getMessage(),
                                                                Toast.LENGTH_LONG
                                                        ).show();
                                                    }
                                                }
                                        )
                        ))
                .show();
    }

    @Override
    public void onDestroy() {
        disposables.dispose();
        super.onDestroy();
    }

    private void refreshPreferences() {
        final List<ProfileRecord> profiles = ProfileManager.getProfiles(requireContext());
        final CharSequence[] entries = new CharSequence[profiles.size()];
        final CharSequence[] values = new CharSequence[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            entries[i] = ProfileManager.getDisplayName(requireContext(), profiles.get(i));
            values[i] = profiles.get(i).getId();
        }

        final ProfileRecord active = ProfileManager.getActiveProfile(requireContext());
        final SwitchPreferenceCompat kidsMode = findPreference("profile_kids_mode");
        kidsMode.setChecked(KidsMode.isEnabled(requireContext(), active.getId()));
        activePreference.setEntries(entries);
        final SwitchPreferenceCompat learning = requirePreference(
                R.string.learning_profile_included_key);
        learning.setChecked(LearningMode.isProfileIncluded(requireContext(), active.getId()));
        learning.setSummary(getString(R.string.learning_profile_included_summary,
                ProfileManager.getDisplayName(requireContext(), active)));
        activePreference.setEntryValues(values);
        activePreference.setValue(active.getId());
        activePreference.setSummary(ProfileManager.getDisplayName(requireContext(), active));

        namePreference.setText(ProfileManager.getDisplayName(requireContext(), active));
        descriptionPreference.setText(active.getDescription());
        descriptionPreference.setSummary(active.getDescription().trim().isEmpty()
                ? getString(R.string.profile_description_summary)
                : active.getDescription());

        iconPreference.setValue(active.getIconKey());
        iconPreference.setSummary(iconPreference.getEntry());

        actionsPreference.setEnabled(profiles.size() > 1);
    }

    private int iconLabel(@NonNull final ProfileIcon icon) {
        switch (icon) {
            case WORK:
                return R.string.profile_icon_work;
            case STUDY:
                return R.string.profile_icon_study;
            case ENTERTAINMENT:
                return R.string.profile_icon_entertainment;
            case MUSIC:
                return R.string.profile_icon_music;
            case FAVORITES:
                return R.string.profile_icon_favorites;
            case PERSON:
            default:
                return R.string.profile_icon_person;
        }
    }
}
