package org.schabi.newpipe.about

import android.content.Context
import android.util.Base64
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.io.IOException

/**
 * @param context the context to use
 * @param license the license
 * @return String which contains a HTML formatted license page
 * styled according to the context's theme
 */
private fun getFormattedLicense(context: Context, license: License): String {
    try {
        return context.assets.open(license.filename).bufferedReader().use { it.readText() }
            // split the HTML file and insert the stylesheet into the HEAD of the file
            .replace("</head>", "<style>${getLicenseStylesheet(context)}</style></head>")
    } catch (e: IOException) {
        throw IllegalArgumentException("Could not get license file: ${license.filename}", e)
    }
}

/**
 * @param context the Android context
 * @return String which is a CSS stylesheet according to the context's theme
 */
private fun getLicenseStylesheet(context: Context): String {
    val isLightTheme = ThemeHelper.isLightThemeSelected(context)
    val licenseBackgroundColor = getHexRGBColor(
        context, if (isLightTheme) R.color.light_license_background_color else R.color.dark_license_background_color
    )
    val licenseTextColor = getHexRGBColor(
        context, if (isLightTheme) R.color.light_license_text_color else R.color.dark_license_text_color
    )
    val youtubePrimaryColor = getHexRGBColor(
        context, if (isLightTheme) R.color.light_youtube_primary_color else R.color.dark_youtube_primary_color
    )
    return "body{padding:12px 15px;margin:0;background:#$licenseBackgroundColor;color:#$licenseTextColor}" +
        "a[href]{color:#$youtubePrimaryColor}pre{white-space:pre-wrap}"
}

/**
 * Cast R.color to a hexadecimal color value.
 *
 * @param context the context to use
 * @param color   the color number from R.color
 * @return a six characters long String with hexadecimal RGB values
 */
private fun getHexRGBColor(context: Context, color: Int): String {
    return context.getString(color).substring(3)
}

fun showLicense(context: Context?, component: SoftwareComponent): Disposable {
    return showLicense(context, component.license) {
        setPositiveButton(R.string.dismiss) { dialog, _ ->
            dialog.dismiss()
        }
        setNeutralButton(R.string.open_website_license) { _, _ ->
            ShareUtils.openUrlInApp(context!!, component.link)
        }
    }
}

fun showLicense(context: Context?, license: License) = showLicense(context, license) {
    setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
}

private fun showLicense(
    context: Context?,
    license: License,
    block: AlertDialog.Builder.() -> AlertDialog.Builder
): Disposable {
    return if (context == null) {
        Disposable.empty()
    } else {
        Observable.fromCallable { getFormattedLicense(context, license) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { formattedLicense ->
                val webViewData =
                    Base64.encodeToString(formattedLicense.toByteArray(), Base64.NO_PADDING)
                val webView = WebView(context)
                webView.loadData(webViewData, "text/html; charset=UTF-8", "base64")

                Localization.assureCorrectAppLanguage(context)
                AlertDialog.Builder(context)
                    .setTitle(license.name)
                    .setView(webView)
                    .block()
                    .show()
            }
    }
}
