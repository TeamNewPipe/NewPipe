/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Runs in the test APK so Android identifies it as a different app from the responder.
 */
class PanicTriggerTestActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPackage = requireNotNull(intent.getStringExtra(EXTRA_TARGET_PACKAGE))
        val action = requireNotNull(intent.getStringExtra(EXTRA_ACTION))
        startActivityForResult(
            Intent(action).setClassName(targetPackage, RESPONDER_CLASS),
            1
        )
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        sendBroadcast(
            Intent(ACTION_FINISHED)
                .setPackage(requireNotNull(intent.getStringExtra(EXTRA_TARGET_PACKAGE)))
        )
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_ACTION = "action"
        const val ACTION_FINISHED = "org.schabi.newpipe.panic.test.TRIGGER_FINISHED"
        private const val RESPONDER_CLASS = "org.schabi.newpipe.panic.PanicResponderActivity"
    }
}
