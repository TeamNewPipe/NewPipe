package org.schabi.newpipe.settings.preferencesearch

import androidx.annotation.XmlRes

/**
 * Represents a preference-item inside the search.
 *
 * @param key Key of the setting/preference. E.g. used inside [android.content.SharedPreferences].
 * @param title Title of the setting, e.g. 'Default resolution' or 'Show higher resolutions'.
 * @param summary Summary of the setting, e.g. '480p' or 'Only some devices can play 2k/4k'.
 * @param entries Possible entries of the setting, e.g. 480p,720p,...
 * @param breadcrumbs Breadcrumbs - a hint where the setting is located e.g. 'Video and Audio > Player'
 * @param searchIndexItemResId The xml-resource where this item was found/built from.
 */

data class PreferenceSearchItem(
    val key: String,
    val title: String,
    val summary: String,
    val entries: String,
    val breadcrumbs: String,
    @XmlRes val searchIndexItemResId: Int
) {
    fun hasData(): Boolean {
        return !key.isEmpty() && !title.isEmpty()
    }

    fun getAllRelevantSearchFields(): MutableList<String?> {
        return mutableListOf(title, summary, entries, breadcrumbs)
    }

    override fun toString(): String {
        return "PreferenceItem: $title $summary $key"
    }
}
