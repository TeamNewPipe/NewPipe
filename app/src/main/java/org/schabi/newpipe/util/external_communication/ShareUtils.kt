package org.schabi.newpipe.util.external_communication

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.extractor.Image
import java.io.File
import java.io.FileOutputStream

object ShareUtils {
    private val TAG: String = ShareUtils::class.java.getSimpleName()

    /**
     * Open an Intent to install an app.
     *
     *
     * This method tries to open the default app market with the package id passed as the
     * second param (a system chooser will be opened if there are multiple markets and no default)
     * and falls back to Google Play Store web URL if no app to handle the market scheme was found.
     *
     *
     * It uses [.openIntentInApp] to open market scheme and [ ][.openUrlInBrowser] to open Google Play Store web URL.
     *
     * @param context   the context to use
     * @param packageId the package id of the app to be installed
     */
    fun installApp(context: Context, packageId: String) {
        // Try market scheme
        val marketSchemeIntent: Intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageId))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryOpenIntentInApp(context, marketSchemeIntent)) {
            // Fall back to Google Play Store Web URL (F-Droid can handle it)
            openUrlInApp(context, "https://play.google.com/store/apps/details?id=" + packageId)
        }
    }

    /**
     * Open the url with the system default browser. If no browser is set as default, falls back to
     * [.openAppChooser].
     *
     *
     * This function selects the package to open based on which apps respond to the `http://`
     * schema alone, which should exclude special non-browser apps that are can handle the url (e.g.
     * the official YouTube app).
     *
     *
     * Therefore **please prefer [.openUrlInApp]**, that handles package
     * resolution in a standard way, unless this is the action of an explicit "Open in browser"
     * button.
     *
     * @param context the context to use
     * @param url     the url to browse
     */
    fun openUrlInBrowser(context: Context, url: String?) {
        // Resolve using a generic http://, so we are sure to get a browser and not e.g. the yt app.
        // Note that this requires the `http` schema to be added to `<queries>` in the manifest.
        val defaultBrowserInfo: ResolveInfo?
        val browserIntent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            defaultBrowserInfo = context.getPackageManager().resolveActivity(browserIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            defaultBrowserInfo = context.getPackageManager().resolveActivity(browserIntent,
                    PackageManager.MATCH_DEFAULT_ONLY)
        }
        val intent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (defaultBrowserInfo == null) {
            // No app installed to open a web URL, but it may be handled by other apps so try
            // opening a system chooser for the link in this case (it could be bypassed by the
            // system if there is only one app which can open the link or a default app associated
            // with the link domain on Android 12 and higher)
            openAppChooser(context, intent, true)
            return
        }
        val defaultBrowserPackage: String = defaultBrowserInfo.activityInfo.packageName
        if ((defaultBrowserPackage == "android")) {
            // No browser set as default (doesn't work on some devices)
            openAppChooser(context, intent, true)
        } else {
            try {
                intent.setPackage(defaultBrowserPackage)
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Not a browser but an app chooser because of OEMs changes
                intent.setPackage(null)
                openAppChooser(context, intent, true)
            }
        }
    }

    /**
     * Open a url with the system default app using [Intent.ACTION_VIEW], showing a toast in
     * case of failure.
     *
     * @param context the context to use
     * @param url     the url to open
     */
    fun openUrlInApp(context: Context, url: String?) {
        openIntentInApp(context, Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Open an intent with the system default app.
     *
     *
     * Use [.openIntentInApp] to show a toast in case of failure.
     *
     * @param context the context to use
     * @param intent  the intent to open
     * @return true if the intent could be opened successfully, false otherwise
     */
    fun tryOpenIntentInApp(context: Context,
                           intent: Intent): Boolean {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return false
        }
        return true
    }

    /**
     * Open an intent with the system default app, showing a toast in case of failure.
     *
     *
     * Use [.tryOpenIntentInApp] if you don't want the toast. Use [ ][.openUrlInApp] as a shorthand for [Intent.ACTION_VIEW] with urls.
     *
     * @param context the context to use
     * @param intent  the intent to
     */
    fun openIntentInApp(context: Context,
                        intent: Intent) {
        if (!tryOpenIntentInApp(context, intent)) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG)
                    .show()
        }
    }

    /**
     * Open the system chooser to launch an intent.
     *
     *
     * This method opens an [android.content.Intent.ACTION_CHOOSER] of the intent putted
     * as the intent param. If the setTitleChooser boolean is true, the string "Open with" will be
     * set as the title of the system chooser.
     * For Android P and higher, title for [android.content.Intent.ACTION_SEND] system
     * choosers must be set on this intent, not on the
     * [android.content.Intent.ACTION_CHOOSER] intent.
     *
     * @param context         the context to use
     * @param intent          the intent to open
     * @param setTitleChooser set the title "Open with" to the chooser if true, else not
     */
    private fun openAppChooser(context: Context,
                               intent: Intent,
                               setTitleChooser: Boolean) {
        val chooserIntent: Intent = Intent(Intent.ACTION_CHOOSER)
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (setTitleChooser) {
            chooserIntent.putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with))
        }

        // Migrate any clip data and flags from the original intent.
        val permFlags: Int = intent.getFlags() and ((Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        if (permFlags != 0) {
            var targetClipData: ClipData? = intent.getClipData()
            if (targetClipData == null && intent.getData() != null) {
                val item: ClipData.Item = ClipData.Item(intent.getData())
                val mimeTypes: Array<String>
                if (intent.getType() != null) {
                    mimeTypes = arrayOf(intent.getType())
                } else {
                    mimeTypes = arrayOf()
                }
                targetClipData = ClipData(null, mimeTypes, item)
            }
            if (targetClipData != null) {
                chooserIntent.setClipData(targetClipData)
                chooserIntent.addFlags(permFlags)
            }
        }
        try {
            context.startActivity(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show()
        }
    }
    /**
     * Open the android share sheet to share a content.
     *
     *
     *
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if its URL is not null or empty and its
     * corresponding image is in the image cache.
     *
     *
     * @param context         the context to use
     * @param title           the title of the content
     * @param content         the content to share
     * @param imagePreviewUrl the image of the subject
     */
    /**
     * Open the android share sheet to share a content.
     *
     *
     *
     * This calls [.shareText] with an empty string for the
     * `imagePreviewUrl` parameter. This method should be used when the shared content has no
     * preview thumbnail.
     *
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     */
    @JvmOverloads
    fun shareText(context: Context,
                  title: String,
                  content: String?,
                  imagePreviewUrl: String = "") {
        val shareIntent: Intent = Intent(Intent.ACTION_SEND)
        shareIntent.setType("text/plain")
        shareIntent.putExtra(Intent.EXTRA_TEXT, content)
        if (!TextUtils.isEmpty(title)) {
            shareIntent.putExtra(Intent.EXTRA_TITLE, title)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
        }

        // Content preview in the share sheet has been added in Android 10, so it's not needed to
        // set a content preview which will be never displayed
        // See https://developer.android.com/training/sharing/send#adding-rich-content-previews
        // If loading of images has been disabled, don't try to generate a content preview
        if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ) && !TextUtils.isEmpty(imagePreviewUrl)
                        && ImageStrategy.shouldLoadImages())) {
            val clipData: ClipData? = generateClipDataForImagePreview(context, imagePreviewUrl)
            if (clipData != null) {
                shareIntent.setClipData(clipData)
                shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        openAppChooser(context, shareIntent, false)
    }

    /**
     * Open the android share sheet to share a content.
     *
     *
     *
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if the preferred image chosen by [ ][ImageStrategy.choosePreferredImage] is in the image cache.
     *
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     * @param images  a set of possible [Image]s of the subject, among which to choose with
     * [ImageStrategy.choosePreferredImage] since that's likely to
     * provide an image that is in Picasso's cache
     */
    fun shareText(context: Context,
                  title: String,
                  content: String?,
                  images: List<Image?>?) {
        shareText(context, title, content, ImageStrategy.choosePreferredImage(images))
    }

    /**
     * Copy the text to clipboard, and indicate to the user whether the operation was completed
     * successfully using a Toast.
     *
     * @param context the context to use
     * @param text    the text to copy
     */
    fun copyToClipboard(context: Context, text: String?) {
        val clipboardManager: ClipboardManager? = ContextCompat.getSystemService<ClipboardManager>(context, ClipboardManager::class.java)
        if (clipboardManager == null) {
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
            return
        }
        try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
            if (Build.VERSION.SDK_INT < 33) {
                // Android 13 has its own "copied to clipboard" dialog
                Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error when trying to copy text to clipboard", e)
            Toast.makeText(context, R.string.msg_failed_to_copy, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generate a [ClipData] with the image of the content shared, if it's in the app cache.
     *
     *
     *
     * In order not to worry about network issues (timeouts, DNS issues, low connection speed, ...)
     * when sharing a content, only images in the [LruCache][com.squareup.picasso.LruCache]
     * used by the Picasso library inside [PicassoHelper] are used as preview images. If the
     * thumbnail image is not in the cache, no [ClipData] will be generated and `null`
     * will be returned.
     *
     *
     *
     *
     * In order to display the image in the content preview of the Android share sheet, an URI of
     * the content, accessible and readable by other apps has to be generated, so a new file inside
     * the application cache will be generated, named `android_share_sheet_image_preview.jpg`
     * (if a file under this name already exists, it will be overwritten). The thumbnail will be
     * compressed in JPEG format, with a `90` compression level.
     *
     *
     *
     *
     * Note that if an exception occurs when generating the [ClipData], `null` is
     * returned.
     *
     *
     *
     *
     * This method will call [PicassoHelper.getImageFromCacheIfPresent] to get the
     * thumbnail of the content in the [LruCache][com.squareup.picasso.LruCache] used by
     * the Picasso library inside [PicassoHelper].
     *
     *
     *
     *
     * Using the result of this method when sharing has only an effect on the system share sheet (if
     * OEMs didn't change Android system standard behavior) on Android API 29 and higher.
     *
     *
     * @param context      the context to use
     * @param thumbnailUrl the URL of the content thumbnail
     * @return a [ClipData] of the content thumbnail, or `null`
     */
    private fun generateClipDataForImagePreview(
            context: Context,
            thumbnailUrl: String): ClipData? {
        try {
            val bitmap: Bitmap? = PicassoHelper.getImageFromCacheIfPresent(thumbnailUrl)
            if (bitmap == null) {
                return null
            }

            // Save the image in memory to the application's cache because we need a URI to the
            // image to generate a ClipData which will show the share sheet, and so an image file
            val applicationContext: Context = context.getApplicationContext()
            val appFolder: String = applicationContext.getCacheDir().getAbsolutePath()
            val thumbnailPreviewFile: File = File((appFolder
                    + "/android_share_sheet_image_preview.jpg"))

            // Any existing file will be overwritten with FileOutputStream
            val fileOutputStream: FileOutputStream = FileOutputStream(thumbnailPreviewFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.close()
            val clipData: ClipData = ClipData.newUri(applicationContext.getContentResolver(), "",
                    FileProvider.getUriForFile(applicationContext,
                            BuildConfig.APPLICATION_ID + ".provider",
                            thumbnailPreviewFile))
            if (MainActivity.Companion.DEBUG) {
                Log.d(TAG, "ClipData successfully generated for Android share sheet: " + clipData)
            }
            return clipData
        } catch (e: Exception) {
            Log.w(TAG, "Error when setting preview image for share sheet", e)
            return null
        }
    }
}
