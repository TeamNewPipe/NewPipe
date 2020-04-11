package org.schabi.newpipe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

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

public class PanicResponderActivity extends Activity {
    public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && PANIC_TRIGGER_ACTION.equals(intent.getAction())) {
            // TODO: Explicitly clear the search results
            //  once they are restored when the app restarts
            //  or if the app reloads the current video after being killed,
            //  that should be cleared also
            ExitActivity.exitAndRemoveFromRecentApps(this);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }
}
