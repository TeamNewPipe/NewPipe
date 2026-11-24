/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import org.junit.Test;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsJsonHelper;

import java.util.List;

public class MainTabStateRestoreTest {
    @Test
    public void fallsBackToHostAndPluginClassLoaderForRestoredState() throws Exception {
        final ClassLoader stateLoader =
                MainFragment.SelectedTabsPagerAdapter.resolveStateClassLoader(null);

        assertSame(org.schabi.newpipe.plugin.PluginManager.getHostAndPluginsClassLoader(),
                stateLoader);
        assertSame(Fragment.SavedState.class,
                Class.forName(Fragment.SavedState.class.getName(), false, stateLoader));
    }

    @Test
    public void preservesProvidedClassResolutionForRestoredState() throws Exception {
        final ClassLoader providedLoader = getClass().getClassLoader();
        final ClassLoader stateLoader =
                MainFragment.SelectedTabsPagerAdapter.resolveStateClassLoader(providedLoader);
        assertSame(providedLoader, stateLoader.getParent());
        assertSame(getClass(), Class.forName(getClass().getName(), false, stateLoader));
    }

    @Test
    public void rejectsShiftedTabsAndOtherProfilesButPreservesMatchingState() {
        final List<Tab> original = List.of(Tab.Type.FEED.getTab(),
                Tab.Type.LEARNING.getTab(), Tab.Type.BOOKMARKS.getTab());
        final Bundle state = new Bundle();
        state.putString("tab_profile", "default");
        state.putString("tab_layout", TabsJsonHelper.getJsonToSave(original));
        assertTrue(MainFragment.SelectedTabsPagerAdapter.matchesTabState(
                state, "default", original));
        assertFalse(MainFragment.SelectedTabsPagerAdapter.matchesTabState(
                state, "other", original));
        assertFalse(MainFragment.SelectedTabsPagerAdapter.matchesTabState(
                state, "default", List.of(Tab.Type.FEED.getTab(), Tab.Type.BOOKMARKS.getTab())));
        assertFalse(MainFragment.SelectedTabsPagerAdapter.matchesTabState(
                new Bundle(), "default", original));
    }

    @Test
    public void removesOnlyMissingFragmentEntries() {
        final Bundle state = new Bundle();
        state.putString("f0", "missing");
        state.putString("f1", "available");
        state.putString("states", "pager-state");

        final Bundle cleaned =
                MainFragment.SelectedTabsPagerAdapter.removeMissingFragmentEntries(
                        state,
                        getClass().getClassLoader(),
                        (bundle, key) -> {
                            if ("f0".equals(key)) {
                                throw new IllegalStateException("Fragment no longer exists");
                            }
                        }
                );

        assertFalse(cleaned.containsKey("f0"));
        assertTrue(cleaned.containsKey("f1"));
        assertTrue(cleaned.containsKey("states"));
        assertTrue(state.containsKey("f0"));
    }
}
