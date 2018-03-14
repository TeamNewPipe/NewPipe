package org.schabi.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import org.schabi.newpipe.extractor.NewPipe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageDownloader extends BaseImageDownloader {
    private static final ByteArrayInputStream DUMMY_INPUT_STREAM =
            new ByteArrayInputStream(new byte[]{});

    private final SharedPreferences preferences;
    private final String downloadThumbnailKey;

    public ImageDownloader(Context context) {
        super(context);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.downloadThumbnailKey = context.getString(R.string.download_thumbnail_key);
    }

    private boolean isDownloadingThumbnail() {
        return preferences.getBoolean(downloadThumbnailKey, true);
    }

    protected InputStream getStreamFromNetwork(String imageUri, Object extra) throws IOException {
        if (isDownloadingThumbnail()) {
            final Downloader downloader = (Downloader) NewPipe.getDownloader();
            return downloader.stream(imageUri);
        } else {
            return DUMMY_INPUT_STREAM;
        }
    }
}
