package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

                //on M and above, if user chooses to minimise to popup player on exit and the app doesn't have
                //display over other apps permission, show a snackbar to let the user give permission
                if(s.equals(getString(R.string.minimize_on_exit_key))){

                    String newSetting = sharedPreferences.getString(s,null);
                    if(newSetting != null){

                        if(newSetting.equals(getString(R.string.minimize_on_exit_popup_key))){

                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getContext())){

                                Snackbar.make(getListView(),R.string.permission_display_over_apps,Snackbar.LENGTH_INDEFINITE)
                                        .setAction(R.string.settings, new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                PermissionHelper.checkSystemAlertWindowPermission(getContext());
                                            }
                                        })
                                        .show();

                            }
                        }
                    }
                }
            }
        };
    }



    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.video_audio_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);

    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }
}
