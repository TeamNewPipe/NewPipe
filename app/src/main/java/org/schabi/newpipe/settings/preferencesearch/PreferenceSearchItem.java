package org.schabi.newpipe.settings.preferencesearch;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a preference-item inside the search.
 */
public class PreferenceSearchItem {
    @NonNull
    private final String key;
    @NonNull
    private final String title;
    @NonNull
    private final String summary;
    @NonNull
    private final String entries;
    @NonNull
    private final String keywords;
    @NonNull
    private final String breadcrumbs;
    private final int searchIndexItemResId;

    public PreferenceSearchItem(
            @NonNull final String key,
            @NonNull final String title,
            @NonNull final String summary,
            @NonNull final String entries,
            @NonNull final String keywords,
            @NonNull final String breadcrumbs,
            final int searchIndexItemResId
    ) {
        this.key = Objects.requireNonNull(key);
        this.title = Objects.requireNonNull(title);
        this.summary = Objects.requireNonNull(summary);
        this.entries = Objects.requireNonNull(entries);
        this.keywords = Objects.requireNonNull(keywords);
        this.breadcrumbs = Objects.requireNonNull(breadcrumbs);
        this.searchIndexItemResId = searchIndexItemResId;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getEntries() {
        return entries;
    }

    public String getBreadcrumbs() {
        return breadcrumbs;
    }

    public String getKeywords() {
        return keywords;
    }

    public int getSearchIndexItemResId() {
        return searchIndexItemResId;
    }

    boolean hasData() {
        return !key.isEmpty() && !title.isEmpty();
    }

    public List<String> getAllRelevantSearchFields() {
        return Arrays.asList(
            getTitle(),
            getSummary(),
            getEntries(),
            getBreadcrumbs(),
            getKeywords());
    }


    @Override
    public String toString() {
        return "PreferenceItem: " + title + " " + summary + " " + key;
    }
}
