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
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
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

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class BookmarkImportService {
    private Uri textFileUri;
    private RemotePlaylistManager remotePlaylistManager;
    private LocalPlaylistManager localPlaylistManager;

    private CompositeDisposable disposable;

    List<StreamEntity> streams = new ArrayList<>();

    public BookmarkImportService(final Uri textFileUri,
                                 final RemotePlaylistManager remotePlaylistManager,
                                 final LocalPlaylistManager localPlaylistManager,
                                 final CompositeDisposable compositeDisposable) {
        this.textFileUri = textFileUri;
        this.remotePlaylistManager = remotePlaylistManager;
        this.localPlaylistManager = localPlaylistManager;
        this.disposable = compositeDisposable;
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
                        handleUrl(activity, line);
                        if (count == 0) {
                            //cannot create an empty playlist
                            createNewPlayListWithOneEntry();
                            count++;
                        } else {
                            addEntries();
                        }
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
    public void createNewPlayListWithOneEntry() {
        System.out.println("LOL");
    }
    public void addEntries() {
        System.out.println("LOL");
    }
    public String handleUrl(final Activity activity, final String url) {
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
                    return "parsingException";
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
                        // Update UI
                        activity.runOnUiThread(() -> {
                            streams.add(streamEntity);
                        });
                    });
                    ((ExecutorService) executor).shutdown();
                }
            }
        } catch (final ExtractionException e) {
            return "false1";
        }
        return "false2";
    }
}
