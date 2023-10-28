package org.schabi.newpipe.local.bookmark;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class BookmarkImportService {
    private Uri textFileUri;

    public BookmarkImportService(final Uri textFileUri) {
        this.textFileUri = textFileUri;
    }

    public void importBookmarks(final Activity activity) {
        if (textFileUri != null) {
        try {
            final InputStream inputStream =
                    activity.getContentResolver().openInputStream(textFileUri);
            if (inputStream != null) {
                final BufferedReader reader =
                        new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = reader.readLine()) != null) {
                    Toast.makeText(activity, line, Toast.LENGTH_SHORT).show();
                }

                reader.close();
                inputStream.close();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        }
    }

}
