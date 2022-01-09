package org.schabi.newpipe.settings.preferencesearch;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PreferenceSearchConfiguration {
    private PreferenceSearchFunction searcher = new PreferenceFuzzySearchFunction();

    private final List<String> parserIgnoreElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName());
    private final List<String> parserContainerElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName(),
            PreferenceScreen.class.getSimpleName());


    public void setSearcher(final PreferenceSearchFunction searcher) {
        this.searcher = Objects.requireNonNull(searcher);
    }

    public PreferenceSearchFunction getSearcher() {
        return searcher;
    }

    public List<String> getParserIgnoreElements() {
        return parserIgnoreElements;
    }

    public List<String> getParserContainerElements() {
        return parserContainerElements;
    }

    @FunctionalInterface
    public interface PreferenceSearchFunction {
        Stream<PreferenceSearchItem> search(
                Stream<PreferenceSearchItem> allAvailable,
                String keyword);
    }
}
