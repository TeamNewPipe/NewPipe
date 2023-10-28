package org.schabi.newpipe.local.bookmark;

import android.app.Activity;
import android.net.Uri;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

public class BookmarkImportService {
    private Uri textFileUri;
    private RemotePlaylistManager remotePlaylistManager;
    private LocalPlaylistManager localPlaylistManager;
    private StreamingService streamingService;
    private Single<StreamInfo> streamInfoSingle;
    private StreamInfo streamInfo;
    List<StreamEntity> streams;
    public BookmarkImportService(final Uri textFileUri,
                                 final RemotePlaylistManager remotePlaylistManager,
                                 final LocalPlaylistManager localPlaylistManager) {
        this.textFileUri = textFileUri;
        this.remotePlaylistManager = remotePlaylistManager;
        this.localPlaylistManager = localPlaylistManager;
    }

    public void importBookmarks(final Activity activity) {
        readTextFile(activity);
    }
    public void readTextFile(final Activity activity) {
        int count = 0;
        if (textFileUri != null) {
            try {
                final InputStream inputStream =
                        activity.getContentResolver().openInputStream(textFileUri);
                if (inputStream != null) {
                    final BufferedReader reader =
                            new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (count == 0) {
                            //cannot create an empty playlist
                            createNewPlayListWithOneEntry();
                            count++;
                            getStreamEntity(line);
                        } else {
                            addEntries();
                        }
                    }
                    reader.close();
                    inputStream.close();
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public void createNewPlayListWithOneEntry() {
        System.out.println("LOL");
    }
    public void addEntries() {
        System.out.println("LOL");
    }
    public void getStreamEntity(final String url) {
        try {
            streamingService = NewPipe.getServiceByUrl(url);

            streamInfoSingle =
                    ExtractorHelper.getStreamInfo(streamingService.getServiceId(),
                            url, true);
            convertToStreamEntity(streamInfoSingle);
        } catch (final ExtractionException e) {
            throw new RuntimeException(e);
        }
    }
    public void convertToStreamEntity(final Single<StreamInfo> singleStreamInfo) {
        streamInfo = singleStreamInfo.blockingGet();
       final StreamEntity streamEntity =  new StreamEntity(
                Long.parseLong(streamInfo.getId()),
                streamInfo.getServiceId(),
                streamInfo.getUrl(),
                streamInfo.getName(),
                streamInfo.getStreamType(),
                streamInfo.getDuration(),
                streamInfo.getUploaderName(),
                streamInfo.getUploaderUrl(),
                ImageStrategy.imageListToDbUrl(streamInfo.getThumbnails()),
                streamInfo.getViewCount(),
                streamInfo.getTextualUploadDate(),
                streamInfo.getUploadDate() != null
                        ? streamInfo.getUploadDate().offsetDateTime() : null,
                streamInfo.getUploadDate() != null
                        ? streamInfo.getUploadDate().isApproximation() : null
        );
        streams.add(streamEntity);
    }
}
