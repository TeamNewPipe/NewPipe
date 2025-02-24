package org.schabi.newpipe.local.playlist;

import static com.google.common.collect.Streams.stream;
import static org.apache.commons.collections4.IterableUtils.reversedIterable;
import static java.util.Collections.reverse;

import android.content.Context;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import okhttp3.HttpUrl;



final class ExportPlaylist {

    private ExportPlaylist() {
    }

    static String export(final PlayListShareMode shareMode,
                         final List<PlaylistStreamEntry> playlist,
                         final Context context) {

        return switch (shareMode) {

            case WITH_TITLES            -> exportWithTitles(playlist, context);
            case JUST_URLS              -> exportJustUrls(playlist);
            case YOUTUBE_TEMP_PLAYLIST  -> exportAsYoutubeTempPlaylist(playlist);
        };
    }

    static String exportWithTitles(final List<PlaylistStreamEntry> playlist,
                                   final Context context) {

        return playlist.stream()
            .map(PlaylistStreamEntry::getStreamEntity)
            .map(entity -> context.getString(R.string.video_details_list_item,
                                             entity.getTitle(),
                                             entity.getUrl()
                                            )
            )
            .collect(Collectors.joining("\n"));
    }

    static String exportJustUrls(final List<PlaylistStreamEntry> playlist) {

        return playlist.stream()
            .map(PlaylistStreamEntry::getStreamEntity)
            .map(StreamEntity::getUrl)
            .collect(Collectors.joining("\n"));
    }

    static String exportAsYoutubeTempPlaylist(final List<PlaylistStreamEntry> playlist) {

        final List<String> videoIDs =
            stream(reversedIterable(playlist))
                .map(PlaylistStreamEntry::getStreamEntity)
                .map(entity -> getYouTubeId(entity.getUrl()))
                .filter(Objects::nonNull)
                .limit(50)
                .collect(Collectors.toList());

        reverse(videoIDs);

        final String commaSeparatedVideoIDs = videoIDs.stream()
            .collect(Collectors.joining(","));

        return "http://www.youtube.com/watch_videos?video_ids=" + commaSeparatedVideoIDs;
    }

    /**
     * Gets the video id from a YouTube URL.
     *
     * @param url YouTube URL
     * @return the video id
     */
    static String getYouTubeId(final String url) {

        final HttpUrl httpUrl = HttpUrl.parse(url);

        return httpUrl == null ? null
                               : httpUrl.queryParameter("v");
    }
}
