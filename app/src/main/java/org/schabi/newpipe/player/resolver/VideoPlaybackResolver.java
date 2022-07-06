package org.schabi.newpipe.player.resolver;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.ListHelper;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.exoplayer2.C.TIME_UNSET;
import static org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams;
import static org.schabi.newpipe.util.ListHelper.getNonTorrentStreams;

public class VideoPlaybackResolver implements PlaybackResolver {
    private static final String TAG = VideoPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @NonNull
    private final QualityResolver qualityResolver;

    @Nullable
    private String playbackQuality;

    public VideoPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource,
                                 @NonNull final QualityResolver qualityResolver) {
        this.context = context;
        this.dataSource = dataSource;
        this.qualityResolver = qualityResolver;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            return liveSource;
        }

        final List<MediaSource> mediaSources = new ArrayList<>();

        // Create video stream source
        final List<VideoStream> videoStreamsList = ListHelper.getSortedStreamVideosList(context,
                getNonTorrentStreams(info.getVideoStreams()),
                getNonTorrentStreams(info.getVideoOnlyStreams()), false, true);
        final int index;
        if (videoStreamsList.isEmpty()) {
            index = -1;
        } else if (playbackQuality == null) {
            index = qualityResolver.getDefaultResolutionIndex(videoStreamsList);
        } else {
            index = qualityResolver.getOverrideResolutionIndex(videoStreamsList,
                    getPlaybackQuality());
        }
        final StreamInfoTag tag = StreamInfoTag.of(info, videoStreamsList, index);
        @Nullable final VideoStream video = tag.getMaybeQuality()
                .map(MediaItemTag.Quality::getSelectedVideoStream)
                .orElse(null);

        if (video != null) {
            try {
                final MediaSource streamSource = PlaybackResolver.buildMediaSource(
                        dataSource, video, info, PlaybackResolver.cacheKeyOf(info, video), tag);
                mediaSources.add(streamSource);
            } catch (final ResolverException e) {
                Log.e(TAG, "Unable to create video source", e);
                return null;
            }
        }

        // Create optional audio stream source
        final List<AudioStream> audioStreams = getNonTorrentStreams(info.getAudioStreams());
        final AudioStream audio = audioStreams.isEmpty() ? null : audioStreams.get(
                ListHelper.getDefaultAudioFormat(context, audioStreams));

        // Use the audio stream if there is no video stream, or
        // merge with audio stream in case if video does not contain audio
        if (audio != null && (video == null || video.isVideoOnly())) {
            try {
                final MediaSource audioSource = PlaybackResolver.buildMediaSource(
                        dataSource, audio, info, PlaybackResolver.cacheKeyOf(info, audio), tag);
                mediaSources.add(audioSource);
            } catch (final ResolverException e) {
                Log.e(TAG, "Unable to create audio source", e);
                return null;
            }
        }

        // If there is no audio or video sources, then this media source cannot be played back
        if (mediaSources.isEmpty()) {
            return null;
        }

        if (video != null) {
            if (video.isVideoOnly()) {
                tag.setSourceType(audio == null
                        ? SourceType.VIDEO_ONLY : SourceType.VIDEO_WITH_SEPARATED_AUDIO);
            } else {
                tag.setSourceType(SourceType.VIDEO_WITH_AUDIO);
            }
        } else {
            // If there is no video stream, it means that there is an audio stream for playback
            // (because if there is no audio stream and video stream, the block above would be run)
            tag.setSourceType(SourceType.AUDIO_ONLY);
        }

        // Below are auxiliary media sources

        // Create subtitle sources
        final List<SubtitlesStream> subtitlesStreams = info.getSubtitles();
        if (subtitlesStreams != null) {
            // Torrent and non URL subtitles are not supported by ExoPlayer
            final List<SubtitlesStream> nonTorrentAndUrlStreams = getUrlAndNonTorrentStreams(
                    subtitlesStreams);
            for (final SubtitlesStream subtitle : nonTorrentAndUrlStreams) {
                final MediaFormat mediaFormat = subtitle.getFormat();
                if (mediaFormat != null) {
                    @C.RoleFlags final int textRoleFlag = subtitle.isAutoGenerated()
                            ? C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
                            : C.ROLE_FLAG_CAPTION;
                    final MediaItem.SubtitleConfiguration textMediaItem =
                            new MediaItem.SubtitleConfiguration.Builder(
                                    Uri.parse(subtitle.getContent()))
                                    .setMimeType(mediaFormat.getMimeType())
                                    .setRoleFlags(textRoleFlag)
                                    .setLanguage(PlayerHelper.captionLanguageOf(context, subtitle))
                                    .build();
                    final MediaSource textSource = dataSource.getSingleSampleMediaSourceFactory()
                            .createMediaSource(textMediaItem, TIME_UNSET);
                    mediaSources.add(textSource);
                }
            }
        }

        if (mediaSources.size() == 1) {
            return mediaSources.get(0);
        } else {
            return new MergingMediaSource(true, mediaSources.toArray(new MediaSource[0]));
        }
    }

    @Nullable
    public String getPlaybackQuality() {
        return playbackQuality;
    }

    public void setPlaybackQuality(@Nullable final String playbackQuality) {
        this.playbackQuality = playbackQuality;
    }

    public interface QualityResolver {
        int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

        int getOverrideResolutionIndex(List<VideoStream> sortedVideos, String playbackQuality);
    }
}
