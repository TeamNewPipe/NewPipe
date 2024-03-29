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
import java.util.Optional;

import static com.google.android.exoplayer2.C.TIME_UNSET;
import static org.schabi.newpipe.util.ListHelper.getFilteredAudioStreams;
import static org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams;
import static org.schabi.newpipe.util.ListHelper.getPlayableStreams;

public class VideoPlaybackResolver implements PlaybackResolver {
    private static final String TAG = VideoPlaybackResolver.class.getSimpleName();

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @NonNull
    private final QualityResolver qualityResolver;
    private SourceType streamSourceType;

    @Nullable
    private String playbackQuality;
    @Nullable
    private String audioTrack;

    public enum SourceType {
        LIVE_STREAM,
        VIDEO_WITH_SEPARATED_AUDIO,
        VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
    }

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
            streamSourceType = SourceType.LIVE_STREAM;
            return liveSource;
        }

        final List<MediaSource> mediaSources = new ArrayList<>();

        // Create video stream source
        final List<VideoStream> videoStreamsList = ListHelper.getSortedStreamVideosList(context,
                getPlayableStreams(info.getVideoStreams(), info.getServiceId()),
                getPlayableStreams(info.getVideoOnlyStreams(), info.getServiceId()), false, true);
        final List<AudioStream> audioStreamsList =
                getFilteredAudioStreams(context, info.getAudioStreams());

        final int videoIndex;
        if (videoStreamsList.isEmpty()) {
            videoIndex = -1;
        } else if (playbackQuality == null) {
            videoIndex = qualityResolver.getDefaultResolutionIndex(videoStreamsList);
        } else {
            videoIndex = qualityResolver.getOverrideResolutionIndex(videoStreamsList,
                    getPlaybackQuality());
        }

        final int audioIndex =
                ListHelper.getAudioFormatIndex(context, audioStreamsList, audioTrack);
        final MediaItemTag tag =
                StreamInfoTag.of(info, videoStreamsList, videoIndex, audioStreamsList, audioIndex);
        @Nullable final VideoStream video = tag.getMaybeQuality()
                .map(MediaItemTag.Quality::getSelectedVideoStream)
                .orElse(null);
        @Nullable final AudioStream audio = tag.getMaybeAudioTrack()
                .map(MediaItemTag.AudioTrack::getSelectedAudioStream)
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

        // Use the audio stream if there is no video stream, or
        // merge with audio stream in case if video does not contain audio
        if (audio != null && (video == null || video.isVideoOnly() || audioTrack != null)) {
            try {
                final MediaSource audioSource = PlaybackResolver.buildMediaSource(
                        dataSource, audio, info, PlaybackResolver.cacheKeyOf(info, audio), tag);
                mediaSources.add(audioSource);
                streamSourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO;
            } catch (final ResolverException e) {
                Log.e(TAG, "Unable to create audio source", e);
                return null;
            }
        } else {
            streamSourceType = SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY;
        }

        // If there is no audio or video sources, then this media source cannot be played back
        if (mediaSources.isEmpty()) {
            return null;
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

    /**
     * Returns the last resolved {@link StreamInfo}'s {@link SourceType source type}.
     *
     * @return {@link Optional#empty()} if nothing was resolved, otherwise the {@link SourceType}
     * of the last resolved {@link StreamInfo} inside an {@link Optional}
     */
    public Optional<SourceType> getStreamSourceType() {
        return Optional.ofNullable(streamSourceType);
    }

    @Nullable
    public String getPlaybackQuality() {
        return playbackQuality;
    }

    public void setPlaybackQuality(@Nullable final String playbackQuality) {
        this.playbackQuality = playbackQuality;
    }

    @Nullable
    public String getAudioTrack() {
        return audioTrack;
    }

    public void setAudioTrack(@Nullable final String audioLanguage) {
        this.audioTrack = audioLanguage;
    }

    public interface QualityResolver {
        int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

        int getOverrideResolutionIndex(List<VideoStream> sortedVideos, String playbackQuality);
    }
}
