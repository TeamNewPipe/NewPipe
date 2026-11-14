/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TitleFilterTest {

    private static boolean matches(final String query, final String title) {
        final TitleFilter filter = TitleFilter.of(query);
        return filter != null && filter.matches(title);
    }

    @Test
    public void ignoresCaseAndAccents() {
        assertTrue(matches("beyonce", "Beyoncé - Live"));
        assertTrue(matches("CAFÉ", "cafe del mar"));
        assertTrue(matches("musica", "Música Andina"));
    }

    @Test
    public void foldsCyrillicCase() {
        assertTrue(matches("плејлиста", "Моја ПЛЕЈЛИСТА"));
    }

    @Test
    public void requiresEveryWordInAnyOrder() {
        assertTrue(matches("live artist", "Artist - Song Title (Live 1985)"));
        assertFalse(matches("live studio", "Artist - Song Title (Live 1985)"));
        assertTrue(matches("  lofi   beats ", "Lofi Beats to Study"));
    }

    @Test
    public void rejectsMissingTitle() {
        assertFalse(matches("x", null));
    }

    @Test
    public void blankQueryMeansNoFilter() {
        assertNull(TitleFilter.of(null));
        assertNull(TitleFilter.of(""));
        assertNull(TitleFilter.of("   "));
    }
}
