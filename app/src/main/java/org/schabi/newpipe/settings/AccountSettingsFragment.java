package org.schabi.newpipe.settings;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.widget.Toast;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.auth.AuthService;
import org.schabi.newpipe.database.RemoteDatabase;
import org.schabi.newpipe.util.NavigationHelper;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class AccountSettingsFragment extends BasePreferenceFragment {

    private CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.account_settings);

        Context context = getContext().getApplicationContext();

        EditTextPreference urlPreference = (EditTextPreference) findPreference(getString(R.string.sync_server_url_key));
        urlPreference.setSummary(urlPreference.getText());
        urlPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                preference.setSummary(o.toString());
                return true;
            }
        });

        Preference usernamePreference = findPreference(getString(R.string.username_key));
        usernamePreference.setOnPreferenceClickListener((Preference p) -> {
            if (!AuthService.getInstance(context).isLoggedIn()) {
                NavigationHelper.openLoginFragment(getFragmentManager());
            }
            return true;
        });

        Preference syncPreference = findPreference(getString(R.string.sync_key));
        syncPreference.setOnPreferenceClickListener((Preference p) -> {
            Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show();
            RemoteDatabase remoteDatabase = (RemoteDatabase) NewPipeDatabase.getInstance(context);
            Disposable disposable = remoteDatabase.sync().observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show(),
                            e -> Toast.makeText(context, "Sync failed", Toast.LENGTH_SHORT).show());
            disposables.add(disposable);
            return true;
        });

        Preference logoutPreference = findPreference(getString(R.string.logout_key));
        logoutPreference.setOnPreferenceClickListener((Preference p) -> {
            Disposable disposable = AuthService.getInstance(context).logout().observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {
                Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show();
                // go to main activity
                NavigationHelper.openMainActivity(getContext());
            }, e -> Toast.makeText(context, "Logout failed", Toast.LENGTH_SHORT).show());
            disposables.add(disposable);
            return true;
        });

        if(AuthService.getInstance(context).isLoggedIn()){
            String username = AuthService.getInstance(context).getUsername();
            usernamePreference.setTitle(username);
            usernamePreference.setSummary("Logged in as " + username);
            urlPreference.setEnabled(false);
            usernamePreference.setEnabled(false);
            syncPreference.setEnabled(true);
            logoutPreference.setEnabled(true);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
        disposables = null;
    }

}
