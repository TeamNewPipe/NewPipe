package org.schabi.newpipe.settings.preferencesearch

import androidx.annotation.XmlRes
import java.util.Objects

/**
 * Represents a preference-item inside the search.
 */
class PreferenceSearchItem(
        key: String,
        title: String,
        summary: String,
        entries: String,
        breadcrumbs: String,
        /**
         * The xml-resource where this item was found/built from.
         */
        @field:XmlRes @param:XmlRes private val searchIndexItemResId: Int
) {
    /**
     * Key of the setting/preference. E.g. used inside [android.content.SharedPreferences].
     */
    private val key: String

    /**
     * Title of the setting, e.g. 'Default resolution' or 'Show higher resolutions'.
     */
    private val title: String

    /**
     * Summary of the setting, e.g. '480p' or 'Only some devices can play 2k/4k'.
     */
    private val summary: String

    /**
     * Possible entries of the setting, e.g. 480p,720p,...
     */
    private val entries: String

    /**
     * Breadcrumbs - a hint where the setting is located e.g. 'Video and Audio > Player'
     */
    private val breadcrumbs: String

    init {
        this.key = Objects.requireNonNull(key)
        this.title = Objects.requireNonNull(title)
        this.summary = Objects.requireNonNull(summary)
        this.entries = Objects.requireNonNull(entries)
        this.breadcrumbs = Objects.requireNonNull(breadcrumbs)
    }

    fun getKey(): String {
        return key
    }

    fun getTitle(): String {
        return title
    }

    fun getSummary(): String {
        return summary
    }

    fun getEntries(): String {
        return entries
    }

    fun getBreadcrumbs(): String {
        return breadcrumbs
    }

    fun getSearchIndexItemResId(): Int {
        return searchIndexItemResId
    }

    fun hasData(): Boolean {
        return !key.isEmpty() && !title.isEmpty()
    }

    fun getAllRelevantSearchFields(): List<String> {
        return java.util.List.of(getTitle(), getSummary(), getEntries(), getBreadcrumbs())
    }

    public override fun toString(): String {
        return "PreferenceItem: " + title + " " + summary + " " + key
    }
}
