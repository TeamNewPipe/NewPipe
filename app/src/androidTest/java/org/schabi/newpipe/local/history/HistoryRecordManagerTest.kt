package org.schabi.newpipe.local.history

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class HistoryRecordManagerTest {

    private lateinit var manager: HistoryRecordManager
    private lateinit var database: AppDatabase

    @get:Rule
    val trampolineScheduler = TrampolineSchedulerRule()

    @get:Rule
    val timeout = Timeout(1, TimeUnit.SECONDS)

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = HistoryRecordManager(ApplicationProvider.getApplicationContext())
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun onSearched() {
        manager.onSearched(0, "Hello").test().await().assertValue(1)

        // For some reason the Flowable returned by getAll() never completes, so we can't assert
        // that the number of Lists it returns is exactly 1, we can only check if the first List is
        // correct. Why on earth has a Flowable been used instead of a Single for getAll()?!?
        val entities = database.searchHistoryDAO().all.blockingFirst()
        assertEquals(1, entities.size)
        assertEquals(1, entities[0].id)
        assertEquals(0, entities[0].serviceId)
        assertEquals("Hello", entities[0].search)
    }

    @Test
    fun deleteSearchHistory() {
        val entries = listOf(
            SearchHistoryEntry(OffsetDateTime.now(), 0, "A"),
            SearchHistoryEntry(OffsetDateTime.now(), 2, "A"),
            SearchHistoryEntry(OffsetDateTime.now(), 1, "B"),
            SearchHistoryEntry(OffsetDateTime.now(), 0, "B"),
        )

        // make sure all 4 were inserted
        database.searchHistoryDAO().insertAll(entries)
        assertEquals(entries.size, database.searchHistoryDAO().all.blockingFirst().size)

        // try to delete only "A" entries, "B" entries should be untouched
        manager.deleteSearchHistory("A").test().await().assertValue(2)
        val entities = database.searchHistoryDAO().all.blockingFirst()
        assertEquals(2, entities.size)
        assertTrue(entries[2].hasEqualValues(entities[0]))
        assertTrue(entries[3].hasEqualValues(entities[1]))

        // assert that nothing happens if we delete a search query that does exist in the db
        manager.deleteSearchHistory("A").test().await().assertValue(0)
        val entities2 = database.searchHistoryDAO().all.blockingFirst()
        assertEquals(2, entities2.size)
        assertTrue(entries[2].hasEqualValues(entities2[0]))
        assertTrue(entries[3].hasEqualValues(entities2[1]))

        // delete all remaining entries
        manager.deleteSearchHistory("B").test().await().assertValue(2)
        assertEquals(0, database.searchHistoryDAO().all.blockingFirst().size)
    }

    @Test
    fun deleteCompleteSearchHistory() {
        val entries = listOf(
            SearchHistoryEntry(OffsetDateTime.now(), 1, "A"),
            SearchHistoryEntry(OffsetDateTime.now(), 2, "B"),
            SearchHistoryEntry(OffsetDateTime.now(), 0, "C"),
        )

        // make sure all 3 were inserted
        database.searchHistoryDAO().insertAll(entries)
        assertEquals(entries.size, database.searchHistoryDAO().all.blockingFirst().size)

        // should remove everything
        manager.deleteCompleteSearchHistory().test().await().assertValue(entries.size)
        assertEquals(0, database.searchHistoryDAO().all.blockingFirst().size)
    }

    @Test
    fun getRelatedSearches_emptyQuery() {
        // make sure all entries were inserted
        database.searchHistoryDAO().insertAll(RELATED_SEARCHES_ENTRIES)
        assertEquals(
            RELATED_SEARCHES_ENTRIES.size,
            database.searchHistoryDAO().all.blockingFirst().size
        )

        // make sure correct number of searches is returned and in correct order
        val searches = manager.getRelatedSearches("", 6, 4).blockingFirst()
        assertEquals(4, searches.size)
        assertEquals(RELATED_SEARCHES_ENTRIES[6].search, searches[0]) // A (even if in two places)
        assertEquals(RELATED_SEARCHES_ENTRIES[4].search, searches[1]) // B
        assertEquals(RELATED_SEARCHES_ENTRIES[5].search, searches[2]) // AA
        assertEquals(RELATED_SEARCHES_ENTRIES[2].search, searches[3]) // BA
    }

    @Test
    fun getRelatedSearched_nonEmptyQuery() {
        // make sure all entries were inserted
        database.searchHistoryDAO().insertAll(RELATED_SEARCHES_ENTRIES)
        assertEquals(
            RELATED_SEARCHES_ENTRIES.size,
            database.searchHistoryDAO().all.blockingFirst().size
        )

        // make sure correct number of searches is returned and in correct order
        val searches = manager.getRelatedSearches("A", 3, 5).blockingFirst()
        assertEquals(3, searches.size)
        assertEquals(RELATED_SEARCHES_ENTRIES[6].search, searches[0]) // A (even if in two places)
        assertEquals(RELATED_SEARCHES_ENTRIES[5].search, searches[1]) // AA
        assertEquals(RELATED_SEARCHES_ENTRIES[1].search, searches[2]) // BA

        // also make sure that the string comparison is case insensitive
        val searches2 = manager.getRelatedSearches("a", 3, 5).blockingFirst()
        assertEquals(searches, searches2)
    }

    companion object {
        val RELATED_SEARCHES_ENTRIES = listOf(
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(7), 2, "AC"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(6), 0, "ABC"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(5), 1, "BA"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(4), 3, "A"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(2), 0, "B"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(3), 2, "AA"),
            SearchHistoryEntry(OffsetDateTime.now().minusSeconds(1), 1, "A"),
        )
    }
}
