package org.schabi.newpipe.util.external_communication;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public final class ShareUtils {
    private static final String TAG = ShareUtils.class.getSimpleName();

    private ShareUtils() {
    }

    /**
     * Open an Intent to install an app.
     * <p>
     * This method tries to open the default app market with the package id passed as the
     * second param (a system chooser will be opened if there are multiple markets and no default)
     * and falls back to Google Play Store web URL if no app to handle the market scheme was found.
     * <p>
     * It uses {@link #openIntentInApp(Context, Intent)} to open market scheme and {@link
     * #openUrlInBrowser(Context, String)} to open Google Play Store web URL.
     *
     * @param context   the context to use
     * @param packageId the package id of the app to be installed
     */
    public static void installApp(@NonNull final Context context, final String packageId) {
        // Try market scheme
        final Intent marketSchemeIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageId))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryOpenIntentInApp(context, marketSchemeIntent)) {
            // Fall back to Google Play Store Web URL (F-Droid can handle it)
            openUrlInApp(context, "https://play.google.com/store/apps/details?id=" + packageId);
        }
    }

    /**
     * Open the url with the system default browser. If no browser is set as default, falls back to
     * {@link #openAppChooser(Context, Intent, boolean)}.
     * <p>
     * This function selects the package to open based on which apps respond to the {@code http://}
     * schema alone, which should exclude special non-browser apps that are can handle the url (e.g.
     * the official YouTube app).
     * <p>
     * Therefore <b>please prefer {@link #openUrlInApp(Context, String)}</b>, that handles package
     * resolution in a standard way, unless this is the action of an explicit "Open in browser"
     * button.
     *
     * @param context the context to use
     * @param url     the url to browse
     **/
    public static void openUrlInBrowser(@NonNull final Context context, final String url) {
        // Resolve using a generic http://, so we are sure to get a browser and not e.g. the yt app.
        // Note that this requires the `http` schema to be added to `<queries>` in the manifest.
        final ResolveInfo defaultBrowserInfo;
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            defaultBrowserInfo = context.getPackageManager().resolveActivity(browserIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY));
        } else {
            defaultBrowserInfo = context.getPackageManager().resolveActivity(browserIntent,
                    PackageManager.MATCH_DEFAULT_ONLY);
        }

        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (defaultBrowserInfo == null) {
            // No app installed to open a web URL, but it may be handled by other apps so try
            // opening a system chooser for the link in this case (it could be bypassed by the
            // system if there is only one app which can open the link or a default app associated
            // with the link domain on Android 12 and higher)
            openAppChooser(context, intent, true);
            return;
        }

        final String defaultBrowserPackage = defaultBrowserInfo.activityInfo.packageName;

        if (defaultBrowserPackage.equals("android")) {
            // No browser set as default (doesn't work on some devices)
            openAppChooser(context, intent, true);
        } else {
            try {
                intent.setPackage(defaultBrowserPackage);
                context.startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                // Not a browser but an app chooser because of OEMs changes
                intent.setPackage(null);
                openAppChooser(context, intent, true);
            }
        }
    }

    /**
     * Open a url with the system default app using {@link Intent#ACTION_VIEW}, showing a toast in
     * case of failure.
     *
     * @param context the context to use
     * @param url     the url to open
     */
    public static void openUrlInApp(@NonNull final Context context, final String url) {
        openIntentInApp(context, new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * Open an intent with the system default app.
     * <p>
     * Use {@link #openIntentInApp(Context, Intent)} to show a toast in case of failure.
     *
     * @param context the context to use
     * @param intent  the intent to open
     * @return true if the intent could be opened successfully, false otherwise
     */
    public static boolean tryOpenIntentInApp(@NonNull final Context context,
                                             @NonNull final Intent intent) {
        try {
            context.startActivity(intent);
        } catch (final ActivityNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Open an intent with the system default app, showing a toast in case of failure.
     * <p>
     * Use {@link #tryOpenIntentInApp(Context, Intent)} if you don't want the toast. Use {@link
     * #openUrlInApp(Context, String)} as a shorthand for {@link Intent#ACTION_VIEW} with urls.
     *
     * @param context the context to use
     * @param intent  the intent to
     */
    public static void openIntentInApp(@NonNull final Context context,
                                       @NonNull final Intent intent) {
        if (!tryOpenIntentInApp(context, intent)) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Open the system chooser to launch an intent.
     * <p>
     * This method opens an {@link android.content.Intent#ACTION_CHOOSER} of the intent putted
     * as the intent param. If the setTitleChooser boolean is true, the string "Open with" will be
     * set as the title of the system chooser.
     * For Android P and higher, title for {@link android.content.Intent#ACTION_SEND} system
     * choosers must be set on this intent, not on the
     * {@link android.content.Intent#ACTION_CHOOSER} intent.
     *
     * @param context         the context to use
     * @param intent          the intent to open
     * @param setTitleChooser set the title "Open with" to the chooser if true, else not
     */
    private static void openAppChooser(@NonNull final Context context,
                                       @NonNull final Intent intent,
                                       final boolean setTitleChooser) {
        final Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (setTitleChooser) {
            chooserIntent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with));
        }

        // Migrate any clip data and flags from the original intent.
        final int permFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (permFlags != 0) {
            ClipData targetClipData = intent.getClipData();
            if (targetClipData == null && intent.getData() != null) {
                final ClipData.Item item = new ClipData.Item(intent.getData());
                final String[] mimeTypes;
                if (intent.getType() != null) {
                    mimeTypes = new String[] {intent.getType()};
                } else {
                    mimeTypes = new String[] {};
                }
                targetClipData = new ClipData(null, mimeTypes, item);
            }
            if (targetClipData != null) {
                chooserIntent.setClipData(targetClipData);
                chooserIntent.addFlags(permFlags);
            }
        }

        try {
            context.startActivity(chooserIntent);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Open the android share sheet to share a content.
     *
     * <p>
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if its URL is not null or empty and its
     * corresponding image is in the image cache.
     * </p>
     *
     * @param context         the context to use
     * @param title           the title of the content
     * @param content         the content to share
     * @param imagePreviewUrl the image of the subject
     */
    public static void shareText(@NonNull final Context context,
                                 @NonNull final String title,
                                 final String content,
                                 final String imagePreviewUrl) {
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
        if (!TextUtils.isEmpty(title)) {
            shareIntent.putExtra(Intent.EXTRA_TITLE, title);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        }

        // Content preview in the share sheet has been added in Android 10, so it's not needed to
        // set a content preview which will be never displayed
        // See https://developer.android.com/training/sharing/send#adding-rich-content-previews
        // If loading of images has been disabled, don't try to generate a content preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !TextUtils.isEmpty(imagePreviewUrl)
                && ImageStrategy.shouldLoadImages()) {

            final ClipData clipData = generateClipDataForImagePreview(context, imagePreviewUrl);
            if (clipData != null) {
                shareIntent.setClipData(clipData);
                shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        openAppChooser(context, shareIntent, false);
    }

    /**
     * Open the android share sheet to share a content.
     *
     * <p>
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if the preferred image chosen by {@link
     * ImageStrategy#choosePreferredImage(List)} is in the image cache.
     * </p>
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     * @param images  a set of possible {@link Image}s of the subject, among which to choose with
     *                {@link ImageStrategy#choosePreferredImage(List)} since that's likely to
     *                provide an image that is in Picasso's cache
     */
    public static void shareText(@NonNull final Context context,
                                 @NonNull final String title,
                                 final String content,
                                 final List<Image> images) {
        shareText(context, title, content, ImageStrategy.choosePreferredImage(images));
    }

    /**
     * Open the android share sheet to share a content.
     *
     * <p>
     * This calls {@link #shareText(Context, String, String, String)} with an empty string for the
     * {@code imagePreviewUrl} parameter. This method should be used when the shared content has no
     * preview thumbnail.
     * </p>
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     */
    public static void shareText(@NonNull final Context context,
                                 @NonNull final String title,
                                 final String content) {
        shareText(context, title, content, "");
    }

    /**
     * Copy the text to clipboard, and indicate to the user whether the operation was completed
     * successfully using a Toast.
     *
     * @param context the context to use
     * @param text    the text to copy
     */
    public static void copyToClipboard(@NonNull final Context context, final String text) {
        final ClipboardManager clipboardManager =
                ContextCompat.getSystemService(context, ClipboardManager.class);

        if (clipboardManager == null) {
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text));
            if (Build.VERSION.SDK_INT < 33) {
                // Android 13 has its own "copied to clipboard" dialog
                Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show();
            }
        } catch (final Exception e) {
            Log.e(TAG, "Error when trying to copy text to clipboard", e);
            Toast.makeText(context, R.string.msg_failed_to_copy, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Generate a {@link ClipData} with the image of the content shared, if it's in the app cache.
     *
     * <p>
     * In order not to worry about network issues (timeouts, DNS issues, low connection speed, ...)
     * when sharing a content, only images in the {@link com.squareup.picasso.LruCache LruCache}
     * used by the Picasso library inside {@link PicassoHelper} are used as preview images. If the
     * thumbnail image is not in the cache, no {@link ClipData} will be generated and {@code null}
     * will be returned.
     * </p>
     *
     * <p>
     * In order to display the image in the content preview of the Android share sheet, an URI of
     * the content, accessible and readable by other apps has to be generated, so a new file inside
     * the application cache will be generated, named {@code android_share_sheet_image_preview.jpg}
     * (if a file under this name already exists, it will be overwritten). The thumbnail will be
     * compressed in JPEG format, with a {@code 90} compression level.
     * </p>
     *
     * <p>
     * Note that if an exception occurs when generating the {@link ClipData}, {@code null} is
     * returned.
     * </p>
     *
     * <p>
     * This method will call {@link PicassoHelper#getImageFromCacheIfPresent(String)} to get the
     * thumbnail of the content in the {@link com.squareup.picasso.LruCache LruCache} used by
     * the Picasso library inside {@link PicassoHelper}.
     * </p>
     *
     * <p>
     * Using the result of this method when sharing has only an effect on the system share sheet (if
     * OEMs didn't change Android system standard behavior) on Android API 29 and higher.
     * </p>
     *
     * @param context      the context to use
     * @param thumbnailUrl the URL of the content thumbnail
     * @return a {@link ClipData} of the content thumbnail, or {@code null}
     */
    @Nullable
    private static ClipData generateClipDataForImagePreview(
            @NonNull final Context context,
            @NonNull final String thumbnailUrl) {
        try {
            final Bitmap bitmap = PicassoHelper.getImageFromCacheIfPresent(thumbnailUrl);
            if (bitmap == null) {
                return null;
            }

            // Save the image in memory to the application's cache because we need a URI to the
            // image to generate a ClipData which will show the share sheet, and so an image file
            final Context applicationContext = context.getApplicationContext();
            final String appFolder = applicationContext.getCacheDir().getAbsolutePath();
            final File thumbnailPreviewFile = new File(appFolder
                    + "/android_share_sheet_image_preview.jpg");

            // Any existing file will be overwritten with FileOutputStream
            final FileOutputStream fileOutputStream = new FileOutputStream(thumbnailPreviewFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
            fileOutputStream.close();

            final ClipData clipData = ClipData.newUri(applicationContext.getContentResolver(), "",
                        FileProvider.getUriForFile(applicationContext,
                                BuildConfig.APPLICATION_ID + ".provider",
                                thumbnailPreviewFile));

            if (DEBUG) {
                Log.d(TAG, "ClipData successfully generated for Android share sheet: " + clipData);
            }
            return clipData;

        } catch (final Exception e) {
            Log.w(TAG, "Error when setting preview image for share sheet", e);
            return null;
        }
    }
}
