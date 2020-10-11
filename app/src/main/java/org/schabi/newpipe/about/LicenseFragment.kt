package org.schabi.newpipe.about

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ShareUtils
import org.schabi.newpipe.util.ThemeHelper

/**
 * @return String which is a CSS stylesheet according to the context's theme
 */
private val Context.licenseStylesheet: String
    get() {
        val isLightTheme = ThemeHelper.isLightThemeSelected(this)
        return ("body{padding:12px 15px;margin:0;" + "background:#" +
                getHexRGBColor(if (isLightTheme) R.color.light_license_background_color else R.color.dark_license_background_color) + ";" +
                "color:#" + getHexRGBColor(if (isLightTheme) R.color.light_license_text_color else R.color.dark_license_text_color) + "}" +
                "a[href]{color:#" + getHexRGBColor(if (isLightTheme) R.color.light_youtube_primary_color else R.color.dark_youtube_primary_color) + "}" +
                "pre{white-space:pre-wrap}")
    }

/**
 * @param license the license
 * @return String which contains a HTML formatted license page
 * styled according to the context's theme
 */
private suspend fun Context.getFormattedLicense(license: License): String {
    val webViewData: String
    try {
        val licenseContent = withContext(Dispatchers.IO) {
            assets.open(license.filename).bufferedReader(Charsets.UTF_8)
                    .readLines().joinToString(separator = "")
        }

        // split the HTML file and insert the stylesheet into the HEAD of the file
        webViewData = licenseContent.replace("</head>", "<style>$licenseStylesheet</style></head>")
    } catch (e: IOException) {
        throw IllegalArgumentException("Could not get license file: " + license.filename, e)
    }
    return webViewData
}

/**
 * Cast R.color to a hexadecimal color value.
 *
 * @param color the color number from R.color
 * @return a six characters long String with hexadecimal RGB values
 */
private fun Context.getHexRGBColor(color: Int) = resources.getString(color).substring(3)

/**
 * Shows a popup containing the license.
 *
 * @param license the license to show
 */
private suspend fun Activity.showLicense(license: License) {
    val webViewData = Base64.encodeToString(getFormattedLicense(license).toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    val webView = WebView(this)
    webView.loadData(webViewData, "text/html; charset=UTF-8", "base64")

    val alert = AlertDialog.Builder(this)
    alert.setTitle(license.name)
    alert.setView(webView)
    Localization.assureCorrectAppLanguage(this)
    alert.setNegativeButton(getString(R.string.finish)) { dialog, _ -> dialog.dismiss() }
    alert.show()
}

/**
 * Fragment containing the software licenses.
 */
class LicenseFragment : Fragment() {
    private lateinit var softwareComponents: Array<SoftwareComponent>
    private lateinit var activeLicense: License

    private var componentForContextMenu: SoftwareComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        softwareComponents = requireArguments().getParcelableArray(ARG_COMPONENTS) as Array<SoftwareComponent>
        if (savedInstanceState != null) {
            val license = savedInstanceState.getSerializable(LICENSE_KEY)
            if (license != null) {
                activeLicense = license as License
            }
        }
        // Sort components by name
        softwareComponents.sortBy { it.name }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_licenses, container, false)
        val softwareComponentsView = rootView.findViewById<ViewGroup>(R.id.software_components)
        val licenseLink = rootView.findViewById<View>(R.id.app_read_license)
        licenseLink.setOnClickListener {
            lifecycleScope.launch { activity?.showLicense(StandardLicenses.GPL3) }
        }
        for (component in softwareComponents) {
            val componentView = inflater
                    .inflate(R.layout.item_software_component, container, false)
            val softwareName = componentView.findViewById<TextView>(R.id.name)
            val copyright = componentView.findViewById<TextView>(R.id.copyright)
            softwareName.text = component.name
            copyright.text = getString(R.string.copyright,
                    component.years,
                    component.copyrightOwner,
                    component.license.abbreviation)
            componentView.tag = component
            componentView.setOnClickListener {
                activeLicense = component.license
                lifecycleScope.launch { activity?.showLicense(component.license) }
            }
            softwareComponentsView.addView(componentView)
            registerForContextMenu(componentView)
        }
        if (this::activeLicense.isInitialized) {
            lifecycleScope.launch { activity?.showLicense(activeLicense) }
        }
        return rootView
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val inflater = requireActivity().menuInflater
        val component = v.tag as SoftwareComponent
        menu.setHeaderTitle(component.name)
        inflater.inflate(R.menu.software_component, menu)
        super.onCreateContextMenu(menu, v, menuInfo)
        componentForContextMenu = v.tag as SoftwareComponent
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        // item.getMenuInfo() is null so we use the tag of the view
        val component = componentForContextMenu ?: return false
        when (item.itemId) {
            R.id.action_website -> {
                ShareUtils.openUrlInBrowser(activity, component.link)
                return true
            }
            R.id.action_show_license -> lifecycleScope.launch { activity?.showLicense(component.license) }
        }
        return false
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        if (this::activeLicense.isInitialized) {
            savedInstanceState.putSerializable(LICENSE_KEY, activeLicense)
        }
    }

    companion object {
        private const val ARG_COMPONENTS = "components"
        private const val LICENSE_KEY = "ACTIVE_LICENSE"

        @JvmStatic
        fun newInstance(softwareComponents: Array<SoftwareComponent?>): LicenseFragment {
            return LicenseFragment().apply { arguments = bundleOf(ARG_COMPONENTS to softwareComponents) }
        }
    }
}
