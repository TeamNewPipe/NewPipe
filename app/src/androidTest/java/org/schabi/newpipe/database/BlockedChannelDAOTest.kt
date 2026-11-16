package org.schabi.newpipe.database

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.database.block.BlockedChannelDAO
import org.schabi.newpipe.database.block.BlockedChannelEntity

class BlockedChannelDAOTest {
    private lateinit var db: AppDatabase
    private lateinit var blockedChannelDAO: BlockedChannelDAO

    val channelA = BlockedChannelEntity(url = "https://www.youtube.com/channel/a", name = "a channel")
    val channelB = BlockedChannelEntity(url = "https://www.youtube.com/channel/b", name = "B channel")

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        blockedChannelDAO = db.blockedChannelDAO()
    }

    @Test
    fun testChannelsAreSortedByName() {
        // The new table starts empty and works
        assertEquals(0, blockedChannelDAO.getBlockedUrls().size)
        blockedChannelDAO.upsert(channelB)
        blockedChannelDAO.upsert(channelA)
        assertEquals(2, blockedChannelDAO.getBlockedUrls().size)

        assertEquals(
            setOf("https://www.youtube.com/channel/a", "https://www.youtube.com/channel/b"),
            blockedChannelDAO.getBlockedUrls().toSet()
        )
        // Sorted by name, ignoring case
        assertEquals(
            listOf("a channel", "B channel"),
            blockedChannelDAO.getAll().blockingFirst().map { it.name }
        )
    }

    @Test
    fun testTheSameChannelUrlCanOnlyBeBlockedOnce() {
        // the same channel url can only be blocked once - blocking it again replaces the old entry
        assertEquals(0, blockedChannelDAO.getBlockedUrls().size)
        blockedChannelDAO.upsert(channelA)
        blockedChannelDAO.upsert(channelB)
        assertEquals(2, blockedChannelDAO.getBlockedUrls().size)

        blockedChannelDAO.upsert(channelB)
        assertEquals(2, blockedChannelDAO.getBlockedUrls().size)

        blockedChannelDAO.deleteByUrl("https://www.youtube.com/channel/a")
        assertEquals(listOf("https://www.youtube.com/channel/b"), blockedChannelDAO.getBlockedUrls())
    }
}
