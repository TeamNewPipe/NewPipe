/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.VimeoSettingsFragment
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager

@RunWith(AndroidJUnit4::class)
class VimeoSearchNavigationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val apiRequests = ConcurrentLinkedQueue<String>()
    private val apiRequestReceived = CountDownLatch(1)
    private lateinit var previousTokenProvider: () -> String?
    private lateinit var savedPreferences: Map<String, Any?>
    private lateinit var restoreDownloader: () -> Unit

    @Before
    fun isolateHomeSearchAndApiConfiguration() {
        val serviceKey = context.getString(R.string.current_service_key)
        val tabsKey = context.getString(R.string.saved_tabs_key)
        val searchBehaviorKey = context.getString(R.string.search_button_behavior_key)
        val searchHistoryKey = context.getString(R.string.enable_search_history_key)
        savedPreferences = listOf(serviceKey, tabsKey, searchBehaviorKey, searchHistoryKey)
            .associateWith { preferences.all[it] }
        previousTokenProvider = VimeoPublicApi.setTokenProvider { null }
        InfoCache.getInstance().clearCache()
        val previousDownloader = NewPipe.getDownloader()
        val previousLocalization = NewPipe.getPreferredLocalization()
        val previousCountry = NewPipe.getPreferredContentCountry()
        restoreDownloader = { NewPipe.init(previousDownloader, previousLocalization, previousCountry) }
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val uri = Uri.parse(request.url())
                    if (uri.host == "api.vimeo.com") apiRequests.add(request.url())
                    if (uri.host != "api.vimeo.com" || uri.path != "/videos") {
                        throw IOException("This navigation test permits only its synthetic Vimeo search response")
                    }
                    apiRequestReceived.countDown()
                    return Response(200, "OK", emptyMap(), EMPTY_SEARCH, EMPTY_SEARCH.toByteArray(), request.url())
                }

                override fun executeAsync(request: Request, callback: AsyncCallback): Nothing {
                    if (Uri.parse(request.url()).host == "api.vimeo.com") apiRequests.add(request.url())
                    throw IOException("This navigation test does not permit asynchronous network requests")
                }
            },
            previousLocalization,
            previousCountry
        )
        ServiceHelper.setSelectedServiceId(context, ServiceList.Vimeo.serviceId)
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.FEED.tab))
        preferences.edit()
            .putString(searchBehaviorKey, context.getString(R.string.search_button_behavior_current_tab_value))
            .putBoolean(searchHistoryKey, false)
            .commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun restoreHomeSearchAndApiConfiguration() {
        VimeoPublicApi.setTokenProvider(previousTokenProvider)
        InfoCache.getInstance().clearCache()
        restoreDownloader()
        preferences.edit().apply {
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
    }

    @Test(timeout = 60_000)
    fun whatsNewOffersVimeoSearchBeforeSetupAndCancelPreservesTheLocalQuery() {
        launchHome().use { scenario ->
            try {
                openContextualSearch(scenario)
                onMainActivity(scenario) { activity ->
                    assertSearchAction(activity)
                    activity.findViewById<EditText>(R.id.contextual_search_edit_text).setText(QUERY)
                    assertSearchAction(activity).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    val setup = setupDialog(activity)
                    val dialog = setup.requireDialog() as AlertDialog
                    assertTrue(dialog.isShowing)
                    assertEquals(context.getString(R.string.vimeo_search_setup_message), dialog.findViewById<TextView>(android.R.id.message)!!.text.toString())
                    assertFalse(activity.supportFragmentManager.fragments.any { it is SearchFragment || it is OpenServiceLinkDialog })
                    assertTrue("Local search and its setup prompt must not request Vimeo results", apiRequests.isEmpty())
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                }
                waitForActivity(scenario, "Cancel did not dismiss Vimeo setup") { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    activity.supportFragmentManager.fragments.none { it is VimeoSearchSetupDialog }
                }
                onMainActivity(scenario) { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    assertFalse(activity.supportFragmentManager.fragments.any { it is VimeoSearchSetupDialog })
                    assertEquals(QUERY, activity.findViewById<EditText>(R.id.contextual_search_edit_text).text.toString())
                    assertSearchAction(activity)
                }
                assertTrue(apiRequests.isEmpty())
            } finally {
                onMainActivity(scenario) { it.finish() }
            }
        }
    }

    @Test(timeout = 60_000)
    fun setupSurvivesRotationOpensVimeoSettingsAndResumesTheOriginalQuery() {
        launchHome().use { scenario ->
            var settings: SettingsActivity? = null
            val monitor = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)
            try {
                openContextualSearch(scenario)
                onMainActivity(scenario) { activity ->
                    activity.findViewById<EditText>(R.id.contextual_search_edit_text).setText(QUERY)
                    assertSearchAction(activity).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    assertTrue(setupDialog(activity).requireDialog().isShowing)
                }
                scenario.recreate()
                waitForActivity(scenario, "The setup dialog and local query did not restore") { activity ->
                    activity.supportFragmentManager.fragments.filterIsInstance<VimeoSearchSetupDialog>().singleOrNull()?.dialog?.isShowing == true &&
                        activity.findViewById<EditText>(R.id.contextual_search_edit_text)?.text?.toString() == QUERY
                }
                onMainActivity(scenario) { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    val dialog = setupDialog(activity).requireDialog() as AlertDialog
                    assertTrue(dialog.isShowing)
                    assertEquals(QUERY, activity.findViewById<EditText>(R.id.contextual_search_edit_text).text.toString())
                    assertTrue(apiRequests.isEmpty())
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                settings = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? SettingsActivity
                assertNotNull("The setup action must open SettingsActivity", settings)
                instrumentation.runOnMainSync {
                    val screen = requireNotNull(settings)
                    screen.supportFragmentManager.executePendingTransactions()
                    assertTrue(screen.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) is VimeoSettingsFragment)
                    assertTrue(apiRequests.isEmpty())
                    VimeoPublicApi.setTokenProvider { TEST_TOKEN }
                    screen.finish()
                }
                assertTrue("Returning from Vimeo setup must submit the retained query", apiRequestReceived.await(10, TimeUnit.SECONDS))
                onMainActivity(scenario) { activity ->
                    activity.supportFragmentManager.executePendingTransactions()
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is SearchFragment)
                    assertFalse(activity.supportFragmentManager.fragments.any { it is VimeoSearchSetupDialog || it is OpenServiceLinkDialog })
                    assertEquals(QUERY, activity.findViewById<EditText>(R.id.toolbar_search_edit_text).text.toString())
                }
                assertTrue(apiRequests.isNotEmpty())
                apiRequests.forEach { url -> assertEquals(QUERY, Uri.parse(url).getQueryParameter("query")) }
            } finally {
                settings?.let { screen -> instrumentation.runOnMainSync { if (!screen.isFinishing) screen.finish() } }
                instrumentation.removeMonitor(monitor)
                onMainActivity(scenario) { it.finish() }
            }
        }
    }

    @Test(timeout = 30_000)
    fun setupKeepsTokenFreeLinkEntryAndUnlistedLinksAcrossRotation() {
        val intent = Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<AboutActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                NavigationHelper.openSearchFragment(activity.supportFragmentManager, ServiceList.Vimeo.serviceId, "a keyword")
                activity.supportFragmentManager.executePendingTransactions()
                val setup = activity.supportFragmentManager.fragments.filterIsInstance<VimeoSearchSetupDialog>().single()
                (setup.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<OpenServiceLinkDialog>().single()
                val dialog = fragment.requireDialog() as AlertDialog
                val input = dialog.findViewById<EditText>(R.id.dialogEditText)!!
                assertEquals("", input.text.toString())
                input.setText("a keyword")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(dialog.isShowing)
                assertEquals(context.getString(R.string.unsupported_url), input.error.toString())
                input.setText(UNLISTED_URL)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<OpenServiceLinkDialog>().single()
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                assertEquals(UNLISTED_URL, dialog.findViewById<EditText>(R.id.dialogEditText)!!.text.toString())
                assertFalse(activity.supportFragmentManager.fragments.any { it is SearchFragment })
                assertTrue("Public link entry must remain token-free", apiRequests.isEmpty())
                fragment.dismissNow()
            }
        }
    }

    private fun launchHome(): ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))

    private fun openContextualSearch(scenario: ActivityScenario<MainActivity>) {
        waitForActivity(scenario, "The Home feed search action did not become available") { activity ->
            activity.supportFragmentManager.executePendingTransactions()
            val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
            main?.childFragmentManager?.executePendingTransactions()
            val item = activity.findViewById<Toolbar>(R.id.toolbar)?.menu?.findItem(R.id.action_search)
            main != null && main.childFragmentManager.fragments.any { it.view != null && it.isResumed } &&
                item != null && main.onOptionsItemSelected(item)
        }
    }

    private fun waitForActivity(scenario: ActivityScenario<MainActivity>, message: String, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            onMainActivity(scenario) { ready = predicate(it) }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue(message, ready)
    }

    private fun assertSearchAction(activity: MainActivity): ExtendedFloatingActionButton = activity.findViewById<ExtendedFloatingActionButton>(R.id.contextual_global_search_fab).also { action ->
        assertTrue("Vimeo search must remain visible before API setup", action.isShown)
        assertEquals(context.getString(R.string.search_with_service_name, "Vimeo"), action.text.toString())
    }

    private fun setupDialog(activity: MainActivity): VimeoSearchSetupDialog = activity.supportFragmentManager.fragments.filterIsInstance<VimeoSearchSetupDialog>().single()

    // Avoid an unbounded idle wait while old Android versions animate a fragment transition.
    private fun onMainActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity { action(it) }
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("Activity callback did not complete", completed.await(10, TimeUnit.SECONDS))
        failure?.let { throw it }
    }

    companion object {
        private const val QUERY = "Lebanon"
        private const val TEST_TOKEN = "test-public-token"
        private const val UNLISTED_URL = "https://player.vimeo.com/video/76979871?h=abcd1234ef#t=30s"
        private const val EMPTY_SEARCH = """{"total":0,"page":1,"per_page":25,"paging":{"next":null,"previous":null,"first":null,"last":null},"data":[]}"""
    }
}
