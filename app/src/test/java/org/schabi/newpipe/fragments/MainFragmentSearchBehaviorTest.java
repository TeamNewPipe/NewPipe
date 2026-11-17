package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainFragmentSearchBehaviorTest {
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void currentTabPreferenceUsesContextualSearchWhenAvailable() {
        assertTrue(MainFragment.shouldUseContextualSearch(false, true));
        assertFalse(MainFragment.shouldUseContextualSearch(false, false));
    }

    @Test
    public void globalSearchPreferenceSkipsContextualSearch() {
        assertFalse(MainFragment.shouldUseContextualSearch(true, true));
        assertFalse(MainFragment.shouldUseContextualSearch(true, false));
    }

    @Test
    public void contentSettingsExposeSearchButtonBehavior() throws Exception {
        final String settings = Files.readString(
                resourcesDirectory.resolve("xml/content_settings.xml"));
        assertTrue(settings.contains("@string/search_button_behavior_key"));
        assertTrue(settings.contains("@array/search_button_behavior_entries"));
        assertTrue(settings.contains("@array/search_button_behavior_values"));
        assertTrue(settings.contains(
                "android:defaultValue=\"@string/search_button_behavior_current_service_value\""));
    }
}
