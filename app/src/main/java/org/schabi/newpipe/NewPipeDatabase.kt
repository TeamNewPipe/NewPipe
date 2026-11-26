/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.content.Context
import androidx.room.Room.databaseBuilder
import kotlin.concurrent.Volatile
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.Migrations.MIGRATION_10_11
import org.schabi.newpipe.database.Migrations.MIGRATION_11_12
import org.schabi.newpipe.database.Migrations.MIGRATION_12_13
import org.schabi.newpipe.database.Migrations.MIGRATION_13_14
import org.schabi.newpipe.database.Migrations.MIGRATION_14_15
import org.schabi.newpipe.database.Migrations.MIGRATION_15_16
import org.schabi.newpipe.database.Migrations.MIGRATION_16_17
import org.schabi.newpipe.database.Migrations.MIGRATION_17_18
import org.schabi.newpipe.database.Migrations.MIGRATION_18_19
import org.schabi.newpipe.database.Migrations.MIGRATION_19_20
import org.schabi.newpipe.database.Migrations.MIGRATION_1_2
import org.schabi.newpipe.database.Migrations.MIGRATION_20_21
import org.schabi.newpipe.database.Migrations.MIGRATION_21_22
import org.schabi.newpipe.database.Migrations.MIGRATION_22_23
import org.schabi.newpipe.database.Migrations.MIGRATION_23_24
import org.schabi.newpipe.database.Migrations.MIGRATION_24_25
import org.schabi.newpipe.database.Migrations.MIGRATION_25_26
import org.schabi.newpipe.database.Migrations.MIGRATION_26_27
import org.schabi.newpipe.database.Migrations.MIGRATION_27_28
import org.schabi.newpipe.database.Migrations.MIGRATION_28_29
import org.schabi.newpipe.database.Migrations.MIGRATION_29_30
import org.schabi.newpipe.database.Migrations.MIGRATION_2_3
import org.schabi.newpipe.database.Migrations.MIGRATION_30_31
import org.schabi.newpipe.database.Migrations.MIGRATION_31_32
import org.schabi.newpipe.database.Migrations.MIGRATION_32_33
import org.schabi.newpipe.database.Migrations.MIGRATION_33_34
import org.schabi.newpipe.database.Migrations.MIGRATION_34_35
import org.schabi.newpipe.database.Migrations.MIGRATION_3_4
import org.schabi.newpipe.database.Migrations.MIGRATION_4_5
import org.schabi.newpipe.database.Migrations.MIGRATION_5_6
import org.schabi.newpipe.database.Migrations.MIGRATION_6_7
import org.schabi.newpipe.database.Migrations.MIGRATION_7_8
import org.schabi.newpipe.database.Migrations.MIGRATION_8_9
import org.schabi.newpipe.database.Migrations.MIGRATION_9_10

object NewPipeDatabase {

    @Volatile
    private var databaseInstance: AppDatabase? = null

    private fun getDatabase(context: Context, databaseName: String): AppDatabase {
        return databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            databaseName
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35
        ).build()
    }

    @JvmStatic
    fun getInstance(context: Context): AppDatabase {
        var result = databaseInstance
        if (result == null) {
            synchronized(NewPipeDatabase::class.java) {
                result = databaseInstance
                if (result == null) {
                    databaseInstance = getDatabase(context, AppDatabase.DATABASE_NAME)
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

    /**
     * Opens and migrates a staged database with the same Room configuration used by the app.
     * Room validates every expected table and column before this method returns, so callers can
     * reject foreign databases without replacing the live database first.
     */
    @JvmStatic
    fun validateImportDatabase(context: Context, databaseName: String) {
        val database = getDatabase(context, databaseName)
        try {
            database.openHelper.writableDatabase
            database.query("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "The imported database failed SQLite integrity validation"
                }
            }
            database.query("PRAGMA wal_checkpoint(TRUNCATE)", null).close()
        } finally {
            database.close()
        }
    }
}
