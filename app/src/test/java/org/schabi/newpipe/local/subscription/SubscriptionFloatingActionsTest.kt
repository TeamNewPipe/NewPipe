package org.schabi.newpipe.local.subscription

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SubscriptionFloatingActionsTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)

    @Test
    fun classicIsTheDefaultAndUnknownValuesDoNotRemoveTheExistingEntryPoints() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.subscription_actions_style_key)
        preferences.edit().remove(key).commit()
        assertFalse(SubscriptionFloatingActions.enabled(context))
        preferences.edit().putString(key, "floating").commit()
        assertTrue(SubscriptionFloatingActions.enabled(context))
        preferences.edit().putString(key, "unknown").commit()
        assertFalse(SubscriptionFloatingActions.enabled(context))
    }

    @Test
    fun visibilityAvailabilityAndKeyboardPreserveActionsAndOnlyReserveTheirOwnSpace() {
        val binding = FragmentSubscriptionBinding.inflate(LayoutInflater.from(context))
        binding.itemsList.setPaddingRelative(3, 5, 7, 11)
        binding.itemsList.clipToPadding = true
        var creates = 0
        var searches = 0
        var replacements = 0
        val actions = SubscriptionFloatingActions(binding, { creates++ }, { searches++ }, { replacements++ })
        val clearance = context.resources.getDimensionPixelSize(R.dimen.subscription_floating_action_size) +
            2 * context.resources.getDimensionPixelSize(R.dimen.margin_normal)

        actions.update(true, true, context.getString(R.string.open_link), R.drawable.ic_link)
        assertTrue(actions.searchIsVisible)
        assertEquals(context.getString(R.string.open_link), binding.subscriptionFloatingSearch.contentDescription)
        assertEquals(binding.subscriptionFloatingSearch.contentDescription, binding.subscriptionFloatingSearch.tooltipText)
        assertEquals(11 + clearance, binding.itemsList.paddingBottom)
        assertFalse(binding.itemsList.clipToPadding)
        binding.subscriptionFloatingCreate.performClick()
        binding.subscriptionFloatingSearch.performClick()
        assertEquals(listOf(1, 1), listOf(creates, searches))
        actions.update(true, true, context.getString(R.string.search), R.drawable.ic_search)
        assertEquals("An unchanged presentation must not invalidate the menu repeatedly", 1, replacements)

        actions.update(true, false, context.getString(R.string.search), R.drawable.ic_search)
        assertEquals(View.VISIBLE, binding.subscriptionFloatingActions.visibility)
        assertFalse(actions.searchIsVisible)
        binding.subscriptionFloatingSearch.performClick()
        assertEquals(1, searches)
        binding.subscriptionFloatingCreate.performClick()
        assertEquals(2, creates)

        actions.update(true, true, context.getString(R.string.search), R.drawable.ic_search)
        // API 30 tracks IME visibility independently from its inset dimensions.
        val keyboardShown = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        assertTrue(keyboardShown.isVisible(WindowInsetsCompat.Type.ime()))
        ViewCompat.dispatchApplyWindowInsets(binding.root, keyboardShown)
        assertEquals(View.GONE, binding.subscriptionFloatingActions.visibility)
        assertFalse(actions.searchIsVisible)
        assertEquals(11, binding.itemsList.paddingBottom)
        assertTrue(binding.itemsList.clipToPadding)
        binding.subscriptionFloatingCreate.performClick()
        binding.subscriptionFloatingSearch.performClick()
        assertEquals(listOf(2, 1), listOf(creates, searches))

        val keyboardHidden = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
        assertFalse(keyboardHidden.isVisible(WindowInsetsCompat.Type.ime()))
        ViewCompat.dispatchApplyWindowInsets(binding.root, keyboardHidden)
        assertTrue(actions.searchIsVisible)
        assertEquals(11 + clearance, binding.itemsList.paddingBottom)
        actions.update(false, true, context.getString(R.string.search), R.drawable.ic_search)
        assertFalse(actions.searchIsVisible)
        assertEquals(11, binding.itemsList.paddingBottom)
        actions.dispose()
        assertEquals(listOf(3, 5, 7, 11), listOf(binding.itemsList.paddingStart, binding.itemsList.paddingTop, binding.itemsList.paddingEnd, binding.itemsList.paddingBottom))
        assertTrue(binding.itemsList.clipToPadding)
    }
}
