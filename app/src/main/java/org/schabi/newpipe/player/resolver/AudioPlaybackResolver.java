package org.schabi.newpipe.player.resolver;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.database.download.entry.DownloadEntry;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.download.DownloadRecordManager;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.ListHelper;

import us.shandian.giga.service.DownloadManager;

public class AudioPlaybackResolver implements PlaybackResolver {
    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;

    public AudioPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource) {
        this.context = context;
        this.dataSource = dataSource;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource liveSource = maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            return liveSource;
        }

        final int index = ListHelper.getDefaultAudioFormat(context, info.getAudioStreams());
        if (index < 0 || index >= info.getAudioStreams().size()) {
            return null;
        }

        final AudioStream audio = info.getAudioStreams().get(index);
        final MediaItemTag tag = StreamInfoTag.of(info);

        DownloadRecordManager d = new DownloadRecordManager(context);

        DownloadEntry downloadEntry = d.getUri(info.getId()).blockingGet();
        String uri = null;
        if (downloadEntry != null) {
            uri = downloadEntry.getUriValue();
        }

        return buildMediaSource(dataSource, audio.getUrl(), PlayerHelper.cacheKeyOf(info, audio),
                MediaFormat.getSuffixById(audio.getFormatId()), tag, uri);
    }
}
