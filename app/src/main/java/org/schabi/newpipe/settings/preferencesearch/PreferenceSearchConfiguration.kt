package org.schabi.newpipe.settings.preferencesearch

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import java.util.Objects
import java.util.stream.Stream

class PreferenceSearchConfiguration() {
    private var searcher: PreferenceSearchFunction = PreferenceFuzzySearchFunction()
    private val parserIgnoreElements: List<String> = java.util.List.of(
            PreferenceCategory::class.java.getSimpleName())
    private val parserContainerElements: List<String> = java.util.List.of(
            PreferenceCategory::class.java.getSimpleName(),
            PreferenceScreen::class.java.getSimpleName())

    fun setSearcher(searcher: PreferenceSearchFunction) {
        this.searcher = Objects.requireNonNull(searcher)
    }

    fun getSearcher(): PreferenceSearchFunction {
        return searcher
    }

    fun getParserIgnoreElements(): List<String> {
        return parserIgnoreElements
    }

    fun getParserContainerElements(): List<String> {
        return parserContainerElements
    }

    open fun interface PreferenceSearchFunction {
        fun search(
                allAvailable: Stream<PreferenceSearchItem?>,
                keyword: String): Stream<PreferenceSearchItem?>
    }
}
