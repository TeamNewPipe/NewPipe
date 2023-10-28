package org.schabi.newpipe.local.bookmark;

import static android.widget.Toast.LENGTH_SHORT;

import android.app.Activity;
import android.net.Uri;
import android.widget.Toast;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public class BookmarkImportService {
    private Uri textFileUri;
    private LocalPlaylistManager localPlaylistManager;
    private int readerLineCount = 0;
    private int addedByBackgroundThreadCount = 0;

    List<StreamEntity> streams;

    public BookmarkImportService(final Uri textFileUri,
                                 final LocalPlaylistManager localPlaylistManager) {
        this.textFileUri = textFileUri;
        this.localPlaylistManager = localPlaylistManager;
    }

    public void importBookmarks(final Activity activity) {
        readTextFile(activity);
    }
    public void readTextFile(final Activity activity) {
        if (textFileUri != null) {
            try {
                final InputStream inputStream =
                        activity.getContentResolver().openInputStream(textFileUri);
                if (inputStream != null) {
                    final BufferedReader reader =
                            new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    streams = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        readerLineCount++;
                        handleUrl(activity, line);
                    }
                    reader.close();
                    inputStream.close();

                } else {
                    Toast.makeText(activity, "File is empty", LENGTH_SHORT).show();
                }
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public boolean handleUrl(final Activity activity, final String url) {
        final StreamingService service;
        final StreamingService.LinkType linkType;
        try {
            service = NewPipe.getServiceByUrl(url);
            linkType = service.getLinkTypeByUrl(url);
            if (linkType == StreamingService.LinkType.STREAM) {
                final LinkHandlerFactory factory = service.getStreamLHFactory();
                final String cleanUrl;
                try {
                    cleanUrl = factory.getUrl(factory.getId(url));
                } catch (final ParsingException e) {
                    return false;
                }
                final Single<StreamInfo> single =
                        ExtractorHelper.getStreamInfo(service.getServiceId(), cleanUrl, false);
                if (single != null) {
                    // Use a cached thread pool
                    final Executor executor = Executors.newCachedThreadPool();
                    executor.execute(() -> {
                        // Blocking network call
                        final StreamInfo streamInfo = single.blockingGet();
                        final StreamEntity streamEntity =  new StreamEntity(streamInfo);
                        addedByBackgroundThreadCount++;
                        // Update the streams list.
                        activity.runOnUiThread(() -> {
                            streams.add(streamEntity);

                            if (addedByBackgroundThreadCount == readerLineCount) {
                                //All background threads done.
                                //Add playlist
                                final Maybe<List<Long>> playlistIds =
                                        localPlaylistManager.createPlaylist("Sample", streams);
                                playlistIds.subscribe(list -> {
                                    //this is to make the fragment fetch data from the database
                                    //I could not find another way to do this.
                                });
                                Toast.makeText(activity, "Playlist added", LENGTH_SHORT).show();
                            }
                        });
                    });
                    ((ExecutorService) executor).shutdown();
                }
            }
        } catch (final ExtractionException e) {
            return false;
        }
        return false;
    }
}
