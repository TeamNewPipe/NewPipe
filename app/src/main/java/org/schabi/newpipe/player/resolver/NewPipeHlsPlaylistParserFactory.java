package org.schabi.newpipe.player.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.upstream.ParsingLoadable;

import java.io.IOException;
import java.io.InputStream;

public class NewPipeHlsPlaylistParserFactory implements HlsPlaylistParserFactory {

    private final HlsPlaylist hlsPlaylist;

    public NewPipeHlsPlaylistParserFactory(final HlsPlaylist hlsPlaylist) {
        this.hlsPlaylist = hlsPlaylist;
    }

    private class NewPipeHlsPlayListParser implements ParsingLoadable.Parser<HlsPlaylist> {

        @Override
        public HlsPlaylist parse(final Uri uri,
                                 final InputStream inputStream) throws IOException {
            return hlsPlaylist;
        }
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        return new NewPipeHlsPlayListParser();
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
            @NonNull final HlsMasterPlaylist masterPlaylist) {
        return new NewPipeHlsPlayListParser();
    }
}
