package org.schabi.newpipe.util.urlfinder

import java.util.regex.Pattern

class UrlFinder {
    companion object {
        private val WEB_URL_WITH_PROTOCOL = Pattern.compile(PatternsCompat.WEB_URL_WITH_PROTOCOL)

        /**
         * @return the first url found in the input, null otherwise.
         */
        @JvmStatic
        fun firstUrlFromInput(input: String?): String? {
            if (input.isNullOrEmpty()) {
                return null
            }

            val matcher = WEB_URL_WITH_PROTOCOL.matcher(input)

            if (matcher.find()) {
                return matcher.group()
            }

            return null
        }
    }
}
