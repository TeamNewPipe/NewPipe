package org.schabi.newpipe.util.urlfinder

import java.util.regex.Pattern

class UrlFinder {
    companion object {
        private const val PROTOCOL = "(?i:http|https)://"
        private const val WORD_BOUNDARY = "(?:\\b|$|^)"
        private const val USER_INFO = "(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@"
        private const val PORT_NUMBER = "\\:\\d{1,5}"
        private const val PATH_AND_QUERY = "[/\\?](?:(?:[a-zA-Z0-9;/\\?:@&=#~\\-\\.\\+!\\*'\\(\\),_\\$])|(?:%[a-fA-F0-9]{2}))*"
        private const val IP_ADDRESS = "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))"
        private const val IRI_LABEL = "[a-zA-Z0-9](?:[a-zA-Z0-9_\\-]{0,61}[a-zA-Z0-9]){0,1}"
        private const val RELAXED_DOMAIN_NAME = "(?:(?:" + IRI_LABEL + "(?:\\.(?=\\S))" + "+)+" + "|" + IP_ADDRESS + ")"

        private val WEB_URL_WITH_PROTOCOL = Pattern.compile(
            "(" + WORD_BOUNDARY + "(?:" + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")" + "(?:" + RELAXED_DOMAIN_NAME + ")?" + "(?:" + PORT_NUMBER + ")?" + ")" + "(?:" + PATH_AND_QUERY + ")?" + WORD_BOUNDARY + ")"
        )

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
