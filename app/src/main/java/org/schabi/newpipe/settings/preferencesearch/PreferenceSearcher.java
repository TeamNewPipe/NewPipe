package org.schabi.newpipe.settings.preferencesearch;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PreferenceSearcher {
    private final List<PreferenceSearchItem> allEntries = new ArrayList<>();

    private final PreferenceSearchConfiguration configuration;

    public PreferenceSearcher(final PreferenceSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    public void add(final List<PreferenceSearchItem> items) {
        allEntries.addAll(items);
    }

    List<PreferenceSearchItem> searchFor(final String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }

        return configuration.getSearcher()
                .search(allEntries.stream(), keyword)
                .collect(Collectors.toList());
    }

    public void clear() {
        allEntries.clear();
    }
}
