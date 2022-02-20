package org.schabi.newpipe.settings.preferencesearch;

import androidx.annotation.NonNull;
import androidx.annotation.XmlRes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a preference-item inside the search.
 */
public class PreferenceSearchItem {
    /**
     * Key of the setting/preference. E.g. used inside {@link android.content.SharedPreferences}.
     */
    @NonNull
    private final String key;
    /**
     * Title of the setting, e.g. 'Default resolution' or 'Show higher resolutions'.
     */
    @NonNull
    private final String title;
    /**
     * Summary of the setting, e.g. '480p' or 'Only some devices can play 2k/4k'.
     */
    @NonNull
    private final String summary;
    /**
     * Possible entries of the setting, e.g. 480p,720p,...
     */
    @NonNull
    private final String entries;
    /**
     *  Breadcrumbs - a hint where the setting is located e.g. 'Video and Audio > Player'
     */
    @NonNull
    private final String breadcrumbs;
    /**
     * The xml-resource where this item was found/built from.
     */
    @XmlRes
    private final int searchIndexItemResId;

    public PreferenceSearchItem(
            @NonNull final String key,
            @NonNull final String title,
            @NonNull final String summary,
            @NonNull final String entries,
            @NonNull final String breadcrumbs,
            @XmlRes final int searchIndexItemResId
    ) {
        this.key = Objects.requireNonNull(key);
        this.title = Objects.requireNonNull(title);
        this.summary = Objects.requireNonNull(summary);
        this.entries = Objects.requireNonNull(entries);
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
            getBreadcrumbs());
    }


    @Override
    public String toString() {
        return "PreferenceItem: " + title + " " + summary + " " + key;
    }
}
