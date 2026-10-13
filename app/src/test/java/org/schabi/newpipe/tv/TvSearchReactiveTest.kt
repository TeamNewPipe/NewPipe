/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv

import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvSearchReactiveTest {

    private lateinit var testScheduler: TestScheduler

    @Before
    fun setUp() {
        testScheduler = TestScheduler()
        RxJavaPlugins.setComputationSchedulerHandler { testScheduler }
        RxJavaPlugins.setIoSchedulerHandler { testScheduler }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.reset()
    }

    @Test
    fun testDebouncedSearchSubjectEmitsAfterInactivity() {
        val searchSubject = PublishSubject.create<String>()
        val emittedQueries = mutableListOf<String>()

        searchSubject
            .debounce(400, TimeUnit.MILLISECONDS, testScheduler)
            .distinctUntilChanged()
            .subscribe { query -> emittedQueries.add(query) }

        // User types 'n', 'ne', 'new', 'newpipe' rapidly within 100ms
        searchSubject.onNext("n")
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        searchSubject.onNext("ne")
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        searchSubject.onNext("new")
        testScheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        searchSubject.onNext("newpipe")

        // No queries emitted yet because 400ms hasn't passed since last character
        assertTrue(emittedQueries.isEmpty())

        // User pauses typing for 400ms
        testScheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS)

        assertEquals(1, emittedQueries.size)
        assertEquals("newpipe", emittedQueries[0])
    }

    @Test
    fun testDistinctUntilChangedPreventsDuplicateSearches() {
        val searchSubject = PublishSubject.create<String>()
        val emittedQueries = mutableListOf<String>()

        searchSubject
            .debounce(400, TimeUnit.MILLISECONDS, testScheduler)
            .distinctUntilChanged()
            .subscribe { query -> emittedQueries.add(query) }

        searchSubject.onNext("android tv")
        testScheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS)

        // Typing the exact same query again should not trigger a second search
        searchSubject.onNext("android tv")
        testScheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS)

        assertEquals(1, emittedQueries.size)
    }
}
