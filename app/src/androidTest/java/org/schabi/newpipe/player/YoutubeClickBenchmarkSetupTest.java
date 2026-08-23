package org.schabi.newpipe.player;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/** Persists the path inputs consumed by the real app process in click-to-first-frame benchmarks. */
@RunWith(AndroidJUnit4.class)
public final class YoutubeClickBenchmarkSetupTest {
    @Test
    public void configure() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        final Bundle arguments = InstrumentationRegistry.getArguments();
        final String client = arguments.getString("youtubeClient", "mweb");
        final String cookieFile = arguments.getString("cookieFile", "");
        final SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putString(context.getString(R.string.youtube_player_client_key), client);
        if (cookieFile.isEmpty()) {
            editor.remove(context.getString(R.string.youtube_cookies_key));
        } else {
            editor.putString(context.getString(R.string.youtube_cookies_key),
                    readTextFile(new File(cookieFile)).trim());
        }
        assertTrue("Could not persist click benchmark inputs", editor.commit());
    }

    private static String readTextFile(final File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
