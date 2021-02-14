package org.schabi.newpipe.util;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
     * This method tries to open the default app market with the package id passed as the
     * second param (a system chooser will be opened if there are multiple markets and no default)
     * and falls back to Google Play Store web URL if no app to handle the market scheme was found.
     * <p>
     * It uses {@link ShareUtils#openIntentInApp(Context, Intent)} to open market scheme and
     * {@link ShareUtils#openUrlInBrowser(Context, String, boolean)} to open Google Play Store web
     * URL with false for the boolean param.
     *
     * @param context   the context to use
     * @param packageId the package id of the app to be installed
     */
    public static void installApp(final Context context, final String packageId) {
        // Try market:// scheme
        final boolean marketSchemeResult = openIntentInApp(context, new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageId))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (!marketSchemeResult) {
            // Fall back to Google Play Store Web URL (F-Droid can handle it)
            openUrlInBrowser(context,
                    "https://play.google.com/store/apps/details?id=" + packageId, false);
        }
    }

    /**
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openAppChooser(Context, Intent, String)}
     *
     * @param context                the context to use
     * @param url                    the url to browse
     * @param httpDefaultBrowserTest the boolean to set if the test for the default browser will be
     *                               for HTTP protocol or for the created intent
     * @return true if the URL can be opened or false if it cannot
     */
    public static boolean openUrlInBrowser(final Context context, final String url,
                                           final boolean httpDefaultBrowserTest) {
        final String defaultPackageName;
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (httpDefaultBrowserTest) {
            defaultPackageName = getDefaultAppPackageName(context, new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://")).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            defaultPackageName = getDefaultAppPackageName(context, intent);
        }

        if (defaultPackageName.equals("android")) {
            // No browser set as default (doesn't work on some devices)
            openAppChooser(context, intent, context.getString(R.string.open_with));
        } else {
            if (defaultPackageName.isEmpty()) {
                // No app installed to open a web url
                Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show();
                return false;
            } else {
                try {
                    intent.setPackage(defaultPackageName);
                    context.startActivity(intent);
                } catch (final ActivityNotFoundException e) {
                    // Not a browser but an app chooser because of OEMs changes
                    intent.setPackage(null);
                    openAppChooser(context, intent, context.getString(R.string.open_with));
                }
            }
        }

        return true;
    }

    /**
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openAppChooser(Context, Intent, String)}
     * <p>
     * This calls {@link ShareUtils#openUrlInBrowser(Context, String, boolean)} with true
     * for the boolean parameter
     *
     * @param context the context to use
     * @param url     the url to browse
     * @return true if the URL can be opened or false if it cannot be
     **/
    public static boolean openUrlInBrowser(final Context context, final String url) {
        return openUrlInBrowser(context, url, true);
    }

    /**
     * Open an intent with the system default app.
     * <p>
     * The intent can be of every type, excepted a web intent for which
     * {@link ShareUtils#openUrlInBrowser(Context, String, boolean)} should be used.
     * <p>
     * If no app is set as default, fallbacks to
     * {@link ShareUtils#openAppChooser(Context, Intent, String)}
     *
     * @param context the context to use
     * @param intent  the intent to open
     * @return true if the intent can be opened or false if it cannot be
     */
    public static boolean openIntentInApp(final Context context, final Intent intent) {
        final String defaultPackageName = getDefaultAppPackageName(context, intent);

        if (defaultPackageName.equals("android")) {
            // No app set as default (doesn't work on some devices)
            openAppChooser(context, intent, context.getString(R.string.open_with));
        } else {
            if (defaultPackageName.isEmpty()) {
                // No app installed to open the intent
                Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show();
                return false;
            } else {
                try {
                    intent.setPackage(defaultPackageName);
                    context.startActivity(intent);
                } catch (final ActivityNotFoundException e) {
                    // Not an app to open the intent but an app chooser because of OEMs changes
                    intent.setPackage(null);
                    openAppChooser(context, intent, context.getString(R.string.open_with));
                }
            }
        }

        return true;
    }

    /**
     * Open the system chooser to launch an intent.
     * <p>
     * This method opens an {@link android.content.Intent#ACTION_CHOOSER} of the intent putted
     * as the viewIntent param. A string for the chooser's title must be passed as the last param.
     *
     * @param context              the context to use
     * @param intent               the intent to open
     * @param chooserStringTitle   the string of chooser's title
     */
    private static void openAppChooser(final Context context, final Intent intent,
                                       final String chooserStringTitle) {
        final Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, chooserStringTitle);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooserIntent);
    }

    /**
     * Get the default app package name.
     * <p>
     * If no app is set as default, it will return "android" (not on some devices because some
     * OEMs changed the app chooser).
     * <p>
     * If no app is installed on user's device to handle the intent, it will return an empty string.
     *
     * @param context the context to use
     * @param intent  the intent to get default app
     * @return the package name of the default app, an empty string if there's no app installed to
     * handle the intent or the app chooser if there's no default
     */
    private static String getDefaultAppPackageName(final Context context, final Intent intent) {
        final ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null) {
            return "";
        } else {
            return resolveInfo.activityInfo.packageName;
        }
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

        openAppChooser(context, shareIntent, context.getString(R.string.share_dialog_title));
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
