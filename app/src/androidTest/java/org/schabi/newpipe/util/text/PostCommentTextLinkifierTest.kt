/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.text

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.ServiceList

@RunWith(AndroidJUnit4::class)
class PostCommentTextLinkifierTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test(timeout = 20_000)
    fun postCommentsKeepTimestampsPlainWhileVideoTimestampsHashtagsAndWebLinksStayActive() {
        val postUrl = "https://www.youtube.com/post/UgkxTextLinkifier1491"
        val targets = linkedMapOf(
            postUrl to 0,
            "$postUrl?lc=comment#reply" to 0,
            "https://www.youtube.com/watch?v=BaW_jenozKc" to 1
        )
        val text = "At 1:23 #community see https://example.org/help"
        val views = mutableMapOf<String, TextView>()
        val completed = CountDownLatch(targets.size)
        val disposables = CompositeDisposable()
        try {
            instrumentation.runOnMainSync {
                targets.keys.forEach { url ->
                    val view = TextView(context)
                    views[url] = view
                    TextLinkifier.fromPlainText(view, text, ServiceList.YouTube, url, disposables) {
                        completed.countDown()
                    }
                }
            }
            assertTrue("Text linkification did not complete", completed.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                targets.forEach { (url, expectedTimestamps) ->
                    val linked = views.getValue(url).text as Spanned
                    assertEquals(url, text, linked.toString())
                    assertEquals(
                        url,
                        expectedTimestamps,
                        linked.getSpans(0, linked.length, TimestampLongPressClickableSpan::class.java).size
                    )
                    val timestampStart = text.indexOf("1:23")
                    assertEquals(
                        url,
                        expectedTimestamps,
                        linked.getSpans(timestampStart, timestampStart + 4, LongPressClickableSpan::class.java).size
                    )
                    val hashtag = linked.getSpans(0, linked.length, HashtagLongPressClickableSpan::class.java).single()
                    assertEquals("#community", linked.subSequence(linked.getSpanStart(hashtag), linked.getSpanEnd(hashtag)).toString())
                    val webLink = linked.getSpans(0, linked.length, UrlLongPressClickableSpan::class.java).single()
                    assertEquals("https://example.org/help", linked.subSequence(linked.getSpanStart(webLink), linked.getSpanEnd(webLink)).toString())
                }
            }
        } finally {
            instrumentation.runOnMainSync { disposables.dispose() }
        }
    }
}
