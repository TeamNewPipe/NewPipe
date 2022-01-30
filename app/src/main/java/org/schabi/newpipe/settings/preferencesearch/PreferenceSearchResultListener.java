package org.schabi.newpipe.settings.preferencesearch;

import androidx.annotation.NonNull;

public interface PreferenceSearchResultListener {
    void onSearchResultClicked(@NonNull PreferenceSearchItem result);
}
