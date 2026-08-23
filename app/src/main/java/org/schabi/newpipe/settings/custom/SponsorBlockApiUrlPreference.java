package org.schabi.newpipe.settings.custom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;

public class SponsorBlockApiUrlPreference extends Preference {
    public SponsorBlockApiUrlPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    protected void onSetInitialValue(@Nullable final Object defaultValue) {
        // apparently this is how you're supposed to respect default values for a custom preference
        persistString(getPersistedString((String) defaultValue));
    }

    @Nullable
    @Override
    protected Object onGetDefaultValue(@NonNull final TypedArray a, final int index) {
        return a.getString(index);
    }

    @Override
    protected void onClick() {
        super.onClick();

        final Context context = getContext();

        final String apiUrl = getPersistedString(null);

        final View alertDialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_sponsor_block_api_url, null);

        final EditText editText = alertDialogView.findViewById(R.id.api_url_edit);
        editText.setText(apiUrl);
        editText.setOnFocusChangeListener((v, hasFocus) -> editText.post(() -> {
            final InputMethodManager inputMethodManager = (InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager
                    .showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }));
        editText.requestFocus();

        alertDialogView.findViewById(R.id.icon_api_url_help)
                .setOnClickListener(v -> {
                    final Uri privacyPolicyUri = Uri.parse(context
                            .getString(R.string.sponsor_block_privacy_policy_url));
                    final View helpDialogView = LayoutInflater.from(context)
                            .inflate(R.layout.dialog_sponsor_block_api_url_help, null);
                    final View privacyPolicyButton = helpDialogView
                            .findViewById(R.id.sponsor_block_privacy_policy_button);
                    privacyPolicyButton.setOnClickListener(v1 -> {
                        final Intent i = new Intent(Intent.ACTION_VIEW, privacyPolicyUri);
                        context.startActivity(i);
                    });

                    new AlertDialog.Builder(context)
                            .setView(helpDialogView)
                            .setPositiveButton("Use Official", (dialog, which) -> {
                                editText.setText(context
                                        .getString(R.string.sponsor_block_default_api_url));
                                dialog.dismiss();
                            })
                            .setNeutralButton("Close", (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                });

        final AlertDialog alertDialog =
                new AlertDialog.Builder(context)
                        .setView(alertDialogView)
                        .setTitle(context.getString(R.string.sponsor_block_api_url_title))
                        .setPositiveButton("OK", (dialog, which) -> {
                            final String newValue = editText.getText().toString();
                            if (!newValue.isEmpty()) {
                                final SharedPreferences.Editor editor =
                                        getPreferenceManager().getSharedPreferences().edit();
                                editor.putString(getKey(), newValue);
                                editor.apply();

                                callChangeListener(newValue);
                            }
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                        .create();

        alertDialog.show();
    }
}
