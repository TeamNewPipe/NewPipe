/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainPagerReusePolicyTest {
    @Test
    public void unchangedTabsKeepExistingPagerAndChildFragments() {
        assertFalse(MainFragment.shouldRebuildPager(false, true, false));
    }

    @Test
    public void missingAdapterOrDifferentTabsRequireRebuild() {
        assertTrue(MainFragment.shouldRebuildPager(true, true, false));
        assertTrue(MainFragment.shouldRebuildPager(false, false, false));
    }

    @Test
    public void explicitTabAndRestrictedModeRefreshStillRebuilds() {
        assertTrue(MainFragment.shouldRebuildPager(false, true, true));
    }
}
