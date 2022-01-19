package org.schabi.newpipe.local.history

import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertThat(entities).hasSize(1)
        assertThat(entities[0].id).isEqualTo(1)
        assertThat(entities[0].serviceId).isEqualTo(0)
        assertThat(entities[0].search).isEqualTo("Hello")
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
        assertThat(database.searchHistoryDAO().all.blockingFirst()).hasSameSizeAs(entries)

        // try to delete only "A" entries, "B" entries should be untouched
        manager.deleteSearchHistory("A").test().await().assertValue(2)
        val entities = database.searchHistoryDAO().all.blockingFirst()
        assertThat(entities).hasSize(2)
        assertThat(entities).usingElementComparator { o1, o2 -> if (o1.hasEqualValues(o2)) 0 else 1 }
            .containsExactly(*entries.subList(2, 4).toTypedArray())

        // assert that nothing happens if we delete a search query that does exist in the db
        manager.deleteSearchHistory("A").test().await().assertValue(0)
        val entities2 = database.searchHistoryDAO().all.blockingFirst()
        assertThat(entities2).hasSize(2)
        assertThat(entities2).usingElementComparator { o1, o2 -> if (o1.hasEqualValues(o2)) 0 else 1 }
            .containsExactly(*entries.subList(2, 4).toTypedArray())

        // delete all remaining entries
        manager.deleteSearchHistory("B").test().await().assertValue(2)
        assertThat(database.searchHistoryDAO().all.blockingFirst()).isEmpty()
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
        assertThat(database.searchHistoryDAO().all.blockingFirst()).hasSameSizeAs(entries)

        // should remove everything
        manager.deleteCompleteSearchHistory().test().await().assertValue(entries.size)
        assertThat(database.searchHistoryDAO().all.blockingFirst()).isEmpty()
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
        assertThat(searches).containsExactly(
            RELATED_SEARCHES_ENTRIES[6].search, // A (even if in two places)
            RELATED_SEARCHES_ENTRIES[4].search, // B
            RELATED_SEARCHES_ENTRIES[5].search, // AA
            RELATED_SEARCHES_ENTRIES[2].search, // BA
        )
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
        assertThat(searches).containsExactly(
            RELATED_SEARCHES_ENTRIES[6].search, // A (even if in two places)
            RELATED_SEARCHES_ENTRIES[5].search, // AA
            RELATED_SEARCHES_ENTRIES[1].search, // BA
        )

        // also make sure that the string comparison is case insensitive
        val searches2 = manager.getRelatedSearches("a", 3, 5).blockingFirst()
        assertThat(searches).isEqualTo(searches2)
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
