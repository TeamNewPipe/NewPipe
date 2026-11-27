/*
 * SPDX-FileCopyrightText: 2016-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

class ExitActivity : Activity() {
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finishAndRemoveTask()
        exitProcess(0)
    }

    companion object {
        @JvmStatic
        fun exitAndRemoveFromRecentApps(activity: Activity) {
            val intent = Intent(activity, ExitActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

            activity.startActivity(intent)
        }
    }
}
