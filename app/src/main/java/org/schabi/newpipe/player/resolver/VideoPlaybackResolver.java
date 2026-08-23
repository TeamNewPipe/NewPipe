package org.schabi.newpipe.player.resolver;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.StreamInfoTag;
import org.schabi.newpipe.util.ListHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.android.exoplayer2.C.TIME_UNSET;
import static org.schabi.newpipe.util.ListHelper.*;

public class VideoPlaybackResolver implements PlaybackResolver {

    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @NonNull
    private final QualityResolver qualityResolver;
    private SourceType streamSourceType;

    @Nullable
    private String selectedResolution;
    @Nullable
    private String selectedCodec;
    @Nullable
    private String audioTrack;

    private List<String> blacklistUrls = new ArrayList<>();

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
        return resolve(info, 0);
    }

    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info, final long initialPositionMs) {
        final long normalizedInitialPositionMs = Math.max(0, initialPositionMs);
        final MediaSource liveSource = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            streamSourceType = SourceType.LIVE_STREAM;
            return liveSource;
        }

        final List<MediaSource> mediaSources = new ArrayList<>();
        final List<VideoStream> videoStreams = new ArrayList<>(info.getVideoStreams());
        final List<VideoStream> videoOnlyStreams = new ArrayList<>(info.getVideoOnlyStreams());

        removeTorrentStreams(videoStreams);
        removeTorrentStreams(videoOnlyStreams);

        if (info.getStreamType() == StreamType.POST_LIVE_STREAM
                && videoStreams.stream()
                .anyMatch(stream -> stream.getDeliveryMethod() == DeliveryMethod.HLS)) {
            videoStreams.removeIf(stream -> stream.getDeliveryMethod() != DeliveryMethod.HLS);
            videoOnlyStreams.clear();
        }

        // Create video stream source
        List<VideoStream> videos = ListHelper.getSortedStreamVideosList(context,
                videoStreams, videoOnlyStreams, false, true)
                .stream().filter(s -> !blacklistUrls.contains(s.getContent())).collect(Collectors.toList());

        if (audioTrack != null) {
            final List<VideoStream> filtered = videos.stream()
                    .filter(s -> audioTrack.equals(s.getAudioTrackId()))
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                videos = filtered;
            }
        } else {
            final boolean hasVideoAudioTracks = videos.stream()
                    .anyMatch(s -> s.getAudioTrackId() != null);
            if (hasVideoAudioTracks) {
                final List<AudioStream> allAudioStreams = ListHelper.getFilteredAudioStreams(
                        context, info.getAudioStreams());
                final int defaultIdx = ListHelper.getDefaultAudioFormat(context, allAudioStreams);
                if (defaultIdx >= 0 && defaultIdx < allAudioStreams.size()) {
                    final String defaultTrackId = allAudioStreams.get(defaultIdx).getAudioTrackId();
                    if (defaultTrackId != null) {
                        final List<VideoStream> filtered = videos.stream()
                                .filter(s -> defaultTrackId.equals(s.getAudioTrackId()))
                                .collect(Collectors.toList());
                        if (!filtered.isEmpty()) {
                            videos = filtered;
                        }
                    }
                }
            }
        }
        int index;
        if (videos.isEmpty()) {
            index = -1;
        } else if (selectedResolution == null) {
            index = qualityResolver.getDefaultResolutionIndex(videos);
        } else {
            index = qualityResolver.getOverrideResolutionIndex(
                    videos, selectedResolution, selectedCodec);
        }
        if (!videos.isEmpty() && (index < 0 || index >= videos.size())) {
            index = qualityResolver.getDefaultResolutionIndex(videos);
            if (index < 0 || index >= videos.size()) {
                index = 0;
            }
        }
        final MediaItemTag tag = StreamInfoTag.of(info, videos, index);
        @Nullable final VideoStream video = tag.getMaybeQuality()
                .map(MediaItemTag.Quality::getSelectedVideoStream)
                .orElse(null);

        if (video != null) {
            try {
                final MediaSource streamSource = PlaybackResolver.buildMediaSource(
                        dataSource, video, info, PlayerHelper.cacheKeyOf(info, video), tag,
                        normalizedInitialPositionMs);
                mediaSources.add(streamSource);
            } catch (final IOException e) {
                if (video.getDeliveryMethod()
                        == org.schabi.newpipe.extractor.stream.DeliveryMethod.SABR) {
                    throw new IllegalStateException(
                            "Unable to create SABR video source for " + info.getUrl(), e);
                }
                return null;
            }
        }

        // Create optional audio stream source
        final List<AudioStream> audioStreams = ListHelper.getFilteredAudioStreams(context,
                info.getAudioStreams()
                        .stream().filter(s -> !blacklistUrls.contains(s.getContent()))
                        .collect(Collectors.toList()));
        final int audioIndex = ListHelper.getAudioFormatIndex(context, audioStreams, audioTrack);
        final AudioStream audio = audioStreams.isEmpty() || audioIndex == -1
                ? null : audioStreams.get(audioIndex);

        // Use the audio stream if there is no video stream, or
        // merge with audio stream in case if video does not contain audio
        // SABR carries audio + video in one MediaSource, so don't add a separate audio source.
        final boolean videoIsSabr = video != null && video.getDeliveryMethod()
                == org.schabi.newpipe.extractor.stream.DeliveryMethod.SABR;
        final boolean videoHasMatchingAudio = video != null && !video.isVideoOnly()
                && audioTrack != null && audioTrack.equals(video.getAudioTrackId());
        if (audio != null && !videoHasMatchingAudio && !videoIsSabr
                && (video == null || video.isVideoOnly() || audioTrack != null)) {
            try {
                final MediaSource audioSource = PlaybackResolver.buildMediaSource(
                        dataSource, audio, info, PlayerHelper.cacheKeyOf(info, audio), tag);
                mediaSources.add(audioSource);
                streamSourceType = SourceType.VIDEO_WITH_SEPARATED_AUDIO;
            } catch (final IOException e) {
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
            final List<SubtitlesStream> nonTorrentAndUrlStreams = removeNonUrlAndTorrentStreams(
                    subtitlesStreams);
            for (final SubtitlesStream subtitle : nonTorrentAndUrlStreams) {
                final MediaFormat mediaFormat = subtitle.getFormat();
                if (mediaFormat != null) {
                    @C.RoleFlags final int textRoleFlag = subtitle.isAutoGenerated()
                            ? C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
                            : C.ROLE_FLAG_CAPTION;
                    if(!subtitle.isUrl()){
                        final MediaItem.SubtitleConfiguration textMediaItem =
                                new MediaItem.SubtitleConfiguration.Builder(
                                        Uri.parse(""))
                                        .setMimeType(mediaFormat.getMimeType())
                                        .setRoleFlags(textRoleFlag)
                                        .setLanguage(PlayerHelper.captionLanguageOf(context, subtitle))
                                        .build();
                        final MediaSource textSource =
                                new SingleSampleMediaSource.Factory(new CustomDataSourceFactory(context, null, subtitle.getContent().getBytes()))
                                        .createMediaSource(textMediaItem, C.TIME_UNSET);
                        mediaSources.add(textSource);
                        continue;
                    }
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

    public void setSelectedStream(@NonNull final VideoStream selectedStream) {
        selectedResolution = selectedStream.getResolution();
        selectedCodec = selectedStream.getCodec();
    }

    public void addBlacklistUrl(@NonNull final String url) {
        blacklistUrls.add(url);
    }

    public List<String> getBlacklistUrls() {
        return blacklistUrls;
    }

    @Nullable
    public String getAudioTrack() {
        return audioTrack;
    }

    public void setAudioTrack(@Nullable final String audioTrack) {
        this.audioTrack = audioTrack;
    }
}
