/**
 * Created by k3b on 07.01.2016.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * NewPipeSettings.java is part of NewPipe.
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

package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import java.io.File;

/**
 * Helper for global settings
 */
public class NewPipeSettings {

    private NewPipeSettings() {
    }

    public static void initSettings(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.settings, false);
        getVideoDownloadFolder(context);
        getAudioDownloadFolder(context);
    }

    public static File getVideoDownloadFolder(Context context) {
        return getFolder(context, R.string.download_path_key, Environment.DIRECTORY_MOVIES);
    }

    public static File getAudioDownloadFolder(Context context) {
        return getFolder(context, R.string.download_path_audio_key, Environment.DIRECTORY_MUSIC);
    }

    private static File getFolder(Context context, int keyID, String defaultDirectoryName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(keyID);
        String downloadPath = prefs.getString(key, null);
        if ((downloadPath != null) && (!downloadPath.isEmpty())) return new File(downloadPath.trim());

        final File folder = getFolder(defaultDirectoryName);
        SharedPreferences.Editor spEditor = prefs.edit();
        spEditor.putString(key
                , new File(folder,"NewPipe").getAbsolutePath());
        spEditor.apply();
        return folder;
    }

    @NonNull
    private static File getFolder(String defaultDirectoryName) {
        return new File(Environment.getExternalStorageDirectory(),defaultDirectoryName);
    }
}
