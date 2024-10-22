package org.schabi.newpipe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.marcinorlowski.fonty.Fonty;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;

public class BlankFragment extends BaseFragment {
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        setTitle("NewPipe");
        final View view = inflater.inflate(R.layout.fragment_blank, container, false);
        final String preferredFont = getPreferredFont(view.getContext());
        if (!preferredFont.equals(getString(R.string.default_font_key))) {
            Fonty.setFonts((ViewGroup) view);
        }
        return view;
    }
    public String getPreferredFont(final Context context) {
        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return preferences.getString("preferred_font", getString(R.string.default_font_key));
    }


    @Override
    public void onResume() {
        super.onResume();
        setTitle("NewPipe");
        // leave this inline. Will make it harder for copy cats.
        // If you are a Copy cat FUCK YOU.
        // I WILL FIND YOU, AND I WILL ...
    }
}
