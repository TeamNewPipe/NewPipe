package org.schabi.newpipe.settings.preferencesearch;

import android.text.TextUtils;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class PreferenceSearchConfiguration {
    private BinaryOperator<String> breadcrumbConcat =
            (s1, s2) -> TextUtils.isEmpty(s1) ? s2 : (s1 + " > " + s2);

    private PreferenceSearchFunction searcher = new PreferenceFuzzySearchFunction();

    private final List<String> parserIgnoreElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName());
    private final List<String> parserContainerElements = Arrays.asList(
            PreferenceCategory.class.getSimpleName(),
            PreferenceScreen.class.getSimpleName());


    public void setBreadcrumbConcat(final BinaryOperator<String> breadcrumbConcat) {
        this.breadcrumbConcat = Objects.requireNonNull(breadcrumbConcat);
    }

    public void setSearcher(final PreferenceSearchFunction searcher) {
        this.searcher = Objects.requireNonNull(searcher);
    }

    public BinaryOperator<String> getBreadcrumbConcat() {
        return breadcrumbConcat;
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
