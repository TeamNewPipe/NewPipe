package org.schabi.newpipe.about

import android.content.Context
import androidx.annotation.AttrRes
import com.google.android.material.R as MaterialR
import java.io.IOException
import java.util.Locale
import org.schabi.newpipe.util.ThemeHelper

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
    val licenseBackgroundColor = getThemeHexRGBColor(context, MaterialR.attr.colorSurface)
    val licenseTextColor = getThemeHexRGBColor(context, MaterialR.attr.colorOnSurface)
    val licenseLinkColor = getThemeHexRGBColor(context, MaterialR.attr.colorPrimary)
    return "body{padding:12px 15px;margin:0;background:#$licenseBackgroundColor;color:#$licenseTextColor}" +
        "a[href]{color:#$licenseLinkColor}pre{white-space:pre-wrap}"
}

/**
 * Resolve a theme color attr to a hexadecimal RGB value for WebView CSS.
 *
 * @param context the context to use
 * @param attrColor the theme color attr to resolve
 * @return a six characters long String with hexadecimal RGB values
 */
private fun getThemeHexRGBColor(context: Context, @AttrRes attrColor: Int): String {
    return String.format(
        Locale.ROOT,
        "%06X",
        ThemeHelper.resolveColorFromAttr(context, attrColor) and 0xFFFFFF
    )
}
