/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.error

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PluginChallengeErrorInfoTest {
    @Test
    fun challengeKeepsItsDedicatedActionAndDestinationWhenHandledAsAnAntiBotBlock() {
        val blocked = "https://provider.example/watch/video"
        val challenge = "https://provider.example/challenge"
        val exception: AntiBotException = ReCaptchaException(1001, "Challenge required", blocked, challenge)
        val error = ErrorInfo(exception, UserAction.REQUESTED_STREAM, blocked, 1001)
        val context = RuntimeEnvironment.getApplication()

        assertEquals(1001, exception.serviceId)
        assertEquals(blocked, exception.url)
        assertEquals(challenge, error.recaptchaUrl)
        assertEquals(context.getString(R.string.recaptcha_request_toast), error.getMessage(context).toString())
        assertFalse(error.isReportable)
    }

    @Test
    fun legacyChallengeConstructorRetainsTheOriginalUrl() {
        val url = "https://provider.example/challenge"
        val exception = ReCaptchaException("Challenge required", url)
        val error = ErrorInfo(listOf(exception), UserAction.REQUESTED_STREAM, url, 1001)

        assertEquals(-1, exception.serviceId)
        assertEquals(url, exception.url)
        assertEquals(url, error.recaptchaUrl)
    }
}
