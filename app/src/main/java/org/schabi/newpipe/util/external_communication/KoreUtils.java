package org.schabi.newpipe.util.external_communication;

import static org.schabi.newpipe.util.external_communication.ShareUtils.installApp;
import static org.schabi.newpipe.util.external_communication.ShareUtils.tryOpenIntentInApp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;

/**
 * Util class that provides methods which are related to the Kodi Media Center and its Kore app.
 * @see <a href="https://kodi.tv/">Kodi website</a>
 */
public final class KoreUtils {
    private KoreUtils() { }

    public static boolean isServiceSupportedByKore(final int serviceId) {
        return (serviceId == ServiceList.YouTube.getServiceId()
                || serviceId == ServiceList.SoundCloud.getServiceId());
    }

    public static boolean shouldShowPlayWithKodi(@NonNull final Context context,
                                                 final int serviceId) {
        return isServiceSupportedByKore(serviceId)
                && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_play_with_kodi_key), false);
    }

    /**
     * Start an activity to install Kore.
     *
     * @param context the context to use
     */
    public static void installKore(final Context context) {
        installApp(context, context.getString(R.string.kore_package));
    }

    /**
     * Start Kore app to show a video on Kodi, and if the app is not installed ask the user to
     * install it.
     * <p>
     * For a list of supported urls see the
     * <a href="https://github.com/xbmc/Kore/blob/master/app/src/main/AndroidManifest.xml">
     * Kore source code
     * </a>.
     *
     * @param context   the context to use
     * @param streamUrl the url to the stream to play
     */
    public static void playWithKore(final Context context, final Uri streamUrl) {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setPackage(context.getString(R.string.kore_package))
                .setData(streamUrl)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (!tryOpenIntentInApp(context, intent)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.kore_not_found)
                    .setPositiveButton(R.string.install, (dialog, which) -> installKore(context))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> { });
            builder.create().show();
        }
    }
}
