/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.text.similarity

import org.apache.commons.text.similarity.FuzzyScore
import java.util.Locale

/**
 * A matching algorithm that is similar to the searching algorithms implemented in editors such
 * as Sublime Text, TextMate, Atom and others.
 *
 *
 *
 * One point is given for every matched character. Subsequent matches yield two bonus points.
 * A higher score indicates a higher similarity.
 *
 *
 *
 *
 * This code has been adapted from Apache Commons Lang 3.3.
 *
 *
 * @since 1.0
 *
 * Note: This class was forked from
 * [
 * apache/commons-text (8cfdafc) FuzzyScore.java
](https://git.io/JyYJg) *
 */
class FuzzyScore(locale: Locale?) {
    /**
     * Gets the locale.
     *
     * @return The locale
     */
    /**
     * Locale used to change the case of text.
     */
    val locale: Locale

    /**
     * This returns a [Locale]-specific [FuzzyScore].
     *
     * @param locale The string matching logic is case insensitive.
     * A [Locale] is necessary to normalize both Strings to lower case.
     * @throws IllegalArgumentException
     * This is thrown if the [Locale] parameter is `null`.
     */
    init {
        requireNotNull(locale) { "Locale must not be null" }
        this.locale = locale
    }

    /**
     * Find the Fuzzy Score which indicates the similarity score between two
     * Strings.
     *
     * <pre>
     * score.fuzzyScore(null, null)                          = IllegalArgumentException
     * score.fuzzyScore("not null", null)                    = IllegalArgumentException
     * score.fuzzyScore(null, "not null")                    = IllegalArgumentException
     * score.fuzzyScore("", "")                              = 0
     * score.fuzzyScore("Workshop", "b")                     = 0
     * score.fuzzyScore("Room", "o")                         = 1
     * score.fuzzyScore("Workshop", "w")                     = 1
     * score.fuzzyScore("Workshop", "ws")                    = 2
     * score.fuzzyScore("Workshop", "wo")                    = 4
     * score.fuzzyScore("Apache Software Foundation", "asf") = 3
    </pre> *
     *
     * @param term a full term that should be matched against, must not be null
     * @param query the query that will be matched against a term, must not be
     * null
     * @return result score
     * @throws IllegalArgumentException if the term or query is `null`
     */
    fun fuzzyScore(term: CharSequence?, query: CharSequence?): Int {
        require(!(term == null || query == null)) { "CharSequences must not be null" }

        // fuzzy logic is case insensitive. We normalize the Strings to lower
        // case right from the start. Turning characters to lower case
        // via Character.toLowerCase(char) is unfortunately insufficient
        // as it does not accept a locale.
        val termLowerCase = term.toString().lowercase(locale)
        val queryLowerCase = query.toString().lowercase(locale)

        // the resulting score
        var score = 0

        // the position in the term which will be scanned next for potential
        // query character matches
        var termIndex = 0

        // index of the previously matched character in the term
        var previousMatchingCharacterIndex = Int.MIN_VALUE
        for (queryIndex in 0 until queryLowerCase.length) {
            val queryChar = queryLowerCase[queryIndex]
            var termCharacterMatchFound = false
            while (termIndex < termLowerCase.length
                    && !termCharacterMatchFound) {
                val termChar = termLowerCase[termIndex]
                if (queryChar == termChar) {
                    // simple character matches result in one point
                    score++

                    // subsequent character matches further improve
                    // the score.
                    if (previousMatchingCharacterIndex + 1 == termIndex) {
                        score += 2
                    }
                    previousMatchingCharacterIndex = termIndex

                    // we can leave the nested loop. Every character in the
                    // query can match at most one character in the term.
                    termCharacterMatchFound = true
                }
                termIndex++
            }
        }
        return score
    }
}
