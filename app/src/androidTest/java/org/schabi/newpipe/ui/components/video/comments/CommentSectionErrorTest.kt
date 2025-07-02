package org.schabi.newpipe.ui.components.video.comments

import android.net.http.NetworkException
import androidx.paging.LoadState
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.UiModel.UnableToLoadCommentsUiModel
import org.schabi.newpipe.viewmodels.util.Resource

@RunWith(AndroidJUnit4::class)
class CommentSectionErrorTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    /**
     * Test Resource.Error state - when initial comment info loading fails
     */
    @Test
    fun testResourceErrorState_ShowsUnableToLoadCommentsUiModel() {

        val networkException = object : NetworkException("Connection attempt timed out", null) {
            override fun getErrorCode(): Int = NetworkException.ERROR_CONNECTION_TIMED_OUT
            override fun isImmediatelyRetryable() = true
        }
        val errorResource = Resource.Error(networkException)
        assertEquals("Should contain the network exception", networkException, errorResource.throwable)

        val errorModel = UnableToLoadCommentsUiModel(networkException)
        val spec = errorModel.spec

        assertEquals("Should have correct message resource", R.string.error_unable_to_load_comments, spec.messageRes)
        assertTrue("Should show retry button", spec.showRetry)
        assertTrue("Should show report button", spec.showReport)
        assertFalse("Should NOT show open in browser button", spec.showOpenInBrowser)
    }

    /**
     *  Test LoadState.Error state - when paging data loading fails
     */
    @Test
    fun testLoadStateErrorState_ShowsUnableToLoadCommentsUiModel() {
        val pagingException = RuntimeException("Paging data loading failed")
        val loadStateError = LoadState.Error(pagingException)

        assertEquals("Should contain the paging exception", pagingException, loadStateError.error)

        val errorModel = UnableToLoadCommentsUiModel(pagingException)
        val spec = errorModel.spec

        assertEquals("Should have correct message resource", R.string.error_unable_to_load_comments, spec.messageRes)
        assertTrue("Should show retry button", spec.showRetry)
        assertTrue("Should show report button", spec.showReport)
        assertFalse("Should NOT show open in browser button", spec.showOpenInBrowser)
    }
}
