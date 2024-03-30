package org.schabi.newpipe.settings

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.util.image.PreferredImageQuality
import java.io.IOException

class ContentSettingsFragment() : BasePreferenceFragment() {
    private var youtubeRestrictedModeEnabledKey: String? = null
    private var initialSelectedLocalization: Localization? = null
    private var initialSelectedContentCountry: ContentCountry? = null
    private var initialLanguage: String? = null
    public override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled)
        addPreferencesFromResourceRegistry()
        initialSelectedLocalization = org.schabi.newpipe.util.Localization.getPreferredLocalization(requireContext())
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization.getPreferredContentCountry(requireContext())
        initialLanguage = defaultPreferences!!.getString(getString(R.string.app_language_key), "en")
        val imageQualityPreference: Preference = requirePreference(R.string.image_quality_key)
        imageQualityPreference.setOnPreferenceChangeListener(
                Preference.OnPreferenceChangeListener({ preference: Preference, newValue: Any? ->
                    ImageStrategy.setPreferredImageQuality(PreferredImageQuality.Companion.fromPreferenceKey(requireContext(), newValue as String?))
                    try {
                        PicassoHelper.clearCache(preference.getContext())
                        Toast.makeText(preference.getContext(),
                                R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                                .show()
                    } catch (e: IOException) {
                        Log.e(TAG, "Unable to clear Picasso cache", e)
                    }
                    true
                }))
    }

    public override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if ((preference.getKey() == youtubeRestrictedModeEnabledKey)) {
            val context: Context? = getContext()
            if (context != null) {
                DownloaderImpl.Companion.getInstance()!!.updateYoutubeRestrictedModeCookies(context)
            } else {
                Log.w(TAG, "onPreferenceTreeClick: null context")
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    public override fun onDestroy() {
        super.onDestroy()
        val selectedLocalization: Localization? = org.schabi.newpipe.util.Localization.getPreferredLocalization(requireContext())
        val selectedContentCountry: ContentCountry? = org.schabi.newpipe.util.Localization.getPreferredContentCountry(requireContext())
        val selectedLanguage: String? = defaultPreferences!!.getString(getString(R.string.app_language_key), "en")
        if ((!(selectedLocalization == initialSelectedLocalization)
                        || !(selectedContentCountry == initialSelectedContentCountry)
                        || !(selectedLanguage == initialLanguage))) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show()
            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry)
        }
    }
}
