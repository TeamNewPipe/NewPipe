package org.schabi.newpipe.util.external_communication

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

/**
 * Util class that provides methods which are related to the Kodi Media Center and its Kore app.
 * @see [Kodi website](https://kodi.tv/)
 */
object KoreUtils {
    fun isServiceSupportedByKore(serviceId: Int): Boolean {
        return ((serviceId == ServiceList.YouTube.getServiceId()
                || serviceId == ServiceList.SoundCloud.getServiceId()))
    }

    fun shouldShowPlayWithKodi(context: Context,
                               serviceId: Int): Boolean {
        return (isServiceSupportedByKore(serviceId)
                && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_play_with_kodi_key), false))
    }

    /**
     * Start an activity to install Kore.
     *
     * @param context the context to use
     */
    fun installKore(context: Context) {
        ShareUtils.installApp(context, context.getString(R.string.kore_package))
    }

    /**
     * Start Kore app to show a video on Kodi, and if the app is not installed ask the user to
     * install it.
     *
     *
     * For a list of supported urls see the
     * [
 * Kore source code
](https://github.com/xbmc/Kore/blob/master/app/src/main/AndroidManifest.xml) * .
     *
     * @param context   the context to use
     * @param streamUrl the url to the stream to play
     */
    fun playWithKore(context: Context, streamUrl: Uri?) {
        val intent: Intent = Intent(Intent.ACTION_VIEW)
                .setPackage(context.getString(R.string.kore_package))
                .setData(streamUrl)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!ShareUtils.tryOpenIntentInApp(context, intent)) {
            AlertDialog.Builder(context)
                    .setMessage(R.string.kore_not_found)
                    .setPositiveButton(R.string.install, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> installKore(context) }))
                    .setNegativeButton(R.string.cancel, null)
                    .show()
        }
    }
}
