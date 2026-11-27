/*
 * SPDX-FileCopyrightText: 2016-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess
import org.schabi.newpipe.MainActivity

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
            arrayOf(ExitActivity::class.java, MainActivity::class.java).forEach {
                val intent = Intent(activity, it)
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                intent.putExtra(MainActivity.EXTRA_REMOVE_FROM_RECENT_APPS_AND_EXIT, true)
                activity.startActivity(intent)
            }
        }
    }
}
