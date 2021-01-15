package org.schabi.newpipe.util;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

public final class ShareUtils {
    private ShareUtils() {
    }

    /**
     * Open an Intent to install an app.
     * <p>
     * This method will first try open to Google Play Store with the market scheme and falls back to
     * Google Play Store web url if this first cannot be found.
     *
     * @param context the context to use
     * @param packageName the package to be installed
     */
    public static void installApp(final Context context, final String packageName) {
        try {
            // Try market:// scheme
            openIntentInApp(context, new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + packageName)));
        } catch (final ActivityNotFoundException e) {
            // Fall back to Google Play Store Web URL (don't worry, F-Droid can handle it :))
            openUrlInBrowser(context,
                    "https://play.google.com/store/apps/details?id=" + packageName, false);
        }
    }

    /**
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openInDefaultApp(Context, Intent)}
     *
     * @param context                the context to use
     * @param url                    the url to browse
     * @param httpDefaultBrowserTest the boolean to set if the test for the default browser will be
     *                               for HTTP protocol or for the created intent
     */
    public static void openUrlInBrowser(final Context context, final String url,
                                        final boolean httpDefaultBrowserTest) {
        final String defaultPackageName;
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (httpDefaultBrowserTest) {
            defaultPackageName = getDefaultBrowserPackageName(context);
        } else {
            defaultPackageName = getDefaultAppPackageName(context, intent);
        }

        if (defaultPackageName.equals("android")) {
            // No browser set as default (doesn't work on some devices)
            openInDefaultApp(context, intent);
        } else {
            try {
                intent.setPackage(defaultPackageName);
                context.startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                // Not a browser but an app chooser because of OEMs changes
                intent.setPackage(null);
                openInDefaultApp(context, intent);
            }
        }
    }

    /**
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openInDefaultApp(Context, Intent)}
     * <p>
     * This calls {@link ShareUtils#openUrlInBrowser(Context, String, boolean)} with true
     * for the boolean parameter
     *
     * @param context the context to use
     * @param url     the url to browse
     **/
    public static void openUrlInBrowser(final Context context, final String url) {
        openUrlInBrowser(context, url, true);
    }

    /**
     * Open an intent with the system default app.
     * <p>
     * The intent can be of every type, excepted a web intent for which
     * {@link ShareUtils#openUrlInBrowser(Context, String, boolean)} should be used.
     * <p>
     * If no app is set as default, fallbacks to
     * {@link ShareUtils#openInDefaultApp(Context, Intent)}
     *
     * @param context the context to use
     * @param intent  the intent to open
     */
    public static void openIntentInApp(final Context context, final Intent intent) {
        final String defaultAppPackageName = getDefaultAppPackageName(context, intent);

        if (defaultAppPackageName.equals("android")) {
            // No app set as default (doesn't work on some devices)
            openInDefaultApp(context, intent);
        } else {
            try {
                intent.setPackage(defaultAppPackageName);
                context.startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                // Not an app to open the intent but an app chooser because of OEMs changes
                intent.setPackage(null);
                openInDefaultApp(context, intent);
            }
        }
    }

    /**
     * Open the url in the default app set to open this type of link.
     * <p>
     * If no app is set as default, it will open a chooser
     *
     * @param context    the context to use
     * @param viewIntent the intent to open
     */
    private static void openInDefaultApp(final Context context, final Intent viewIntent) {
        final Intent intent = new Intent(Intent.ACTION_CHOOSER);
        intent.putExtra(Intent.EXTRA_INTENT, viewIntent);
        intent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Get the default app package name.
     * <p>
     * If no app is set as default, it will return "android".
     * <p>
     * Note: it doesn't return "android" on some devices because some OEMs changed the app chooser.
     *
     * @param context the context to use
     * @param intent  the intent to get default app
     * @return the package name of the default app, or the app chooser if there's no default
     */
    private static String getDefaultAppPackageName(final Context context, final Intent intent) {
        return context.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;
    }

    /**
     * Get the default browser package name.
     * <p>
     * If no browser is set as default, it will return "android"
     * Note: it doesn't return "android" on some devices because some OEMs changed the app chooser.
     *
     * @param context the context to use
     * @return the package name of the default browser, or "android" if there's no default
     */
    private static String getDefaultBrowserPackageName(final Context context) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return context.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;
    }

    /**
     * Open the android share menu to share the current url.
     *
     * @param context the context to use
     * @param subject the url subject, typically the title
     * @param url     the url to share
     */
    public static void shareText(final Context context, final String subject, final String url) {
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);

        final Intent intent = new Intent(Intent.ACTION_CHOOSER);
        intent.putExtra(Intent.EXTRA_INTENT, shareIntent);
        intent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.share_dialog_title));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Copy the text to clipboard, and indicate to the user whether the operation was completed
     * successfully using a Toast.
     *
     * @param context the context to use
     * @param text    the text to copy
     */
    public static void copyToClipboard(final Context context, final String text) {
        final ClipboardManager clipboardManager =
                ContextCompat.getSystemService(context, ClipboardManager.class);

        if (clipboardManager == null) {
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show();
            return;
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text));
        Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show();
    }
}
