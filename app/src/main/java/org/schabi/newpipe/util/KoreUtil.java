package org.schabi.newpipe.util;


import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;

public final class KoreUtil {
    private KoreUtil() { }

    public static boolean isServiceSupportedByKore(final int serviceId) {
        return (serviceId == ServiceList.YouTube.getServiceId()
                || serviceId == ServiceList.SoundCloud.getServiceId());
    }

    public static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (DialogInterface dialog, int which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (DialogInterface dialog, int which) -> {
                });
        builder.create().show();
    }
}
