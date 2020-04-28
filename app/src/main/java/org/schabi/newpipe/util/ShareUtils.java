package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.schabi.newpipe.R;

public final class ShareUtils {
    private ShareUtils() {
    }

    /**
     * Open the url with the system default browser.
     * <p>
     * If no browser is set as default, fallbacks to
     * {@link ShareUtils#openUrlBrowserChooser(Context, String)}
     *
     * @param context the context to use
     * @param url     the url to browse
     */
    public static void openUrlInBrowser(final Context context, final String url) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));

        final String defaultBrowserPackageName = getDefaultBrowserPackageName(context);

        if (defaultBrowserPackageName.equals("android")) {
            // no browser set as default
            openUrlBrowserChooser(context, url);
        } else {
            intent.setPackage(defaultBrowserPackageName);
            context.startActivity(intent);
        }
    }

    /**
     * Open the url with application chooser, including browser and apps able to open url.
     * <p>
     * If any app (except browser, typically NewPipe) is set as default,
     * it will nor open in browser, neither open the chooser, but just the default app.
     *
     * @param context the context to use
     * @param url     the url to browse
     */
    private static void openUrlBrowserChooser(final Context context, final String url) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.share_dialog_title)));
    }

    /**
     * Get the default browser package name.
     * <p>
     * If no browser is set as default, it will return "android"
     *
     * @param context the context to use
     * @return the package name of the default browser, or "android" if there's no default
     */
    private static String getDefaultBrowserPackageName(final Context context) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
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
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        context.startActivity(Intent.createChooser(
                intent, context.getString(R.string.share_dialog_title)));
    }
}
