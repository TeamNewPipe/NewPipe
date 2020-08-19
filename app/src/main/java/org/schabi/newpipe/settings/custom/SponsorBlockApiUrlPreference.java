package org.schabi.newpipe.settings.custom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.schabi.newpipe.R;

public class SponsorBlockApiUrlPreference extends Preference {
    public SponsorBlockApiUrlPreference(final Context context, final AttributeSet attrs,
                                        final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SponsorBlockApiUrlPreference(final Context context, final AttributeSet attrs,
                                        final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SponsorBlockApiUrlPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public SponsorBlockApiUrlPreference(final Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();

        final View alertDialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_sponsor_block_api_url, null);

        final EditText editText = alertDialogView.findViewById(R.id.api_url_edit);
        editText.setText(getSharedPreferences().getString(getKey(), null));
        editText.setOnFocusChangeListener((v, hasFocus) -> editText.post(() -> {
            final InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager
                    .showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }));
        editText.requestFocus();

        alertDialogView.findViewById(R.id.icon_api_url_help)
                .setOnClickListener(v -> {
                    final Uri privacyPolicyUri = Uri.parse(getContext()
                            .getString(R.string.sponsor_block_privacy_policy_url));
                    final View helpDialogView = LayoutInflater.from(getContext())
                            .inflate(R.layout.dialog_sponsor_block_api_url_help, null);
                    final View privacyPolicyButton = helpDialogView
                            .findViewById(R.id.sponsor_block_privacy_policy_button);
                    privacyPolicyButton.setOnClickListener(v1 -> {
                        final Intent i = new Intent(Intent.ACTION_VIEW, privacyPolicyUri);
                        getContext().startActivity(i);
                    });

                    new AlertDialog.Builder(getContext())
                            .setView(helpDialogView)
                            .setPositiveButton("Use Official", (dialog, which) -> {
                                editText.setText(getContext()
                                        .getString(R.string.sponsor_block_default_api_url));
                                dialog.dismiss();
                            })
                            .setNeutralButton("Close", (dialog, which) -> dialog.dismiss())
                            .create()
                            .show();
                });

        final AlertDialog alertDialog =
                new AlertDialog.Builder(getContext())
                        .setView(alertDialogView)
                        .setTitle(getContext().getString(R.string.sponsor_block_api_url_title))
                        .setPositiveButton("OK", (dialog, which) -> {
                            final String newValue = editText.getText().toString();
                            final SharedPreferences.Editor editor =
                                    getPreferenceManager().getSharedPreferences().edit();
                            editor.putString(getKey(), newValue);
                            editor.apply();

                            callChangeListener(newValue);

                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                        .create();

        alertDialog.show();
    }
}
