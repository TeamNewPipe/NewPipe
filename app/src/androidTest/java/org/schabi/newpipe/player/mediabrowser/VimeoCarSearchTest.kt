package org.schabi.newpipe.player.mediabrowser

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.session.SessionError
import androidx.media3.session.legacy.MediaConstants
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.ServiceHelper

class VimeoCarSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val serviceKey = context.getString(R.string.current_service_key)
    private val previousService = preferences.getString(serviceKey, null)

    @After
    fun restorePreferences() {
        preferences.edit().putString(serviceKey, previousService).commit()
    }

    @Test
    fun carRootReportsTheCurrentServiceCapabilityAndVimeoSearchReturnsUnsupported() {
        instrumentation.runOnMainSync {
            val browser = MediaBrowserImpl(context, Consumer { })
            try {
                ServiceHelper.setSelectedServiceId(context, ServiceList.Vimeo.serviceId)
                assertFalse(browser.createRootExtras().getBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED))
                val recent = browser.createRootExtras(true)
                assertFalse(recent.getBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED))
                assertTrue(recent.getBoolean("android.service.media.extra.RECENT"))
                val result = browser.onGetSearchResult("a keyword", 0, 20, null)
                assertTrue("Unsupported search should finish without fetching", result.isDone)
                assertEquals(SessionError.ERROR_NOT_SUPPORTED, result.get(1, TimeUnit.SECONDS).resultCode)

                ServiceHelper.setSelectedServiceId(context, ServiceList.YouTube.serviceId)
                assertTrue(browser.createRootExtras().getBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED))
            } finally {
                browser.dispose()
            }
        }
    }

    @Test
    fun vimeoVoiceSearchReportsUnsupportedWithoutStartingPlayback() {
        instrumentation.runOnMainSync {
            ServiceHelper.setSelectedServiceId(context, ServiceList.Vimeo.serviceId)
            val errors = mutableListOf<Pair<CharSequence, Int>>()
            var clearedError = false
            val preparer = MediaBrowserPlaybackPreparer(
                context,
                BiConsumer { message, code -> errors += message to code },
                Runnable { clearedError = true }
            )
            try {
                preparer.prepareFromMediaItem(
                    MediaItem.Builder().setRequestMetadata(
                        MediaItem.RequestMetadata.Builder().setSearchQuery("a keyword").build()
                    ).build()
                )
                assertEquals(1, errors.size)
                assertEquals(PlaybackException.ERROR_CODE_NOT_SUPPORTED, errors.single().second)
                assertEquals(context.getString(R.string.content_not_supported), errors.single().first.toString())
                assertFalse(clearedError)
            } finally {
                preparer.dispose()
            }
        }
    }
}
