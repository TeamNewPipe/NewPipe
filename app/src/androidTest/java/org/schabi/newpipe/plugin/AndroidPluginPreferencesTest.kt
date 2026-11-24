package org.schabi.newpipe.plugin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.plugin.PluginSetting
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(AndroidJUnit4::class)
class AndroidPluginPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun credentialsStayWithCapturedProfileAndDeletedProfilesCannotBeRecreatedByStalePlugins() {
        val original = ProfileManager.getActiveProfileId(context)
        val first = requireNotNull(ProfileManager.createProfile(context, "Plugin A ${UUID.randomUUID().toString().take(8)}"))
        val second = requireNotNull(ProfileManager.createProfile(context, "Plugin B ${UUID.randomUUID().toString().take(8)}"))
        val pluginId = "test.profile.plugin"
        try {
            val firstPreferences = AndroidPluginPreferences(context, pluginId, emptyList(), first.id)
            val secondPreferences = AndroidPluginPreferences(context, pluginId, emptyList(), second.id)
            firstPreferences.putString("login_cookies", "session=first")
            secondPreferences.putString("login_cookies", "session=second")
            assertEquals("session=first", firstPreferences.getString("login_cookies"))
            assertEquals("session=second", secondPreferences.getString("login_cookies"))
            assertTrue(ProfileManager.setActiveProfile(context, second.id))
            firstPreferences.putString("login_cookies", "session=old-profile-operation")
            assertEquals("session=second", secondPreferences.getString("login_cookies"))

            assertTrue(ProfileManager.deleteProfile(context, first.id))
            firstPreferences.putString("login_cookies", "session=must-not-return")
            firstPreferences.putBoolean("remember", true)
            assertNull(firstPreferences.getString("login_cookies"))
            assertFalse(firstPreferences.getBoolean("remember"))
            assertTrue(firstPreferences.sharedPreferences.all.isEmpty())
            assertEquals("session=second", secondPreferences.getString("login_cookies"))
        } finally {
            ProfileManager.setActiveProfile(context, original)
            ProfileManager.deleteProfile(context, first.id)
            ProfileManager.deleteProfile(context, second.id)
        }
    }

    @Test
    fun manifestDefaultsAreReturnedWithoutCopyingValuesAcrossProfiles() {
        val profile = ProfileManager.getActiveProfileId(context)
        val pluginId = "test.defaults.${UUID.randomUUID().toString().replace("-", "")}"
        val preferences = AndroidPluginPreferences(
            context,
            pluginId,
            listOf(
                PluginSetting("base_url", PluginSetting.Type.STRING, "URL", null, "https://example.org"),
                PluginSetting("enabled", PluginSetting.Type.BOOLEAN, "Enabled", null, "true")
            ),
            profile
        )
        try {
            assertEquals("https://example.org", preferences.getString("base_url"))
            assertTrue(preferences.getBoolean("enabled"))
            assertNull(preferences.getString("login_cookies"))
            assertNotNull(ProfileManager.getProfile(context, profile))
            preferences.putBoolean("enabled", false)
            assertFalse(preferences.getBoolean("enabled"))
        } finally {
            preferences.sharedPreferences.edit().clear().commit()
        }
    }
}
