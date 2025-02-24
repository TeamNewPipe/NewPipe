package org.schabi.newpipe.local.playlist;

import static org.schabi.newpipe.local.playlist.PlayListShareMode.JUST_URLS;
import static org.schabi.newpipe.local.playlist.PlayListShareMode.YOUTUBE_TEMP_PLAYLIST;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;
import java.util.stream.Stream;

public class LocalPlaylistFragmentTest {

    @Test
    public void exportAsYouTubeTempPlaylist() {

        final List<PlaylistStreamEntry> playlist = asPlaylist(

            "https://www.youtube.com/watch?v=1",
            "https://soundcloud.com/cautious-clayofficial/cold-war-2", // non-Youtube URLs should be
            "https://www.youtube.com/watch?v=2",                       // ignored
            "https://www.youtube.com/watch?v=3"
        );

        final String url = LocalPlaylistFragment.export(YOUTUBE_TEMP_PLAYLIST, playlist, null);

        Assert.assertEquals("http://www.youtube.com/watch_videos?video_ids=1,2,3", url);
    }

    @Test
    public void exportMoreThan50Items() {
        /*
         * Playlist has more than 50 items => take the last 50
         * (YouTube limitation)
         */

        final List<Integer> ids = List.of(

            -1,  0,
             1,  2,  3,  4,  5,  6,  7,  8,  9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50
        );

        final List<PlaylistStreamEntry> playlist = asPlaylist(

            ids.stream()
            .map(id -> "https://www.youtube.com/watch?v=" + id)
        );

        final String url = LocalPlaylistFragment.export(YOUTUBE_TEMP_PLAYLIST, playlist, null);

        Assert.assertEquals(

              "http://www.youtube.com/watch_videos?video_ids="
            + "1,2,3,4,5,6,7,8,9,10,"
            + "11,12,13,14,15,16,17,18,19,20,"
            + "21,22,23,24,25,26,27,28,29,30,"
            + "31,32,33,34,35,36,37,38,39,40,"
            + "41,42,43,44,45,46,47,48,49,50",

            url
        );
    }

    @Test
    public void exportJustUrls() {

        final List<PlaylistStreamEntry> playlist = asPlaylist(

            "https://www.youtube.com/watch?v=1",
            "https://www.youtube.com/watch?v=2",
            "https://www.youtube.com/watch?v=3"
        );

        final String exported = LocalPlaylistFragment.export(JUST_URLS, playlist, null);

        Assert.assertEquals("""
            https://www.youtube.com/watch?v=1
            https://www.youtube.com/watch?v=2
            https://www.youtube.com/watch?v=3""", exported);
    }

    @NonNull
    static List<PlaylistStreamEntry> asPlaylist(final String... urls) {

        return asPlaylist(Stream.of(urls));
    }

    @NonNull
    static List<PlaylistStreamEntry> asPlaylist(final Stream<String> urls) {

        return urls
            .map(LocalPlaylistFragmentTest::newPlaylistStreamEntry)
            .toList();
    }

    @NonNull
    private static PlaylistStreamEntry newPlaylistStreamEntry(final String url) {

        return new PlaylistStreamEntry(newStreamEntity(url), 0, 0, 0);
    }

    @NonNull
    static StreamEntity newStreamEntity(final String url) {

        return new StreamEntity(

            0,
            1,
            url,
            "Title",
            StreamType.VIDEO_STREAM,
            100,
            "Uploader",
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
