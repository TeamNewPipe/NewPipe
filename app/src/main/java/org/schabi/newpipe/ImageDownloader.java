package org.schabi.newpipe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import org.schabi.newpipe.extractor.NewPipe;

import java.io.IOException;
import java.io.InputStream;

public class ImageDownloader extends BaseImageDownloader {
    private final Resources resources;
    private final SharedPreferences preferences;
    private final String downloadThumbnailKey;

    public ImageDownloader(final Context context) {
        super(context);
        this.resources = context.getResources();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.downloadThumbnailKey = context.getString(R.string.download_thumbnail_key);
    }

    private boolean isDownloadingThumbnail() {
        return preferences.getBoolean(downloadThumbnailKey, true);
    }

    @SuppressLint("ResourceType")
    @Override
    public InputStream getStream(final String imageUri, final Object extra) throws IOException {
        if (isDownloadingThumbnail()) {
            return super.getStream(imageUri, extra);
        } else {
            return resources.openRawResource(R.drawable.dummy_thumbnail_dark);
        }
    }

    protected InputStream getStreamFromNetwork(final String imageUri, final Object extra)
            throws IOException {
        final DownloaderImpl downloader = (DownloaderImpl) NewPipe.getDownloader();
        return downloader.stream(imageUri);
    }
}
