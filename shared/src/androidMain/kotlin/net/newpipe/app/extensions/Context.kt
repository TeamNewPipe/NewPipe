/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extensions

import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.ComposeActivity
import net.newpipe.app.navigation.Destination

/**
 * Navigates to a given compose destination
 */
fun Context.navigateTo(destination: Destination) = Intent(this, ComposeActivity::class.java).also { intent ->
    intent.putExtra(Constants.INTENT_SCREEN_KEY, Json.encodeToString(destination))
    startActivity(intent)
}
