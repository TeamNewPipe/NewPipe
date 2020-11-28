package org.schabi.newpipe.ktx

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

val Context.defaultSharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(this)
