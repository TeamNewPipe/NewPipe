package org.schabi.newpipe.about

import android.content.Context
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper
import java.io.IOException

/**
 * @param context the context to use
 * @param license the license
 * @return String which contains a HTML formatted license page
 * styled according to the context's theme
 */
fun getFormattedLicense(context: Context, license: License): String {
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
fun getLicenseStylesheet(context: Context): String {
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
fun getHexRGBColor(context: Context, color: Int): String {
    return context.getString(color).substring(3)
}
