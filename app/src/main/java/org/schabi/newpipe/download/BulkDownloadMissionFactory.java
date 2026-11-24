package org.schabi.newpipe.download;

import static org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP;
import static org.schabi.newpipe.util.ListHelper.getStreamsOfSpecifiedDelivery;

import android.content.Context;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.get.DownloadRequestHeaders;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;

final class BulkDownloadMissionFactory {
    enum MediaType {
        VIDEO,
        AUDIO
    }

    private BulkDownloadMissionFactory() {
    }

    static void enqueue(@NonNull final Context context,
                        @NonNull final StoredDirectoryHelper directory,
                        @NonNull final StreamInfo info,
                        @NonNull final MediaType mediaType,
                        final int threads,
                        final int position,
                        final int total,
                        final boolean addNumberPrefix) {
        enqueue(context, directory, info, mediaType, threads, position, total,
                addNumberPrefix, null, null);
    }

    static void enqueue(@NonNull final Context context,
                        @NonNull final StoredDirectoryHelper directory,
                        @NonNull final StreamInfo info,
                        @NonNull final MediaType mediaType,
                        final int threads, final int position, final int total,
                        final boolean addNumberPrefix, final Boolean automaticWifiOnly,
                        final String automaticProfile) {
        final MissionSelection selection = mediaType == MediaType.AUDIO
                ? selectAudio(context, info) : selectVideo(context, info);

        if (!directory.mkdirs()) {
            throw new IllegalStateException("Unable to create the download directory");
        }

        final String title = numberedTitle(info.getName(), position, total, addNumberPrefix);
        final String filename = FilenameUtils.createFilename(context, title)
                + "." + selection.suffix;
        final StoredFileHelper storage = directory.createUniqueFile(filename, selection.mimeType);
        if (storage == null || !storage.canWrite()) {
            throw new IllegalStateException("Unable to create " + filename);
        }

        final String[] urls;
        final ArrayList<MissionRecoveryInfo> recoveryInfo = new ArrayList<>();
        urls = selection.secondaryStream == null
                ? new String[]{selection.primaryStream.getContent()}
                : new String[]{selection.primaryStream.getContent(),
                        selection.secondaryStream.getContent()};
        recoveryInfo.add(new MissionRecoveryInfo(selection.primaryStream));
        if (selection.secondaryStream != null) {
            recoveryInfo.add(new MissionRecoveryInfo(selection.secondaryStream));
        }

        DownloadManagerService.startMission(context, urls, storage, selection.kind, threads,
                info, selection.postprocessingName, selection.postprocessingArguments,
                0, recoveryInfo, automaticWifiOnly, automaticProfile,
                selection.secondaryStream == null
                        ? DownloadRequestHeaders.capture(selection.primaryStream)
                        : DownloadRequestHeaders.capture(selection.primaryStream,
                                selection.secondaryStream));
    }

    @NonNull
    static String numberedTitle(@NonNull final String title,
                                final int position,
                                final int total,
                                final boolean addNumberPrefix) {
        if (!addNumberPrefix) {
            return title;
        }
        final int width = Math.max(2, String.valueOf(Math.max(total, 1)).length());
        return String.format(Locale.ROOT, "%0" + width + "d - %s", position, title);
    }

    @NonNull
    private static MissionSelection selectAudio(@NonNull final Context context,
                                                @NonNull final StreamInfo info) {
        final List<AudioStream> streams = defaultAudioTrack(context, info);
        if (streams.isEmpty()) {
            throw new IllegalStateException("No downloadable audio stream is available");
        }
        final int selectedIndex = ListHelper.getDefaultAudioFormat(context, streams);
        if (selectedIndex < 0 || selectedIndex >= streams.size()) {
            throw new IllegalStateException("No default audio format is available");
        }

        final AudioStream selected = streams.get(selectedIndex);
        final MediaFormat format = selected.getFormat();
        if (format == null) {
            throw new IllegalStateException("The selected audio format is unknown");
        }

        if (format == MediaFormat.WEBMA_OPUS) {
            return new MissionSelection(selected, null, 'a', "audio/ogg", "opus",
                    Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER, null);
        }
        return new MissionSelection(selected, null, 'a', format.mimeType, format.getSuffix(),
                format == MediaFormat.M4A ? Postprocessing.ALGORITHM_M4A_NO_DASH : null,
                null);
    }

    @NonNull
    private static MissionSelection selectVideo(@NonNull final Context context,
                                                @NonNull final StreamInfo info) {
        final List<List<AudioStream>> groupedAudioStreams = ListHelper.getGroupedAudioStreams(
                context, getStreamsOfSpecifiedDelivery(info.getAudioStreams(), PROGRESSIVE_HTTP));
        final List<AudioStream> audioStreams = selectedAudioTrack(context, groupedAudioStreams);
        final List<VideoStream> videoStreams = ListHelper.getSortedStreamVideosList(
                context,
                getStreamsOfSpecifiedDelivery(info.getVideoStreams(), PROGRESSIVE_HTTP),
                getStreamsOfSpecifiedDelivery(info.getVideoOnlyStreams(), PROGRESSIVE_HTTP),
                false,
                groupedAudioStreams.size() > 1);
        if (videoStreams.isEmpty()) {
            throw new IllegalStateException("No downloadable video stream is available");
        }

        final int selectedIndex = ListHelper.getDefaultResolutionIndex(context, videoStreams);
        if (selectedIndex < 0 || selectedIndex >= videoStreams.size()) {
            throw new IllegalStateException("No default video resolution is available");
        }

        final VideoStream selected = videoStreams.get(selectedIndex);
        final MediaFormat format = selected.getFormat();
        if (format == null) {
            throw new IllegalStateException("The selected video format is unknown");
        }

        Stream secondary = null;
        String postprocessing = null;
        if (selected.isVideoOnly()) {
            secondary = SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, selected);
            if (secondary != null) {
                postprocessing = format == MediaFormat.MPEG_4
                        ? Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER
                        : Postprocessing.ALGORITHM_WEBM_MUXER;
            }
        }

        return new MissionSelection(selected, secondary, 'v', format.mimeType,
                format.getSuffix(), postprocessing, null);
    }

    @NonNull
    private static List<AudioStream> defaultAudioTrack(@NonNull final Context context,
                                                       @NonNull final StreamInfo info) {
        return selectedAudioTrack(context, ListHelper.getGroupedAudioStreams(context,
                getStreamsOfSpecifiedDelivery(info.getAudioStreams(), PROGRESSIVE_HTTP)));
    }

    @NonNull
    private static List<AudioStream> selectedAudioTrack(
            @NonNull final Context context,
            @NonNull final List<List<AudioStream>> groupedAudioStreams) {
        final int trackIndex = ListHelper.getDefaultAudioTrackGroup(context, groupedAudioStreams);
        if (trackIndex < 0 || trackIndex >= groupedAudioStreams.size()) {
            return List.of();
        }
        return groupedAudioStreams.get(trackIndex);
    }

    private static final class MissionSelection {
        @NonNull
        private final Stream primaryStream;
        private final Stream secondaryStream;
        private final char kind;
        @NonNull
        private final String mimeType;
        @NonNull
        private final String suffix;
        private final String postprocessingName;
        private final String[] postprocessingArguments;

        private MissionSelection(@NonNull final Stream primaryStream,
                                 final Stream secondaryStream,
                                 final char kind,
                                 @NonNull final String mimeType,
                                 @NonNull final String suffix,
                                 final String postprocessingName,
                                 final String[] postprocessingArguments) {
            this.primaryStream = primaryStream;
            this.secondaryStream = secondaryStream;
            this.kind = kind;
            this.mimeType = mimeType;
            this.suffix = suffix;
            this.postprocessingName = postprocessingName;
            this.postprocessingArguments = postprocessingArguments;
        }
    }
}
