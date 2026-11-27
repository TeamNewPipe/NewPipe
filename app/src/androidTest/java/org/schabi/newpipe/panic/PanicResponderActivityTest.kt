/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import info.guardianproject.panic.Panic
import info.guardianproject.panic.PanicResponder
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.newpipe.app.preferences.PanicPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.testUtil.TestDatabase

class PanicResponderActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
    private lateinit var preferences: SharedPreferences
    private lateinit var savedPreferences: Map<String, *>
    private lateinit var database: AppDatabase
    private var previousDatabase: AppDatabase? = null

    @Before
    fun setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        savedPreferences = preferences.all.filterKeys { it in preferenceKeys }
        val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance")
        databaseField.isAccessible = true
        previousDatabase = databaseField.get(NewPipeDatabase) as AppDatabase?
        database = TestDatabase.createReplacingNewPipeDatabase()

        preferences.edit()
            .putString(PanicResponder.PREF_TRIGGER_PACKAGE_NAME, testPackage)
            .putBoolean(PanicPreferences.KEY_EXIT_APP, false)
            .putBoolean(PanicPreferences.KEY_REMOVE_FROM_RECENT_APPS, false)
            .putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, false)
            .putBoolean(PanicPreferences.KEY_CLEAR_SEARCH_HISTORY, false)
            .putBoolean(PanicPreferences.KEY_CLEAR_WATCH_HISTORY, false)
            .commit()

        val time = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        database.searchHistoryDAO().insertAll(
            listOf(SearchHistoryEntry(creationDate = time, serviceId = 0, search = "test"))
        )
        database.streamDAO().insertAll(
            listOf(
                StreamEntity(
                    uid = 1,
                    serviceId = 0,
                    url = "https://example.com/stream",
                    title = "test",
                    streamType = StreamType.VIDEO_STREAM,
                    duration = 1,
                    uploader = "test"
                )
            )
        )
        database.streamHistoryDAO().insertAll(listOf(StreamHistoryEntity(1, time, 1)))
        assertCounts(1, 1, 1)
    }

    @After
    fun tearDown() {
        val editor = preferences.edit()
        preferenceKeys.forEach { key ->
            when (val value = savedPreferences[key]) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                null -> editor.remove(key)
                else -> error("Unexpected preference value for $key")
            }
        }
        editor.commit()
        val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance")
        databaseField.isAccessible = true
        databaseField.set(NewPipeDatabase, previousDatabase)
        database.close()
    }

    @Test
    fun registeredTriggerClearsEntireDatabase() {
        preferences.edit().putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, true).commit()
        trigger()
        awaitCounts(0, 0, 0)
    }

    @Test
    fun databaseDeletionTakesPrecedenceOverHistorySettings() {
        preferences.edit()
            .putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, true)
            .putBoolean(PanicPreferences.KEY_CLEAR_SEARCH_HISTORY, true)
            .putBoolean(PanicPreferences.KEY_CLEAR_WATCH_HISTORY, true)
            .commit()
        trigger()
        awaitCounts(0, 0, 0)
    }

    @Test
    fun registeredTriggerClearsOnlySearchHistory() {
        preferences.edit().putBoolean(PanicPreferences.KEY_CLEAR_SEARCH_HISTORY, true).commit()
        trigger()
        awaitCounts(0, 1, 1)
    }

    @Test
    fun registeredTriggerClearsOnlyWatchHistory() {
        preferences.edit().putBoolean(PanicPreferences.KEY_CLEAR_WATCH_HISTORY, true).commit()
        trigger()
        awaitCounts(1, 0, 1)
    }

    @Test
    fun registeredTriggerClearsBothHistories() {
        preferences.edit()
            .putBoolean(PanicPreferences.KEY_CLEAR_SEARCH_HISTORY, true)
            .putBoolean(PanicPreferences.KEY_CLEAR_WATCH_HISTORY, true)
            .commit()
        trigger()
        awaitCounts(0, 0, 1)
    }

    @Test
    fun registeredTriggerWithoutDeletionSettingsKeepsData() {
        trigger()
        assertDataRemains()
    }

    @Test
    fun unregisteredAppCannotDeleteData() {
        preferences.edit()
            .putString(PanicResponder.PREF_TRIGGER_PACKAGE_NAME, "another.registered.app")
            .putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, true)
            .commit()
        trigger()
        assertDataRemains()
    }

    @Test
    fun triggerWithoutRegisteredAppCannotDeleteData() {
        preferences.edit()
            .remove(PanicResponder.PREF_TRIGGER_PACKAGE_NAME)
            .putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, true)
            .commit()
        trigger()
        assertDataRemains()
    }

    @Test
    fun registeredAppCannotDeleteDataWithAnotherAction() {
        preferences.edit().putBoolean(PanicPreferences.KEY_CLEAR_APP_DATABASE, true).commit()
        trigger("org.schabi.newpipe.panic.test.NOT_A_TRIGGER")
        assertDataRemains()
    }

    private fun trigger(action: String = Panic.ACTION_TRIGGER) {
        val finished = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                finished.countDown()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PanicTriggerTestActivity.ACTION_FINISHED),
            ContextCompat.RECEIVER_EXPORTED
        )
        try {
            context.startActivity(
                Intent().setClassName(testPackage, PanicTriggerTestActivity::class.java.name)
                    .putExtra(PanicTriggerTestActivity.EXTRA_TARGET_PACKAGE, context.packageName)
                    .putExtra(PanicTriggerTestActivity.EXTRA_ACTION, action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            assertTrue("Trigger activity did not return", finished.await(10, TimeUnit.SECONDS))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Wait at max 5 seconds for the database to apply the expected values.
     */
    @SuppressLint("CheckResult")
    private fun awaitCounts(search: Int, watch: Int, streams: Int) {
        database.searchHistoryDAO().getAll().filter { it.size == search }
            .timeout(5, TimeUnit.SECONDS).blockingFirst()
        database.streamHistoryDAO().getAll().filter { it.size == watch }
            .timeout(5, TimeUnit.SECONDS).blockingFirst()
        database.streamDAO().getAll().filter { it.size == streams }
            .timeout(5, TimeUnit.SECONDS).blockingFirst()
        assertCounts(search, watch, streams)
    }

    /**
     * Wait for a second to check if unexpected changes to the database occur.
     */
    private fun assertDataRemains() {
        val changedSearchHistory = CountDownLatch(1)
        val changedWatchHistory = CountDownLatch(1)
        val changedStreams = CountDownLatch(1)
        val searchSubscription = database.searchHistoryDAO().getAll()
            .filter { it.size != 1 }
            .subscribe { changedSearchHistory.countDown() }
        val watchSubscription = database.streamHistoryDAO().getAll()
            .filter { it.size != 1 }
            .subscribe { changedWatchHistory.countDown() }
        val streamsSubscription = database.streamDAO().getAll()
            .filter { it.size != 1 }
            .subscribe { changedStreams.countDown() }
        try {
            assertFalse("Unexpected deletion from SearchHistoryDAO", changedSearchHistory.await(1, TimeUnit.SECONDS))
            assertFalse("Unexpected deletion from StreamHistoryDAO", changedWatchHistory.await(1, TimeUnit.SECONDS))
            assertFalse("Unexpected deletion from StreamDAO", changedStreams.await(1, TimeUnit.SECONDS))
            assertCounts(1, 1, 1)
        } finally {
            searchSubscription.dispose()
            watchSubscription.dispose()
            streamsSubscription.dispose()
        }
    }

    private fun assertCounts(search: Int, watch: Int, streams: Int) {
        assertEquals(search, database.searchHistoryDAO().getAll().blockingFirst().size)
        assertEquals(watch, database.streamHistoryDAO().getAll().blockingFirst().size)
        assertEquals(streams, database.streamDAO().getAll().blockingFirst().size)
    }

    companion object {
        private val preferenceKeys = setOf(
            PanicResponder.PREF_TRIGGER_PACKAGE_NAME,
            PanicPreferences.KEY_EXIT_APP,
            PanicPreferences.KEY_REMOVE_FROM_RECENT_APPS,
            PanicPreferences.KEY_CLEAR_APP_DATABASE,
            PanicPreferences.KEY_CLEAR_SEARCH_HISTORY,
            PanicPreferences.KEY_CLEAR_WATCH_HISTORY
        )
    }
}
