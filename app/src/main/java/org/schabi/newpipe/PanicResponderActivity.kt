package org.schabi.newpipe

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle

/*
* Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
* PanicResponderActivity.java is part of NewPipe.
*
* NewPipe is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* NewPipe is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
*/
class PanicResponderActivity() : Activity() {
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent: Intent? = getIntent()
        if (intent != null && (PANIC_TRIGGER_ACTION == intent.getAction())) {
            // TODO: Explicitly clear the search results
            //  once they are restored when the app restarts
            //  or if the app reloads the current video after being killed,
            //  that should be cleared also
            ExitActivity.Companion.exitAndRemoveFromRecentApps(this)
        }
        finishAndRemoveTask()
    }

    companion object {
        val PANIC_TRIGGER_ACTION: String = "info.guardianproject.panic.action.TRIGGER"
    }
}
