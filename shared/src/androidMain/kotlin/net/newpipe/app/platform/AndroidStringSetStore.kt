/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.koin.core.annotation.Singleton

@Singleton(binds = [StringSetStore::class])
class AndroidStringSetStore(context: Context) : StringSetStore {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    override fun get(key: String, defaultValue: Set<String>): Set<String> = preferences
        .getStringSet(key, null)?.toSet() ?: defaultValue

    override fun put(key: String, value: Set<String>) = preferences.edit {
        putStringSet(key, value)
    }
}
