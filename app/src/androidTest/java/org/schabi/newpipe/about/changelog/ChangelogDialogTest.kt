package org.schabi.newpipe.about.changelog

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.core.Maybe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity

class ChangelogDialogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val releasedNotice = ChangelogNotice(
        "1.49.1",
        1049001,
        """
        <section data-version="1.49.1" data-version-code="1049001">
        <h2>WizeStream 1.49.1</h2><div class="release-notes"><p>Published fixture changes</p></div>
        </section>
        """.trimIndent()
    )
    private val developmentHistory = """
        <section id="unreleased"><h2>Unreleased</h2>
        <div class="release-notes"><p>Pending development changes</p></div></section>
        ${releasedNotice.html}
    """.trimIndent()

    @Before
    fun resetSeenRelease() {
        context.getSharedPreferences("changelog", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun clearSeenRelease() {
        resetSeenRelease()
    }

    @Test
    fun controllerShowsOnResumeAndRecognizesLauncherAfterRoutedIntent() {
        val shown = CountDownLatch(1)
        val intent = Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<AboutActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentResumed(manager: androidx.fragment.app.FragmentManager, fragment: androidx.fragment.app.Fragment) {
                            if (fragment is ChangelogDialogFragment) shown.countDown()
                        }
                    },
                    false
                )
                val controller = ChangelogPromptController(activity) { Maybe.just(releasedNotice) }
                controller.maybeShow()
                assertNull(activity.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG))
                controller.onNewIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            }
            assertTrue("The changelog should appear after a normal launcher intent", shown.await(5, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG) as ChangelogDialogFragment
                (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(releasedNotice.code, ChangelogPreferences(context).lastSeenCode)
        }
    }

    @Test
    fun developmentNotesDoNotShowOrAcknowledgeARelease() {
        val loaded = CountDownLatch(1)
        var loadError: Throwable? = null
        val preferences = ChangelogPreferences(context)
        preferences.acknowledge(releasedNotice.code)
        val intent = Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_MAIN)
        ActivityScenario.launch<AboutActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val controller = ChangelogPromptController(activity) {
                    Maybe.fromCallable {
                        ChangelogCatalog.parse(developmentHistory).notice("1.49.2", preferences.lastSeenCode)
                    }.doOnEvent { _, error ->
                        loadError = error
                        loaded.countDown()
                    }
                }
                controller.maybeShow()
            }
            assertTrue("The development changelog should finish loading", loaded.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertNull(loadError)
                assertNull(activity.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG))
                assertEquals(releasedNotice.code, preferences.lastSeenCode)
            }
        }
    }

    @Test
    fun rotationKeepsDialogAndCloseAcknowledgesTheRelease() {
        val notice = releasedNotice
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                ChangelogDialogFragment.newInstance(notice).showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
            }
            scenario.recreate()
            scenario.onActivity {
                assertEquals(0, ChangelogPreferences(context).lastSeenCode)
                val fragment = it.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG) as ChangelogDialogFragment
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                assertEquals(it.getString(R.string.close), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
                assertEquals(it.getString(R.string.app_update_full_changelog), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                it.supportFragmentManager.executePendingTransactions()
                assertNull(it.supportFragmentManager.findFragmentByTag(ChangelogDialogFragment.TAG))
                assertEquals(notice.code, ChangelogPreferences(context).lastSeenCode)
            }
        }
    }

    @Test
    fun fullHistoryButtonOpensTheBundledPageAndAcknowledgesTheRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(ChangelogActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val fragment = ChangelogDialogFragment.newInstance(releasedNotice)
                    fragment.showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
                    (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                val page = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull(page)
                assertEquals(releasedNotice.code, ChangelogPreferences(context).lastSeenCode)
                instrumentation.runOnMainSync { page.finish() }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun cancelledDialogAcknowledgesButDowngradeCannotResetSeenVersion() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity {
                val fragment = ChangelogDialogFragment.newInstance(releasedNotice)
                fragment.showNow(it.supportFragmentManager, ChangelogDialogFragment.TAG)
                fragment.requireDialog().cancel()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val preferences = ChangelogPreferences(context)
            assertEquals(releasedNotice.code, preferences.lastSeenCode)
            preferences.acknowledge(1)
            assertEquals(releasedNotice.code, preferences.lastSeenCode)
        }
    }

    @Test
    fun nativeNotesKeepBulletsAndSeparateEntries() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scroll = changelogTextView(activity, "<h3>Fixes</h3><ul><li>First fix</li><li>Second fix</li></ul>", true)
                val text = (scroll.getChildAt(0) as android.widget.TextView).text.toString()
                assertTrue(text.contains("• First fix"))
                assertTrue(text.contains("• Second fix"))
                assertTrue(text.substringAfter("First fix").substringBefore("Second fix").contains('\n'))
            }
        }
    }

    @Test
    fun fullHistoryKeepsUnreleasedNotesSearchable() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scroll = changelogTextView(activity, developmentHistory, false)
                val text = (scroll.getChildAt(0) as android.widget.TextView).text
                assertTrue(text.contains("Unreleased"))
                assertTrue(text.contains("Published fixture changes"))
                assertEquals(1, ChangelogSearch.findMatches(text, "Pending development changes").size)
            }
        }
    }

    @Test
    fun fullHistoryActivitySurvivesRecreation() {
        ActivityScenario.launch<ChangelogActivity>(Intent(context, ChangelogActivity::class.java)).use { scenario ->
            scenario.recreate()
            scenario.onActivity {
                assertEquals(it.getString(R.string.changelog_title), it.title.toString())
                assertNotNull(it.findViewById<android.view.View>(R.id.changelog_content))
            }
        }
    }
}
