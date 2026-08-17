package org.schabi.newpipe.util.external_communication

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.toBitmap
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.util.image.ImageStrategy
import java.nio.file.Files
import kotlin.io.path.toPath

object ShareUtils {
    private val TAG = ShareUtils::class.java.simpleName

    /**
     * Open an Intent to install an app.
     *
     * This method tries to open the default app market with the package id passed as the
     * second param (a system chooser will be opened if there are multiple markets and no default)
     * and falls back to Google Play Store web URL if no app to handle the market scheme was found.
     *
     * It uses [openIntentInApp] to open market scheme and [openUrlInBrowser] to open Google Play Store web URL.
     *
     * @param context   the context to use
     * @param packageId the package id of the app to be installed
     */
    @JvmStatic
    fun installApp(context: Context, packageId: String) {
        // Try market scheme
        val marketSchemeIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageId")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!tryOpenIntentInApp(context, marketSchemeIntent)) {
            // Fall back to Google Play Store Web URL (F-Droid can handle it)
            openUrlInApp(context, "https://play.google.com/store/apps/details?id=$packageId")
        }
    }

    /**
     * Open the url with the system default browser. If no browser is installed, falls back to
     * [openAppChooser] (for displaying that no apps are available
     * to handle the action, or possible OEM-related edge cases).
     *
     * This function selects the package to open based on which apps respond to the `http://`
     * schema alone, which should exclude special non-browser apps that are can handle the url (e.g.
     * the official YouTube app).
     *
     * Therefore **please prefer [openUrlInApp]**, that handles package
     * resolution in a standard way, unless this is the action of an explicit "Open in browser"
     * button.
     *
     * @param context the context to use
     * @param url     the url to browse
     **/
    @JvmStatic
    fun openUrlInBrowser(context: Context, url: String) {
        // Target a generic http://, so we are sure to get a browser and not e.g. the yt app.
        // Note that this requires the `http` schema to be added to `<queries>` in the manifest.
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // See https://stackoverflow.com/a/58801285 and `setSelector` documentation
        intent.selector = browserIntent
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No browser is available. This should, in the end, yield a nice AOSP error message
            // indicating that no app is available to handle this action.
            //
            // Note: there are some situations where modified OEM ROMs have apps that appear
            // to be browsers but are actually app choosers. If starting the Activity fails
            // related to this, opening the system app chooser is still the correct behavior.
            intent.selector = null
            openAppChooser(context, intent, true)
        }
    }

    /**
     * Open a url with the system default app using [Intent.ACTION_VIEW], showing a toast in
     * case of failure.
     *
     * @param context the context to use
     * @param url     the url to open
     */
    @JvmStatic
    fun openUrlInApp(context: Context, url: String) {
        openIntentInApp(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Open an intent with the system default app.
     *
     * Use [openIntentInApp] to show a toast in case of failure.
     *
     * @param context the context to use
     * @param intent  the intent to open
     * @return true if the intent could be opened successfully, false otherwise
     */
    @JvmStatic
    fun tryOpenIntentInApp(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Open an intent with the system default app, showing a toast in case of failure.
     *
     * Use [tryOpenIntentInApp] if you don't want the toast. Use [openUrlInApp] as a shorthand for [Intent.ACTION_VIEW] with urls.
     *
     * @param context the context to use
     * @param intent  the intent to
     */
    @JvmStatic
    fun openIntentInApp(context: Context, intent: Intent) {
        if (!tryOpenIntentInApp(context, intent)) {
            Toast.makeText(context, R.string.no_app_to_open_intent, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Open the system chooser to launch an intent.
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
    private fun openAppChooser(
        context: Context,
        intent: Intent,
        setTitleChooser: Boolean
    ) {
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, intent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (setTitleChooser) {
                putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with))
            }

            // Avoid opening in NewPipe
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, RouterActivity::class.java))
                )
            }
        }

        // Migrate any clip data and flags from the original intent.
        val permFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

        if (permFlags != 0) {
            val targetClipData = intent.clipData ?: intent.data?.let { data ->
                val mimeTypes = intent.type?.let { arrayOf(it) } ?: emptyArray()
                ClipData(null, mimeTypes, ClipData.Item(data))
            }

            targetClipData?.let {
                chooserIntent.clipData = it
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
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if its URL is not null or empty and its
     * corresponding image is in the image cache.
     *
     * @param context         the context to use
     * @param title           the title of the content
     * @param content         the content to share
     * @param imagePreviewUrl the image of the subject
     */
    @JvmStatic
    fun shareText(
        context: Context,
        title: String,
        content: String,
        imagePreviewUrl: String
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            if (title.isNotEmpty()) {
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            imagePreviewUrl.isNotEmpty() &&
            ImageStrategy.shouldLoadImages()
        ) {
            generateClipDataForImagePreview(context, imagePreviewUrl)?.let { clipData ->
                shareIntent.clipData = clipData
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        openAppChooser(context, shareIntent, false)
    }

    /**
     * Open the android share sheet to share a content.
     *
     * For Android 10+ users, a content preview is shown, which includes the title of the shared
     * content and an image preview the content, if the preferred image chosen by [ImageStrategy.choosePreferredImage] is in the image cache.
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     * @param images  a set of possible [Image]s of the subject, among which to choose with
     * [ImageStrategy.choosePreferredImage] since that's likely to
     * provide an image that is in Coil's cache
     */
    @JvmStatic
    fun shareText(
        context: Context,
        title: String,
        content: String,
        images: List<Image>
    ) {
        shareText(context, title, content, ImageStrategy.choosePreferredImage(images) ?: "")
    }

    /**
     * Open the android share sheet to share a content.
     *
     * This calls [shareText] with an empty string for the
     * `imagePreviewUrl` parameter. This method should be used when the shared content has no
     * preview thumbnail.
     *
     * @param context the context to use
     * @param title   the title of the content
     * @param content the content to share
     */
    @JvmStatic
    fun shareText(context: Context, title: String, content: String) {
        shareText(context, title, content, "")
    }

    /**
     * Copy the text to clipboard, and indicate to the user whether the operation was completed
     * successfully using a Toast.
     *
     * @param context the context to use
     * @param text    the text to copy
     */
    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val clipboardManager = ContextCompat.getSystemService(context, ClipboardManager::class.java)

        if (clipboardManager == null) {
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
            return
        }

        try {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ has its own "copied to clipboard" dialog
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
     * In order not to worry about network issues (timeouts, DNS issues, low connection speed, ...)
     * when sharing a content, only images in the [MemoryCache] or [coil3.disk.DiskCache]
     * used by the Coil library are used as preview images. If the thumbnail image is not in the
     * cache, no [ClipData] will be generated and `null` will be returned.
     *
     * In order to display the image in the content preview of the Android share sheet, an URI of
     * the content, accessible and readable by other apps has to be generated, so a new file inside
     * the application cache will be generated, named `android_share_sheet_image_preview.jpg`
     * (if a file under this name already exists, it will be overwritten). The thumbnail will be
     * compressed in JPEG format, with a `90` compression level.
     *
     * Note that if an exception occurs when generating the [ClipData], `null` is
     * returned.
     *
     * Using the result of this method when sharing has only an effect on the system share sheet (if
     * OEMs didn't change Android system standard behavior) on Android API 29 and higher.
     *
     * @param context      the context to use
     * @param thumbnailUrl the URL of the content thumbnail
     * @return a [ClipData] of the content thumbnail, or `null`
     */
    private fun generateClipDataForImagePreview(
        context: Context,
        thumbnailUrl: String
    ): ClipData? {
        return try {
            val applicationContext = context.applicationContext
            val loader = SingletonImageLoader.get(context)
            val value = loader.memoryCache?.get(MemoryCache.Key(thumbnailUrl))

            // Attempt to load from memory, fallback to disk cache
            val cachedBitmap = value?.image?.toBitmap() ?: loader.diskCache?.openSnapshot(thumbnailUrl)?.use { snapshot ->
                BitmapFactory.decodeFile(snapshot.data.toFile().path)
            } ?: return null

            val path = applicationContext.cacheDir.toPath().resolve("android_share_sheet_image_preview.jpg")
            Files.newOutputStream(path).use { outputStream ->
                cachedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }

            ClipData.newUri(
                applicationContext.contentResolver,
                "",
                FileProvider.getUriForFile(
                    applicationContext,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    path.toFile()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error when setting preview image for share sheet", e)
            null
        }
    }
}