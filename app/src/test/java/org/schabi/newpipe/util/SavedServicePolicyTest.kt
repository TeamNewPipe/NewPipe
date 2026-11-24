/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedServicePolicyTest {
    @Test
    fun `missing plugins retain saved data but cannot be refreshed`() {
        val available = setOf(0, 10)
        assertTrue(SavedServicePolicy.canPreserve(1_234_567, available))
        assertFalse(SavedServicePolicy.canRefresh(1_234_567, available, null, "profile-a"))
        assertFalse(SavedServicePolicy.canPreserve(-1, available))
        assertFalse(SavedServicePolicy.canPreserve(999, available))
        assertFalse(SavedServicePolicy.canPreserve(Int.MAX_VALUE, available))
    }

    @Test
    fun `plugin feed requests use only their owning profile configuration`() {
        val available = setOf(0, 1_234_567)
        assertTrue(SavedServicePolicy.canRefresh(1_234_567, available, "profile-a", "profile-a"))
        assertFalse(SavedServicePolicy.canRefresh(1_234_567, available, "profile-a", "profile-b"))
        assertFalse(SavedServicePolicy.canRefresh(1_234_567, available, null, "profile-a"))
        assertTrue(SavedServicePolicy.canRefresh(0, available, "profile-a", "profile-b"))
    }

    @Test
    fun `missing provider does not crash saved-tab search action configuration`() {
        assertFalse(ServiceSearchSupport.supportsSearch(1_234_567))
        assertFalse(ServiceSearchSupport.canOpenSearch(1_234_567))
        assertTrue(ServiceSearchSupport.supportsSearch(0))
    }
}
