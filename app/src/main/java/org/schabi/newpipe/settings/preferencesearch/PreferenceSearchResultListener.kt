package org.schabi.newpipe.settings.preferencesearch

open interface PreferenceSearchResultListener {
    fun onSearchResultClicked(result: PreferenceSearchItem)
}
