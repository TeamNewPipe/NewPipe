package org.schabi.newpipe.local.playlist;

import static org.schabi.newpipe.local.playlist.PlayListShareMode.JUST_URLS;
import static org.schabi.newpipe.local.playlist.PlayListShareMode.YOUTUBE_TEMP_PLAYLIST;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;
import java.util.stream.Stream;

public class LocalPlaylistFragmentTest {

    @Test
    public void export_asYouTubeTempPlaylist() {

        Stream<StreamEntity> entityStream = asStreamEntityStream(

            "https://www.youtube.com/watch?v=1"
           ,"https://www.youtube.com/watch?v=2"
           ,"https://www.youtube.com/watch?v=3"
        );

        String url = LocalPlaylistFragment.export(YOUTUBE_TEMP_PLAYLIST, entityStream, null);

        Assert.assertEquals("http://www.youtube.com/watch_videos?video_ids=1,2,3", url);
    }

    @Test
    public void export_justUrls() {

        Stream<StreamEntity> entityStream = asStreamEntityStream(

            "https://www.youtube.com/watch?v=1"
           ,"https://www.youtube.com/watch?v=2"
           ,"https://www.youtube.com/watch?v=3"
        );

        String exported = LocalPlaylistFragment.export(JUST_URLS, entityStream, null);

        Assert.assertEquals("""
            https://www.youtube.com/watch?v=1
            https://www.youtube.com/watch?v=2
            https://www.youtube.com/watch?v=3""", exported);
    }

    @NonNull
    private static Stream<StreamEntity> asStreamEntityStream(String... urls) {

        return Stream.of(urls)
            .map(LocalPlaylistFragmentTest::newStreamEntity);
    }

    @NonNull
    static StreamEntity newStreamEntity(String url) {

        return new StreamEntity(

              0
            , 1
            , url
            , "Title"
            , StreamType.VIDEO_STREAM
            , 100
            , "Uploader"
            , null
            , null
            , null
            , null
            , null
            , null
        );
    }
}
