/**
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SdCardPermissionHelper.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Parts of this code where reused form the Amaze project
 * https://github.com/TeamAmaze/AmazeFileManage
 * which is also available under GPL3.
 */



package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import static android.app.Activity.RESULT_OK;

public class SdCardPermissionHelper {

    public static final int REQUEST_SD_CARD_PERMISSION = 33431;

    public static String requreSdCardAccessToken(final Activity activity) {
        String key = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(activity.getString(R.string.sdcard_access_key), "");
        if(key.isEmpty()) {
            getSdCardPermission(activity);
            return "";
        } else {
            return key;
        }
    }

    public static void getSdCardPermission(final Activity activity) {
        LayoutInflater layoutInflater =
                (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = layoutInflater.inflate(R.layout.dialog_sdcard_permission, null);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .create();


        view.findViewById(R.id.cancel_button).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.accept_button).setOnClickListener(v -> {
            dialog.dismiss();
            openStorageAccessFramework(activity);
        });

        dialog.show();
    }

    private static void openStorageAccessFramework(final Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        activity.startActivityForResult(intent, REQUEST_SD_CARD_PERMISSION);
    }

    public static void onActivityResult(final Activity activity, int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_SD_CARD_PERMISSION) {
            if(resultCode == RESULT_OK) {
                try {
                    PreferenceManager.getDefaultSharedPreferences(activity)
                            .edit()
                            .putString(activity.getString(R.string.sdcard_access_key),
                                    data.getData().toString())
                            .apply();
                } catch (Exception e) {
                    ErrorActivity.reportError(activity, e, activity.getClass(), null,
                            ErrorActivity.ErrorInfo.make(UserAction.REQUIRE_SDCARD_PERMISSION,
                                    null, "", R.string.get_write_permission_for_sd_failed));
                }
            } else {
                Toast.makeText(activity, R.string.get_write_permission_for_sd_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
