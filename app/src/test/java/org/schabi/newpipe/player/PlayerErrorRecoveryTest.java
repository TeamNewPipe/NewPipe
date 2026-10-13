package org.schabi.newpipe.player;

import android.net.Uri;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediasource.LoadedMediaSource;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlayerErrorRecoveryTest {

    @Test
    public void testIsPlaylistStuckExceptionDirectAndNested() {
        final Uri uri = mock(Uri.class);
        final HlsPlaylistTracker.PlaylistStuckException stuckException =
                new HlsPlaylistTracker.PlaylistStuckException(uri);

        final PlaybackException directError = mock(PlaybackException.class);
        when(directError.getCause()).thenReturn(stuckException);
        assertTrue(Player.isPlaylistStuckException(directError));

        final IOException nested = new IOException("Wrapper", stuckException);
        final PlaybackException deepError = mock(PlaybackException.class);
        when(deepError.getCause()).thenReturn(nested);
        assertTrue(Player.isPlaylistStuckException(deepError));

        final PlaybackException normalError = mock(PlaybackException.class);
        when(normalError.getCause()).thenReturn(new IOException("Network timeout"));
        assertFalse(Player.isPlaylistStuckException(normalError));
    }

    @Test
    public void testIsHttpForbiddenOrExpired() {
        final DataSpec dataSpec = mock(DataSpec.class);
        final HttpDataSource.InvalidResponseCodeException forbidden403 =
                new HttpDataSource.InvalidResponseCodeException(
                        403, "Forbidden", null, Collections.emptyMap(), dataSpec, new byte[0]);
        final PlaybackException error403 = mock(PlaybackException.class);
        when(error403.getCause()).thenReturn(forbidden403);
        assertTrue(Player.isHttpForbiddenOrExpired(error403));

        final HttpDataSource.InvalidResponseCodeException gone410 =
                new HttpDataSource.InvalidResponseCodeException(
                        410, "Gone", null, Collections.emptyMap(), dataSpec, new byte[0]);
        final PlaybackException error410 = mock(PlaybackException.class);
        when(error410.getCause()).thenReturn(gone410);
        assertTrue(Player.isHttpForbiddenOrExpired(error410));

        final HttpDataSource.InvalidResponseCodeException notFound404 =
                new HttpDataSource.InvalidResponseCodeException(
                        404, "Not Found", null, Collections.emptyMap(), dataSpec, new byte[0]);
        final PlaybackException error404 = mock(PlaybackException.class);
        when(error404.getCause()).thenReturn(notFound404);
        assertFalse(Player.isHttpForbiddenOrExpired(error404));
    }

    @Test
    public void testLoadedMediaSourceInvalidation() {
        final MediaSource mockSource = mock(MediaSource.class);
        final MediaItemTag mockTag = mock(MediaItemTag.class);
        final MediaItem mockMediaItem = mock(MediaItem.class);
        when(mockTag.withExtras(any())).thenReturn(mockTag);
        when(mockTag.asMediaItem()).thenReturn(mockMediaItem);

        final StreamInfoItem infoItem = new StreamInfoItem(
                0, "https://example.com/video", "title", StreamType.VIDEO_STREAM);
        final PlayQueueItem playQueueItem = new SinglePlayQueue(infoItem).getItem();

        // Not expired (1 hour in future)
        final long futureExpiry = System.currentTimeMillis() + 3600000;
        final LoadedMediaSource loaded = new LoadedMediaSource(
                mockSource, mockTag, playQueueItem, futureExpiry);

        assertFalse(loaded.isInvalidated());
        assertFalse(loaded.shouldBeReplacedWith(playQueueItem, true));

        loaded.invalidate();
        assertTrue(loaded.isInvalidated());
        assertTrue(loaded.shouldBeReplacedWith(playQueueItem, true));
    }
}
