package org.schabi.newpipe.util;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;

public final class KoreUtil {
    private KoreUtil() { }

    public static boolean isServiceSupportedByKore(final int serviceId) {
        return (serviceId == ServiceList.YouTube.getServiceId()
                || serviceId == ServiceList.SoundCloud.getServiceId());
    }

    public static boolean shouldShowPlayWithKodi(final Context context, final int serviceId) {
        return isServiceSupportedByKore(serviceId)
                && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_play_with_kodi_key), false);
    }

    public static void showInstallKoreDialog(final Context context) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.kore_not_found)
                .setPositiveButton(R.string.install, (dialog, which) ->
                        NavigationHelper.installKore(context))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                });
        builder.create().show();
    }
}
