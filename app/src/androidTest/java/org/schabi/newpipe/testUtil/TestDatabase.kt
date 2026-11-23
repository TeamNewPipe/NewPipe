package org.schabi.newpipe.testUtil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import org.junit.Assert.assertSame
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase

class TestDatabase {
    companion object {
        @JvmOverloads
        fun createReplacingNewPipeDatabase(queryExecutor: Executor? = null): AppDatabase {
            val database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .apply {
                    queryExecutor?.let {
                        setQueryExecutor(it)
                        setTransactionExecutor(it)
                    }
                }
                .build()

            val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance")
            databaseField.isAccessible = true
            databaseField.set(NewPipeDatabase::class, database)

            assertSame(
                "Mocking database failed!",
                database,
                NewPipeDatabase.getInstance(ApplicationProvider.getApplicationContext())
            )

            return database
        }
    }
}
