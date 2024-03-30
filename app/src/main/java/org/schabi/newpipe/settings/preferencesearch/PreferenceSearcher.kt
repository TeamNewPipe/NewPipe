package org.schabi.newpipe.settings.preferencesearch

import android.text.TextUtils
import java.util.stream.Collectors

class PreferenceSearcher(private val configuration: PreferenceSearchConfiguration) {
    private val allEntries: MutableList<PreferenceSearchItem?> = ArrayList()
    fun add(items: List<PreferenceSearchItem?>?) {
        allEntries.addAll((items)!!)
    }

    fun searchFor(keyword: String): List<PreferenceSearchItem?> {
        if (TextUtils.isEmpty(keyword)) {
            return emptyList<PreferenceSearchItem>()
        }
        return configuration.getSearcher()
                .search(allEntries.stream(), keyword)
                .collect(Collectors.toList())
    }

    fun clear() {
        allEntries.clear()
    }
}
