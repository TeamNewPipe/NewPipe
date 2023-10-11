package org.schabi.newpipe.settings.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.BasePreferenceFragment;

public class ManageAccounts extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (getString(R.string.sign_in).equals(preference.getKey())) {
            try {
                final Intent intent = new Intent(requireContext(), SignInActivity.class);
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }
//        if (getString(R.string.sign_out).equals(preference.getKey())) {
//            try {
//                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
//            } catch (final ActivityNotFoundException e) {
//                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
//            }
//        }
        if (getString(R.string.manage_accounts).equals(preference.getKey())) {
            try {
                final Intent intent = new Intent(requireContext(), ManageAccounts.class);
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
    }
}
