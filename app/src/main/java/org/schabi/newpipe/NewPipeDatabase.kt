/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.content.Context
import androidx.room3.Room.databaseBuilder
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.concurrent.Volatile
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.Migrations.MIGRATION_1_2
import org.schabi.newpipe.database.Migrations.MIGRATION_2_3
import org.schabi.newpipe.database.Migrations.MIGRATION_3_4
import org.schabi.newpipe.database.Migrations.MIGRATION_4_5
import org.schabi.newpipe.database.Migrations.MIGRATION_5_6
import org.schabi.newpipe.database.Migrations.MIGRATION_6_7
import org.schabi.newpipe.database.Migrations.MIGRATION_7_8
import org.schabi.newpipe.database.Migrations.MIGRATION_8_9

object NewPipeDatabase {

    @Volatile
    private var databaseInstance: AppDatabase? = null

    private fun getDatabase(context: Context): AppDatabase {
        return databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        ).setDriver(BundledSQLiteDriver()).build()
    }

    @JvmStatic
    fun getInstance(context: Context): AppDatabase {
        var result = databaseInstance
        if (result == null) {
            synchronized(NewPipeDatabase::class.java) {
                result = databaseInstance
                if (result == null) {
                    databaseInstance = getDatabase(context)
                    result = databaseInstance
                }
            }
        }

        return result!!
    }

    /**
     * Runs `PRAGMA wal_checkpoint(full)`, folding the write-ahead log into the main database file
     * so that copying only `newpipe.db` yields a complete backup.
     *
     * Opens the database if this process has not done so yet. That is required for correctness,
     * not just convenience: committed but not yet checkpointed data lives in the `-wal` file, and
     * only opening the database recovers it. This is reachable from a cold process where nothing
     * has opened the database yet — e.g. the main page only has a Blank tab, or the process was
     * reclaimed while the system file picker was in the foreground. Callers must not assume that
     * some other component has already opened it.
     *
     * Blocks the calling thread, so call it off the main thread.
     *
     * @throws RuntimeException if the checkpoint was blocked from completing, i.e. another
     * connection kept part of the log from being folded into `newpipe.db`.
     */
    @JvmStatic
    fun checkpoint(context: Context) {
        val database = getInstance(context)
        runBlocking {
            database.useWriterConnection { transactor ->
                transactor.usePrepared("pragma wal_checkpoint(full)") { statement ->
                    if (statement.step() && statement.getInt(0) == 1) {
                        throw RuntimeException("Checkpoint was blocked from completing")
                    }
                }
            }
        }
    }

    @JvmStatic
    fun close() {
        if (databaseInstance != null) {
            synchronized(NewPipeDatabase::class.java) {
                if (databaseInstance != null) {
                    databaseInstance!!.close()
                    databaseInstance = null
                }
            }
        }
    }
}
