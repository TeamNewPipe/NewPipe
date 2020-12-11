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
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openInDefaultApp(Context, String)}
     *
     * @param context the context to use
     * @param url     the url to browse
     */
    public static void openUrlInBrowser(final Context context, final String url) {
        final String defaultBrowserPackageName = getDefaultBrowserPackageName(context);

        if (defaultBrowserPackageName.equals("android")
            || defaultBrowserPackageName.equals("com.huawei.android.internal.app")
            || defaultBrowserPackageName.equals("com.zui.resolver")) {
            // no browser set as default (doesn't work on some devices)
            openInDefaultApp(context, url);
        } else {
            try {
                final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .setPackage(defaultBrowserPackageName)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                // not a browser but an app chooser because of OEMs changes
                openInDefaultApp(context, url);
            }
        }
    }

    /**
     * Open the url in the default app set to open this type of link.
     * <p>
     * If no app is set as default, it will open a chooser
     *
     * @param context the context to use
     * @param url     the url to browse
     */
    private static void openInDefaultApp(final Context context, final String url) {
        try {
            final Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            final Intent intent = new Intent(Intent.ACTION_CHOOSER);
            intent.setPackage("android");
            intent.putExtra(Intent.EXTRA_INTENT, viewIntent);
            intent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (final ActivityNotFoundException e) {
            // falling back to OEM's chooser if Android's system chooser was removed by the OEM
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(Intent.createChooser(
                    intent, context.getString(R.string.open_with))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /**
     * Get the default browser package name.
     * <p>
     * If no browser is set as default, it will return "android".
     * Note: it doesn't return "android" on some devices because some OEMs changed the app chooser.
     *
     * @param context the context to use
     * @return the package name of the default browser, or the app chooser if there's no default
     */
    private static String getDefaultBrowserPackageName(final Context context) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    /**
     * Open the android share menu to share the current url.
     *
     * @param context the context to use
     * @param subject the url subject, typically the title
     * @param url     the url to share
     */
    public static void shareUrl(final Context context, final String subject, final String url) {
        try {
            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);

            final Intent intent = new Intent(Intent.ACTION_CHOOSER);
            intent.putExtra(Intent.EXTRA_INTENT, shareIntent);
            intent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.share_dialog_title));
            intent.setPackage("android");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (final ActivityNotFoundException e) {
            // falling back to OEM's chooser if Android's system chooser was removed by the OEM
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, url);
            context.startActivity(Intent.createChooser(
                    intent, context.getString(R.string.share_dialog_title))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
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
            Toast.makeText(context,
                    R.string.permission_denied,
                    Toast.LENGTH_LONG).show();
            return;
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text));
        Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT)
                .show();
    }
}
