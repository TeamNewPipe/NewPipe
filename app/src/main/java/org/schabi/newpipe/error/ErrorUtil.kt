package org.schabi.newpipe.error

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.R

class ErrorUtil {
    companion object {
        private const val ERROR_REPORT_NOTIFICATION_ID = 5340681

        /**
         * Reports a new error by starting a new activity.
         *
         * @param context
         * @param errorInfo
         */
        @JvmStatic
        fun openActivity(context: Context, errorInfo: ErrorInfo) {
            context.startActivity(getErrorActivityIntent(context, errorInfo))
        }

        @JvmStatic
        fun showSnackbar(context: Context, errorInfo: ErrorInfo) {
            val rootView = if (context is Activity) context.findViewById<View>(R.id.content) else null
            showSnackbar(context, rootView, errorInfo)
        }

        @JvmStatic
        fun showSnackbar(fragment: Fragment, errorInfo: ErrorInfo) {
            var rootView = fragment.view
            if (rootView == null && fragment.activity != null) {
                rootView = fragment.requireActivity().findViewById(R.id.content)
            }
            showSnackbar(fragment.requireContext(), rootView, errorInfo)
        }

        @JvmStatic
        fun showUiErrorSnackbar(context: Context, request: String, throwable: Throwable) {
            showSnackbar(context, ErrorInfo(throwable, UserAction.UI_ERROR, request))
        }

        @JvmStatic
        fun showUiErrorSnackbar(fragment: Fragment, request: String, throwable: Throwable) {
            showSnackbar(fragment, ErrorInfo(throwable, UserAction.UI_ERROR, request))
        }

        @JvmStatic
        fun createNotification(context: Context, errorInfo: ErrorInfo) {
            val notificationManager =
                ContextCompat.getSystemService(context, NotificationManager::class.java)
            if (notificationManager == null) {
                // this should never happen, but just in case open error activity
                openActivity(context, errorInfo)
            }

            var pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntentFlags = pendingIntentFlags or PendingIntent.FLAG_IMMUTABLE
            }

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
                        PendingIntent.getActivity(
                            context,
                            0,
                            getErrorActivityIntent(context, errorInfo),
                            pendingIntentFlags
                        )
                    )

            notificationManager!!.notify(ERROR_REPORT_NOTIFICATION_ID, notificationBuilder.build())
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
