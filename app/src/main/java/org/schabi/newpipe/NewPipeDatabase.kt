/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.content.Context
import androidx.room.Room.databaseBuilder
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.Migrations.MIGRATION_1_2
import org.schabi.newpipe.database.Migrations.MIGRATION_2_3
import org.schabi.newpipe.database.Migrations.MIGRATION_3_4
import org.schabi.newpipe.database.Migrations.MIGRATION_4_5
import org.schabi.newpipe.database.Migrations.MIGRATION_5_6
import org.schabi.newpipe.database.Migrations.MIGRATION_6_7
import org.schabi.newpipe.database.Migrations.MIGRATION_7_8
import org.schabi.newpipe.database.Migrations.MIGRATION_8_9
import kotlin.concurrent.Volatile

object NewPipeDatabase {

    @Volatile
    private var databaseInstance: AppDatabase? = null

    private fun getDatabase(context: Context): AppDatabase {
        return databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.Companion.DATABASE_NAME
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9
        ).build()
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

    @JvmStatic
    fun checkpoint() {
        checkNotNull(databaseInstance) { "database is not initialized" }
        val c = databaseInstance!!.query("pragma wal_checkpoint(full)", null)
        if (c.moveToFirst() && c.getInt(0) == 1) {
            throw RuntimeException("Checkpoint was blocked from completing")
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
