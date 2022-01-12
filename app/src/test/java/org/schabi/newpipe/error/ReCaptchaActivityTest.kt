package org.schabi.newpipe.error

import org.junit.Assert.assertEquals
import org.junit.Test

class ReCaptchaActivityTest {
    private fun assertSanitized(expected: String, actual: String?) {
        assertEquals(expected, ReCaptchaActivity.sanitizeRecaptchaUrl(actual))
    }

    @Test fun `null, empty or blank url is sanitized correctly`() {
        assertSanitized(ReCaptchaActivity.YT_URL, null)
        assertSanitized(ReCaptchaActivity.YT_URL, "")
        assertSanitized(ReCaptchaActivity.YT_URL, "  \n \t ")
    }

    @Test fun `YouTube url containing pbj=1 is sanitized correctly`() {
        val sanitizedUrl = "https://m.youtube.com/results?search_query=test"
        assertSanitized(sanitizedUrl, "https://m.youtube.com/results?search_query=test")
        assertSanitized(sanitizedUrl, "https://m.youtube.com/results?search_query=test&pbj=1&pbj=1")
        assertSanitized(sanitizedUrl, "https://m.youtube.com/results?pbj=1&search_query=test")
        assertSanitized("pbj://pbj.pbj.pbj/pbj", "pbj://pbj.pbj.pbj/pbj?pbj=1")
        assertSanitized("http://www.host.com/b?p1=7&p2=9", "http://www.host.com/b?p1=7&pbj=1&p2=9")
        assertSanitized("http://www.host.com/a?pbj=0", "http://www.host.com/a?pbj=0")
    }
}
