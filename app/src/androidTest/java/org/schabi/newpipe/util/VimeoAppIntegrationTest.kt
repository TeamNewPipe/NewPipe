package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.settings.tabs.Tab

@RunWith(AndroidJUnit4::class)
class VimeoAppIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val serviceKey = context.getString(R.string.current_service_key)
    private val themeKey = context.getString(R.string.theme_key)
    private val previousService = preferences.getString(serviceKey, null)
    private val previousTheme = preferences.getString(themeKey, null)

    @After
    fun restorePreferences() {
        preferences.edit().putString(serviceKey, previousService).putString(themeKey, previousTheme).commit()
    }

    @Test
    fun selectedVimeoIsPersistedAndItsIconCanBeInflated() {
        ServiceHelper.setSelectedServiceId(context, ServiceList.Vimeo.serviceId)
        assertEquals("Vimeo", preferences.getString(serviceKey, null))
        assertEquals(ServiceList.Vimeo.serviceId, ServiceHelper.getSelectedServiceId(context))
        assertSame(ServiceList.Vimeo, ServiceHelper.getSelectedService(context))
        assertEquals("Vimeo", ServiceHelper.getSelectedServiceName(context))
        assertNotNull(AppCompatResources.getDrawable(context, ServiceHelper.getSelectedServiceIcon(context)))
        assertEquals(context.getString(R.string.vimeo_staff_picks), Tab.DefaultKioskTab().getTabName(context))
        assertEquals(R.drawable.ic_stars, Tab.DefaultKioskTab().getTabIconRes(context))
    }

    @Test
    fun allVimeoThemesResolveWithReadablePrimaryText() {
        for ((key, style) in listOf(
            R.string.light_theme_key to R.style.LightTheme_Vimeo,
            R.string.dark_theme_key to R.style.DarkTheme_Vimeo,
            R.string.black_theme_key to R.style.BlackTheme_Vimeo
        )) {
            preferences.edit().putString(themeKey, context.getString(key)).commit()
            val actual = ThemeHelper.getThemeForService(context, ServiceList.Vimeo.serviceId)
            assertEquals(style, actual)
            val themed = ContextThemeWrapper(context, actual)
            val primary = MaterialColors.getColor(themed, androidx.appcompat.R.attr.colorPrimary, "Vimeo")
            val onPrimary = MaterialColors.getColor(themed, com.google.android.material.R.attr.colorOnPrimary, "Vimeo")
            assertTrue("Vimeo primary text needs readable contrast", ColorUtils.calculateContrast(onPrimary, primary) >= 4.5)
        }
    }

    @Test
    fun androidViewIntentsResolveSupportedVimeoVideoAndChannelFormsToRouter() {
        for (url in listOf(
            "https://vimeo.com/76979871",
            "http://www.vimeo.com/76979871",
            "https://vimeo.com/76979871/abcd1234ef",
            "https://player.vimeo.com/video/76979871?h=abcd1234ef",
            "https://vimeo.com/channels/staffpicks/76979871",
            "https://vimeo.com/groups/shortfilms/videos/76979871",
            "https://vimeo.com/album/123/video/76979871",
            "https://vimeo.com/showcase/123/video/76979871",
            "https://vimeo.com/channels/staffpicks",
            "https://vimeo.com/vimeo/videos",
            "https://vimeo.com/vimeo/videos/"
        )) {
            assertTrue("Vimeo link has no Android router: $url", routerMatches(url))
        }
        for (url in listOf(
            "https://vimeo.com.evil.example/76979871",
            "https://vimeo.com/log_in",
            "https://vimeo.com/manage/videos/76979871",
            "https://player.vimeo.com/"
        )) {
            assertTrue("Unrelated URL must not claim Vimeo routing: $url", !routerMatches(url))
        }
    }

    @Test
    fun unsupportedGlobalSearchIsLabelledOpenLinkAndLocalFilteringStaysSearch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val themed = ContextThemeWrapper(context, R.style.DarkTheme_Vimeo)
            val item = Toolbar(themed).menu.add("Search")
            ServiceSearchSupport.configureAction(item, ServiceList.Vimeo.serviceId, false)
            assertEquals(context.getString(R.string.open_link), item.title.toString())
            assertNotNull(item.icon)
            ServiceSearchSupport.configureAction(item, ServiceList.Vimeo.serviceId, true)
            assertEquals(context.getString(R.string.search), item.title.toString())
            ServiceSearchSupport.configureAction(item, ServiceList.YouTube.serviceId, false)
            assertEquals(context.getString(R.string.search), item.title.toString())
        }
    }

    @Test
    fun openingAnUnlistedVideoPreservesItsHashAndTimestampInTheNavigationIntent() {
        val url = "https://player.vimeo.com/video/76979871?h=abcd1234ef#t=30s"
        val intent = NavigationHelper.getIntentByLink(context, url)
        assertEquals(ServiceList.Vimeo.serviceId, intent.getIntExtra(KEY_SERVICE_ID, -1))
        assertEquals(url, intent.getStringExtra(KEY_URL))
        @Suppress("DEPRECATION")
        assertEquals(StreamingService.LinkType.STREAM, intent.getSerializableExtra(KEY_LINK_TYPE))
    }

    private fun routerMatches(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.name == RouterActivity::class.java.name }
    }
}
