package org.schabi.newpipe.tv;

import android.os.StrictMode;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MovieList {
    private static List<Movie> list;
    private static long count = 0;

    public static List<String> getKiosks() {
        List<String> kiosks = new ArrayList<String>();
        for (StreamingService service : NewPipe.getServices()) {
            try {
                for (String kiosk : service.getKioskList().getAvailableKiosks()) {
                    kiosks.add(service.getServiceInfo().name + "/" + kiosk);
                }
            } catch (ExtractionException e) {}
        }
        return kiosks;
    }

    public static List<List<Movie>> getKiosksItems() {
        List<List<Movie>> kiosksItems = new ArrayList<List<Movie>>();
        for (StreamingService service : NewPipe.getServices()) {
            try {
                for (String kiosk : service.getKioskList().getAvailableKiosks()) {
                    List<Movie> kioskItems = new ArrayList<Movie>();
                    // FIXME: Don't use this hack
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    KioskExtractor kioskExtractor = service.getKioskList().getExtractorById(kiosk, null);
                    for (StreamInfoItem stream : kioskExtractor.getStreams().getStreamInfoItemList()) {
                        kioskItems.add(buildMovieInfo("category", stream.name, "description", stream.uploader_name, "videoUrl", stream.thumbnail_url));
                    }
                    kiosksItems.add(kioskItems);
                }
            } catch (IOException | ExtractionException e) {}
        }
        return kiosksItems;
    }

    private static Movie buildMovieInfo(String category, String title,
                                        String description, String studio, String videoUrl, String imageUrl) {
        Movie movie = new Movie();
        movie.setId(count++);
        movie.setTitle(title);
        movie.setDescription(description);
        movie.setStudio(studio);
        movie.setCategory(category);
        movie.setImageUrl(imageUrl);
        movie.setVideoUrl(videoUrl);
        return movie;
    }
}