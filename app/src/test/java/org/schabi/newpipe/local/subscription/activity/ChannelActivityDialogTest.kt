/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.activity

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType

/** Exercises the real dialog and Room data while controlling the asynchronous query boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w400dp-h800dp")
class ChannelActivityDialogTest {
    private lateinit var controller: ActivityController<TestActivity>
    private lateinit var database: AppDatabase
    private val io = TestScheduler()
    private val asyncErrors = mutableListOf<Throwable>()
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private val profile = SubscriptionEntity.DEFAULT_PROFILE_ID
    private val channel = "https://example.com/channel"

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { io }
        RxJavaPlugins.setErrorHandler { asyncErrors += it }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        val subscription = database.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = channel, profileId = profile))
        val today = OffsetDateTime.now(ZoneOffset.UTC)
        for ((index, date) in listOf(today.minusDays(3), today.minusDays(35)).withIndex()) {
            val stream = database.streamDAO().insert(
                StreamEntity(
                    serviceId = 0,
                    url = "https://example.com/video/$index",
                    title = "Saved upload",
                    streamType = StreamType.VIDEO_STREAM,
                    duration = 60,
                    uploader = "Channel",
                    uploadDate = date
                )
            )
            database.feedDAO().insertAll(listOf(FeedEntity(stream, subscription)))
        }
        controller = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
    }

    @After
    fun tearDown() {
        try {
            if (::controller.isInitialized) controller.pause().stop().destroy()
        } finally {
            databaseField.set(null, previousDatabase)
            if (::database.isInitialized) database.close()
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun weeklyAndMonthlyBarsHaveReadableCountsAndSelectionSurvivesActivityRecreation() {
        val original = show()
        assertEquals(context.getString(R.string.channel_activity_loading), view<TextView>(original, R.id.activity_summary).text.toString())
        load()

        assertEquals(R.id.activity_by_month, view<MaterialButtonToggleGroup>(original, R.id.activity_period).checkedButtonId)
        val monthlyCount = assertReadableBars(original)
        view<MaterialButton>(original, R.id.activity_by_week).performClick()
        val weeklyCount = assertReadableBars(original)
        assertTrue(weeklyCount > monthlyCount)
        val oldWindow = original.requireDialog()

        controller.recreate()
        controller.get().supportFragmentManager.executePendingTransactions()
        val restored = controller.get().supportFragmentManager.findFragmentByTag(TAG) as ChannelActivityDialog
        assertNotSame(original, restored)
        assertFalse(oldWindow.isShowing)
        load()

        assertEquals(R.id.activity_by_week, view<MaterialButtonToggleGroup>(restored, R.id.activity_period).checkedButtonId)
        assertEquals(weeklyCount, assertReadableBars(restored))
        view<MaterialButton>(restored, R.id.activity_by_month).performClick()
        assertEquals(monthlyCount, assertReadableBars(restored))
    }

    @Test
    fun dismissalCancelsThePendingLoadAndAnUnobservedChannelHasAnExplicitEmptyState() {
        val pending = show()
        val oldSummary = view<TextView>(pending, R.id.activity_summary)
        val oldBuckets = view<LinearLayout>(pending, R.id.activity_buckets)
        pending.dismissNow()

        load()

        assertEquals(context.getString(R.string.channel_activity_loading), oldSummary.text.toString())
        assertEquals(0, oldBuckets.childCount)
        assertFalse(pending.isAdded)

        val empty = show(channel + "/unobserved")
        load()
        assertTrue(view<TextView>(empty, R.id.activity_summary).text.contains(context.getString(R.string.channel_activity_empty)))
        assertEquals(View.GONE, view<MaterialButtonToggleGroup>(empty, R.id.activity_period).visibility)
        assertEquals(0, view<LinearLayout>(empty, R.id.activity_buckets).childCount)
    }

    private fun show(channelUrl: String = channel): ChannelActivityDialog = ChannelActivityDialog().apply {
        arguments = Bundle().apply {
            putString("profile", profile)
            putInt("service", 0)
            putString("channel", channelUrl)
        }
        showNow(controller.get().supportFragmentManager, TAG)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun load() {
        io.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(asyncErrors.toString(), asyncErrors.isEmpty())
    }

    private fun assertReadableBars(dialog: ChannelActivityDialog): Int {
        val rows = view<LinearLayout>(dialog, R.id.activity_buckets).children.toList()
        val bars = rows.filterIsInstance<LinearProgressIndicator>()
        val labels = rows.filterIsInstance<TextView>()
        assertTrue(bars.isNotEmpty())
        assertEquals(bars.size, labels.size)
        assertEquals(2, bars.sumOf { it.progress })
        for ((label, bar) in labels.zip(bars)) {
            // The spoken label includes its period and count; the decorative bar is skipped.
            assertTrue(label.text.toString().endsWith(": ${NumberFormat.getIntegerInstance().format(bar.progress)}"))
            assertTrue(label.text.length > 3)
            assertTrue(label.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, bar.importantForAccessibility)
        }
        return bars.size
    }

    private fun <T : View> view(dialog: ChannelActivityDialog, id: Int): T = requireNotNull(dialog.requireDialog().findViewById<T>(id))

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
        }
    }

    companion object {
        private const val TAG = "channel_activity"
    }
}
