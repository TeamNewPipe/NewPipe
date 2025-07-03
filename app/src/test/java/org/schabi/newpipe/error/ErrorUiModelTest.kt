package org.schabi.newpipe.error

import android.net.http.NetworkException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.UiModel.GenericErrorUiModel
import org.schabi.newpipe.ui.UiModel.UnableToLoadCommentsUiModel

class ErrorUiModelTest {

    /**
     * Test that when comments fail to load, the correct error panel is rendered
     */
    @Test
    fun `mapThrowableToErrorUiModel with REQUESTED_COMMENTS returns UnableToLoadCommentsUiModel`() {
        // val throwable = RuntimeException("Comments failed to load")
        val networkException = object : NetworkException("Connection attempt timed out", null) {
            override fun getErrorCode() = NetworkException.ERROR_CONNECTION_TIMED_OUT
            override fun isImmediatelyRetryable() = true
        }
        val result = mapThrowableToErrorUiModel(networkException, UserAction.REQUESTED_COMMENTS)
        assertTrue("Result should be UnableToLoadCommentsUiModel", result is UnableToLoadCommentsUiModel)
        assertEquals("Raw error should be preserved for debugging", networkException, result.rawError)
    }

    /**
     * Test the fallback logic
     */
    @Test
    fun `mapThrowableToErrorUiModel with null UserAction returns GenericErrorUiModel`() {
        val throwable = RuntimeException("Test error")
        val result = mapThrowableToErrorUiModel(throwable, null)

        assertTrue("Should return GenericErrorUiModel", result is GenericErrorUiModel)
        assertEquals("Should preserve the original throwable", throwable, result.rawError)
    }

    /**
     * Test that UnableToLoadCommentsUiModel maps to the correct error panel configuration
     */
    @Test
    fun `UnableToLoadCommentsUiModel has correct ErrorPanelSpec`() {
        val throwable = RuntimeException("Test error")
        val errorModel = UnableToLoadCommentsUiModel(throwable)
        val spec = errorModel.spec
        // Assert: Verify the spec has the correct configuration for comment loading errors
        assertEquals(
            "Error message should be 'Unable to load comments'",
            R.string.error_unable_to_load_comments,
            spec.messageRes,
        )

        assertTrue("Retry button should be shown for comment loading errors", spec.showRetry)
        assertTrue("Report button should be shown for comment loading errors", spec.showReport)
        assertFalse("Open in browser should NOT be shown for comment loading errors", spec.showOpenInBrowser)

        // Assert: Verify the raw error is set correctly
        assertEquals("Raw error should be preserved for debugging", throwable, errorModel.rawError)
    }
}
