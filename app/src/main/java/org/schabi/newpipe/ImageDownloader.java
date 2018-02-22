package org.schabi.newpipe;

import android.content.Context;

import com.nostra13.universalimageloader.core.download.BaseImageDownloader;

import org.schabi.newpipe.extractor.NewPipe;

import java.io.IOException;
import java.io.InputStream;

public class ImageDownloader extends BaseImageDownloader {
    public ImageDownloader(Context context) {
        super(context);
    }

    public ImageDownloader(Context context, int connectTimeout, int readTimeout) {
        super(context, connectTimeout, readTimeout);
    }

    protected InputStream getStreamFromNetwork(String imageUri, Object extra) throws IOException {
        Downloader downloader = (Downloader) NewPipe.getDownloader();
        return downloader.stream(imageUri);
    }
}
