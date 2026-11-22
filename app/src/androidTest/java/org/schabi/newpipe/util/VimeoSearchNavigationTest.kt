package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.list.search.SearchFragment

class VimeoSearchNavigationTest {
    @Test
    fun unsupportedSearchOpensLinkEntryRejectsKeywordsAndKeepsAnUnlistedLinkAcrossRotation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(context, AboutActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<AboutActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                NavigationHelper.openSearchFragment(activity.supportFragmentManager, ServiceList.Vimeo.serviceId, "a keyword")
                activity.supportFragmentManager.executePendingTransactions()
                assertFalse(activity.supportFragmentManager.fragments.any { it is SearchFragment })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
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
            instrumentation.waitForIdleSync()
            scenario.recreate()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<OpenServiceLinkDialog>().single()
                val dialog = fragment.requireDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                assertEquals(UNLISTED_URL, dialog.findViewById<EditText>(R.id.dialogEditText)!!.text.toString())
                assertFalse(activity.supportFragmentManager.fragments.any { it is SearchFragment })
                fragment.dismissNow()
            }
        }
    }

    companion object {
        private const val UNLISTED_URL = "https://player.vimeo.com/video/76979871?h=abcd1234ef#t=30s"
    }
}
