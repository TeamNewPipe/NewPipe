package org.schabi.newpipe.error

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.R

/**
 * This class contains all of the methods that should be used to let the user know that an error has
 * occurred in the least intrusive way possible for each case. This class is for unexpected errors,
 * for handled errors (e.g. network errors) use e.g. [ErrorPanelHelper] instead.
 * - Use a snackbar if the exception is not critical and it happens in a place where a root view
 *      is available.
 * - Use a notification if the exception happens inside a background service (player, subscription
 *      import, ...) or there is no activity/fragment from which to extract a root view.
 * - Finally use the error activity only as a last resort in case the exception is critical and
 *      happens in an open activity (since the workflow would be interrupted anyway in that case).
 */
class ErrorUtil {
    companion object {
        private const val ERROR_REPORT_NOTIFICATION_ID = 5340681

        /**
         * Starts a new error activity allowing the user to report the provided error. Only use this
         * method directly as a last resort in case the exception is critical and happens in an open
         * activity (since the workflow would be interrupted anyway in that case). So never use this
         * for background services.
         *
         * @param context the context to use to start the new activity
         * @param errorInfo the error info to be reported
         */
        @JvmStatic
        fun openActivity(context: Context, errorInfo: ErrorInfo) {
            context.startActivity(getErrorActivityIntent(context, errorInfo))
        }

        /**
         * Show a bottom snackbar to the user, with a report button that opens the error activity.
         * Use this method if the exception is not critical and it happens in a place where a root
         * view is available.
         *
         * @param context will be used to obtain the root view if it is an [Activity]; if no root
         *                view can be found an error notification is shown instead
         * @param errorInfo the error info to be reported
         */
        @JvmStatic
        fun showSnackbar(context: Context, errorInfo: ErrorInfo) {
            val rootView = if (context is Activity) context.findViewById<View>(R.id.content) else null
            showSnackbar(context, rootView, errorInfo)
        }

        /**
         * Show a bottom snackbar to the user, with a report button that opens the error activity.
         * Use this method if the exception is not critical and it happens in a place where a root
         * view is available.
         *
         * @param fragment will be used to obtain the root view if it has a connected [Activity]; if
         *                 no root view can be found an error notification is shown instead
         * @param errorInfo the error info to be reported
         */
        @JvmStatic
        fun showSnackbar(fragment: Fragment, errorInfo: ErrorInfo) {
            var rootView = fragment.view
            if (rootView == null && fragment.activity != null) {
                rootView = fragment.requireActivity().findViewById(R.id.content)
            }
            showSnackbar(fragment.requireContext(), rootView, errorInfo)
        }

        /**
         * Shortcut to calling [showSnackbar] with an [ErrorInfo] of type [UserAction.UI_ERROR]
         */
        @JvmStatic
        fun showUiErrorSnackbar(context: Context, request: String, throwable: Throwable) {
            showSnackbar(context, ErrorInfo(throwable, UserAction.UI_ERROR, request))
        }

        /**
         * Shortcut to calling [showSnackbar] with an [ErrorInfo] of type [UserAction.UI_ERROR]
         */
        @JvmStatic
        fun showUiErrorSnackbar(fragment: Fragment, request: String, throwable: Throwable) {
            showSnackbar(fragment, ErrorInfo(throwable, UserAction.UI_ERROR, request))
        }

        /**
         * Create an error notification. Tapping on the notification opens the error activity. Use
         * this method if the exception happens inside a background service (player, subscription
         * import, ...) or there is no activity/fragment from which to extract a root view.
         *
         * @param context the context to use to show the notification
         * @param errorInfo the error info to be reported; the error message
         *                  [ErrorInfo.messageStringId] will be shown in the notification
         *                  description
         */
        @JvmStatic
        fun createNotification(context: Context, errorInfo: ErrorInfo) {
            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(
                    context,
                    context.getString(R.string.error_report_channel_id)
                )
                    .setSmallIcon(R.drawable.ic_bug_report)
                    .setContentTitle(context.getString(R.string.error_report_notification_title))
                    .setContentText(context.getString(errorInfo.messageStringId))
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntentCompat.getActivity(
                            context,
                            0,
                            getErrorActivityIntent(context, errorInfo),
                            PendingIntent.FLAG_UPDATE_CURRENT,
                            false
                        )
                    )

            NotificationManagerCompat.from(context)
                .notify(ERROR_REPORT_NOTIFICATION_ID, notificationBuilder.build())

            // since the notification is silent, also show a toast, otherwise the user is confused
            Toast.makeText(context, R.string.error_report_notification_toast, Toast.LENGTH_SHORT)
                .show()
        }

        private fun getErrorActivityIntent(context: Context, errorInfo: ErrorInfo): Intent {
            val intent = Intent(context, ErrorActivity::class.java)
            intent.putExtra(ErrorActivity.ERROR_INFO, errorInfo)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        private fun showSnackbar(context: Context, rootView: View?, errorInfo: ErrorInfo) {
            if (rootView == null) {
                // fallback to showing a notification if no root view is available
                createNotification(context, errorInfo)
            } else {
                Snackbar.make(rootView, R.string.error_snackbar_message, Snackbar.LENGTH_LONG)
                    .setActionTextColor(Color.YELLOW)
                    .setAction(context.getString(R.string.error_snackbar_action).uppercase()) {
                        openActivity(context, errorInfo)
                    }.show()
            }
        }
    }
}
