package org.schabi.newpipe.local.history

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule

class HistoryRecordManagerTest {

    private lateinit var manager: HistoryRecordManager
    private lateinit var database: AppDatabase

    @get:Rule
    val trampolineScheduler = TrampolineSchedulerRule()

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
        val entities = database.searchHistoryDAO().getAll().blockingFirst()
        assertThat(entities).hasSize(1)
        assertThat(entities[0].id).isEqualTo(1)
        assertThat(entities[0].serviceId).isEqualTo(0)
        assertThat(entities[0].search).isEqualTo("Hello")
    }

    @Test
    fun deleteSearchHistory() {
        val entries = listOf(
            SearchHistoryEntry(creationDate = time.minusSeconds(1), serviceId = 0, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(2), serviceId = 2, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(3), serviceId = 1, search = "B"),
            SearchHistoryEntry(creationDate = time.minusSeconds(4), serviceId = 0, search = "B")
        )

        // make sure all 4 were inserted
        database.searchHistoryDAO().insertAll(entries)
        assertThat(database.searchHistoryDAO().getAll().blockingFirst()).hasSameSizeAs(entries)

        // try to delete only "A" entries, "B" entries should be untouched
        manager.deleteSearchHistory("A").test().await().assertValue(2)
        val entities = database.searchHistoryDAO().getAll().blockingFirst()
        assertThat(entities).hasSize(2)
        assertThat(entities).usingElementComparator { o1, o2 -> if (o1.hasEqualValues(o2)) 0 else 1 }
            .containsExactly(*entries.subList(2, 4).toTypedArray())

        // assert that nothing happens if we delete a search query that does exist in the db
        manager.deleteSearchHistory("A").test().await().assertValue(0)
        val entities2 = database.searchHistoryDAO().getAll().blockingFirst()
        assertThat(entities2).hasSize(2)
        assertThat(entities2).usingElementComparator { o1, o2 -> if (o1.hasEqualValues(o2)) 0 else 1 }
            .containsExactly(*entries.subList(2, 4).toTypedArray())

        // delete all remaining entries
        manager.deleteSearchHistory("B").test().await().assertValue(2)
        assertThat(database.searchHistoryDAO().getAll().blockingFirst()).isEmpty()
    }

    @Test
    fun deleteCompleteSearchHistory() {
        val entries = listOf(
            SearchHistoryEntry(creationDate = time.minusSeconds(1), serviceId = 1, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(2), serviceId = 2, search = "B"),
            SearchHistoryEntry(creationDate = time.minusSeconds(3), serviceId = 0, search = "C")
        )

        // make sure all 3 were inserted
        database.searchHistoryDAO().insertAll(entries)
        assertThat(database.searchHistoryDAO().getAll().blockingFirst()).hasSameSizeAs(entries)

        // should remove everything
        manager.deleteCompleteSearchHistory().test().await().assertValue(entries.size)
        assertThat(database.searchHistoryDAO().getAll().blockingFirst()).isEmpty()
    }

    private fun insertShuffledRelatedSearches(relatedSearches: Collection<SearchHistoryEntry>) {
        // shuffle to make sure the order of items returned by queries depends only on
        // SearchHistoryEntry.creationDate, not on the actual insertion time, so that we can
        // verify that the `ORDER BY` clause does its job
        database.searchHistoryDAO().insertAll(relatedSearches.shuffled())

        // make sure all entries were inserted
        assertEquals(
            relatedSearches.size,
            database.searchHistoryDAO().getAll().blockingFirst().size
        )
    }

    @Test
    fun getRelatedSearches_emptyQuery() {
        insertShuffledRelatedSearches(RELATED_SEARCHES_ENTRIES)

        // make sure correct number of searches is returned and in correct order
        val searches = manager.getRelatedSearches("", 6, 4).blockingFirst()
        assertThat(searches).containsExactly(
            RELATED_SEARCHES_ENTRIES[6].search, // A (even if in two places)
            RELATED_SEARCHES_ENTRIES[4].search, // B
            RELATED_SEARCHES_ENTRIES[5].search, // AA
            RELATED_SEARCHES_ENTRIES[2].search // BA
        )
    }

    @Test
    fun getRelatedSearches_emptyQuery_manyDuplicates() {
        val relatedSearches = listOf(
            SearchHistoryEntry(creationDate = time.minusSeconds(9), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(8), serviceId = 3, search = "AB"),
            SearchHistoryEntry(creationDate = time.minusSeconds(7), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(6), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(5), serviceId = 3, search = "BA"),
            SearchHistoryEntry(creationDate = time.minusSeconds(4), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(3), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(2), serviceId = 0, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(1), serviceId = 2, search = "AA")
        )
        insertShuffledRelatedSearches(relatedSearches)

        val searches = manager.getRelatedSearches("", 9, 3).blockingFirst()
        assertThat(searches).containsExactly("AA", "A", "BA")
    }

    @Test
    fun getRelatedSearched_nonEmptyQuery() {
        insertShuffledRelatedSearches(RELATED_SEARCHES_ENTRIES)

        // make sure correct number of searches is returned and in correct order
        val searches = manager.getRelatedSearches("A", 3, 5).blockingFirst()
        assertThat(searches).containsExactly(
            RELATED_SEARCHES_ENTRIES[6].search, // A (even if in two places)
            RELATED_SEARCHES_ENTRIES[5].search, // AA
            RELATED_SEARCHES_ENTRIES[1].search // BA
        )

        // also make sure that the string comparison is case insensitive
        val searches2 = manager.getRelatedSearches("a", 3, 5).blockingFirst()
        assertThat(searches).isEqualTo(searches2)
    }

    companion object {
        private val time = OffsetDateTime.of(LocalDateTime.of(2000, 1, 1, 1, 1), ZoneOffset.UTC)

        private val RELATED_SEARCHES_ENTRIES = listOf(
            SearchHistoryEntry(creationDate = time.minusSeconds(7), serviceId = 2, search = "AC"),
            SearchHistoryEntry(creationDate = time.minusSeconds(6), serviceId = 0, search = "ABC"),
            SearchHistoryEntry(creationDate = time.minusSeconds(5), serviceId = 1, search = "BA"),
            SearchHistoryEntry(creationDate = time.minusSeconds(4), serviceId = 3, search = "A"),
            SearchHistoryEntry(creationDate = time.minusSeconds(2), serviceId = 0, search = "B"),
            SearchHistoryEntry(creationDate = time.minusSeconds(3), serviceId = 2, search = "AA"),
            SearchHistoryEntry(creationDate = time.minusSeconds(1), serviceId = 1, search = "A")
        )
    }
}
