package org.schabi.newpipe.ui.components.about

import android.content.Context
import androidx.annotation.StringRes

class AboutData(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val buttonText: Int,
    @StringRes val url: Int
)

/**
 * Class for storing information about a software license.
 */
class License(val name: String, val abbreviation: String, val filename: String) {
    fun getFormattedLicense(context: Context): String {
        return context.assets.open(filename).bufferedReader().use { it.readText() }
    }
}

class SoftwareComponent(
    val name: String,
    val years: String,
    val copyrightOwner: String,
    val link: String,
    val license: License
)
