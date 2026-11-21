/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.error

import android.app.Application
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ServiceRestrictionMessagesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun rateLimitIsActionableNotAParsingErrorOrReportableBug() {
        val throwable = IOException("Response code: 429 Too Many Requests")
        val error = ErrorInfo(
            RuntimeException("network", throwable),
            UserAction.SEARCHED,
            "science",
            ServiceList.YouTube.serviceId
        )
        assertEquals(
            context.getString(R.string.service_rate_limited_advice, "YouTube"),
            error.getMessage(context).toString().trimEnd()
        )
        assertFalse(error.isReportable)
        assertTrue(error.isRetryable)
    }

    @Test
    fun accessDenialExplainsFailureWithoutAssumingTheReason() {
        val error = ErrorInfo(
            IOException("HTTP 403"),
            UserAction.REQUESTED_STREAM,
            "a video",
            ServiceList.YouTube.serviceId
        )
        assertEquals(
            context.getString(R.string.service_access_denied_advice, "YouTube"),
            error.getMessage(context).toString().trimEnd()
        )
    }

    @Test
    fun nonHttpCodesInTitlesAndUnrelatedExceptionsAreNotClassified() {
        assertNull(ServiceRestrictionMessages.kind(IOException("video_429_watch")))
        assertNull(ServiceRestrictionMessages.kind(IOException("403.mp4")))
        assertNull(ServiceRestrictionMessages.kind(IOException("Service unavailable")))
        assertEquals(
            ServiceRestrictionMessages.Kind.RATE_LIMIT,
            ServiceRestrictionMessages.kind(
                IllegalStateException("wrapper", IOException("HTTP status 429"))
            )
        )
    }

    @Test
    fun cyclicCausesDoNotLoopForever() {
        val one = IllegalStateException("first")
        val two = IllegalArgumentException("second", one)
        one.initCause(two)
        assertNull(ServiceRestrictionMessages.kind(one))
    }
}
