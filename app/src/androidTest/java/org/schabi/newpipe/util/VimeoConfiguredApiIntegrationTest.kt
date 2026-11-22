/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.vimeo.VimeoCommentsExtractor
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.extractor.services.vimeo.VimeoSearchExtractor
import org.schabi.newpipe.fragments.detail.TabAdapter
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment

@RunWith(AndroidJUnit4::class)
class VimeoConfiguredApiIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var previousTokenProvider: () -> String?

    @Before
    fun isolateTokenConfiguration() {
        previousTokenProvider = VimeoPublicApi.setTokenProvider { null }
    }

    @After
    fun restoreTokenConfiguration() {
        VimeoPublicApi.setTokenProvider(previousTokenProvider)
    }

    @Test
    fun configuringAndRemovingTokenChangesCapabilitiesWithoutHidingTheSearchAction() {
        val service = ServiceList.Vimeo
        assertFalse(ServiceSearchSupport.supportsSearch(service.serviceId))
        assertTrue(ServiceSearchSupport.canOpenSearch(service.serviceId))
        assertNull(service.commentsLHFactory)
        assertFalse(service.serviceInfo.mediaCapabilities.contains(MediaCapability.COMMENTS))

        VimeoPublicApi.setTokenProvider { TEST_TOKEN }
        assertTrue(ServiceSearchSupport.supportsSearch(service.serviceId))
        assertNotNull(service.searchQHFactory)
        assertNotNull(service.commentsLHFactory)
        assertTrue(service.serviceInfo.mediaCapabilities.contains(MediaCapability.COMMENTS))
        assertTrue(service.getSearchExtractor("public film") is VimeoSearchExtractor)
        assertTrue(service.getCommentsExtractor(VIDEO_URL) is VimeoCommentsExtractor)
        assertTrue(VimeoCommentsSupport.supportsUrl(service.serviceId, VIDEO_URL))
        assertTrue(VimeoCommentsSupport.supportsUrl(service.serviceId, "https://vimeo.com/76979871/abcd1234ef"))
        assertFalse(VimeoCommentsSupport.supportsUrl(service.serviceId, "https://vimeo.com/event/4768062"))
        assertFalse(VimeoCommentsSupport.supportsUrl(service.serviceId, "https://vimeo.com/event/4768062/embed"))
        assertFalse(VimeoCommentsSupport.supportsUrl(service.serviceId, null))
        assertTrue(VimeoCommentsSupport.supportsUrl(ServiceList.YouTube.serviceId, "https://www.youtube.com/watch?v=test"))

        instrumentation.runOnMainSync {
            val item = Toolbar(ContextThemeWrapper(context, R.style.DarkTheme_Vimeo)).menu.add("Search")
            ServiceSearchSupport.configureAction(item, service.serviceId, false)
            assertEquals(context.getString(R.string.search), item.title.toString())
            VimeoPublicApi.setTokenProvider { null }
            ServiceSearchSupport.configureAction(item, service.serviceId, false)
            assertEquals(context.getString(R.string.search), item.title.toString())
        }
        assertNull(service.searchQHFactory)
        assertTrue(ServiceSearchSupport.canOpenSearch(service.serviceId))
        assertNull(service.commentsLHFactory)
        assertFalse(service.serviceInfo.mediaCapabilities.contains(MediaCapability.COMMENTS))
        assertFalse(VimeoCommentsSupport.supportsUrl(service.serviceId, VIDEO_URL))
    }

    @Test
    fun existingDetailTabsDetectTokenChangesAfterPauseAndLeaveStableOrDisabledTabsAlone() {
        val serviceId = ServiceList.Vimeo.serviceId
        lateinit var adapter: TabAdapter
        val description = Fragment()
        val intent = Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<AboutActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                adapter = TabAdapter(activity.supportFragmentManager).apply {
                    addFragment(description, "DESCRIPTION TAB")
                }
                assertFalse(VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, VIDEO_URL)))
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            VimeoPublicApi.setTokenProvider { TEST_TOKEN }
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity {
                assertTrue("Returning from setup must add the newly available comments tab", VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, VIDEO_URL)))
                val comments = CommentsFragment.getInstance(serviceId, VIDEO_URL, "Vimeo fixture")
                adapter.addFragment(comments, COMMENTS_TAG)
                assertFalse("An unchanged capability must keep the existing comments fragment", VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, VIDEO_URL)))
                assertSame(comments, adapter.getItem(adapter.getItemPositionByTitle(COMMENTS_TAG)))
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            VimeoPublicApi.setTokenProvider { null }
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity {
                assertTrue("Returning after token removal must remove the existing comments tab", VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, VIDEO_URL)))
                adapter.removeItemByTitle(COMMENTS_TAG)
                assertFalse(VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, VIDEO_URL)))
                assertSame(description, adapter.getItem(0))

                VimeoPublicApi.setTokenProvider { TEST_TOKEN }
                assertFalse("The show-comments preference must still be respected", VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, false))
                assertFalse("Events must not acquire a video-comments tab", VimeoCommentsSupport.shouldRefreshTabs(serviceId, adapter, COMMENTS_TAG, VimeoCommentsSupport.supportsUrl(serviceId, "https://vimeo.com/event/4768062")))
                assertFalse(VimeoCommentsSupport.shouldRefreshTabs(ServiceList.YouTube.serviceId, adapter, COMMENTS_TAG, true))
                assertFalse(VimeoCommentsSupport.shouldRefreshTabs(serviceId, null, COMMENTS_TAG, true))
            }
        }
    }

    @Test(timeout = 30_000)
    fun configuredEmptySearchOpensSearchFragmentWithoutLinkDialogOrNetworkRequest() {
        VimeoPublicApi.setTokenProvider { TEST_TOKEN }
        val previousDownloader = NewPipe.getDownloader()
        val previousLocalization = NewPipe.getPreferredLocalization()
        val previousCountry = NewPipe.getPreferredContentCountry()
        val requests = AtomicInteger()
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    requests.incrementAndGet()
                    throw IOException("This navigation test does not permit network requests")
                }

                override fun executeAsync(request: Request, callback: AsyncCallback): Nothing {
                    requests.incrementAndGet()
                    throw IOException("This navigation test does not permit network requests")
                }
            },
            previousLocalization,
            previousCountry
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(TvRemoteKeyDispatcher.EXTRA_DESTINATION, TvRemoteAction.BOOKMARKS.id)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                try {
                    onMainActivity(scenario) { activity ->
                        activity.supportFragmentManager.executePendingTransactions()
                        assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is BookmarkFragment)
                        NavigationHelper.openSearchFragment(activity.supportFragmentManager, ServiceList.Vimeo.serviceId, "")
                        activity.supportFragmentManager.executePendingTransactions()
                        assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is SearchFragment)
                        assertFalse(activity.supportFragmentManager.fragments.any { it is OpenServiceLinkDialog })
                        assertEquals("", activity.findViewById<EditText>(R.id.toolbar_search_edit_text).text.toString())
                    }
                } finally {
                    onMainActivity(scenario) { it.finish() }
                }
            }
            assertEquals("Opening an empty Vimeo search must not fetch API results or suggestions", 0, requests.get())
        } finally {
            NewPipe.init(previousDownloader, previousLocalization, previousCountry)
        }
    }

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
        private const val TEST_TOKEN = "test-public-token"
        private const val VIDEO_URL = "https://vimeo.com/76979871"
        private const val COMMENTS_TAG = "COMMENTS"
    }
}
