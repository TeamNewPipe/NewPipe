package org.schabi.newpipe.download;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Localization;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.StreamItemAdapter;

import java.util.List;
import java.util.Locale;

import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;

public interface IDownloadVideo {


    default void downloadSelected(Context context, String url, Stream selectedStream, String location, StreamInfo streamInfo,
                                  String fileName,
                                  char kind, int threads, StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter,
                                  StreamItemAdapter.StreamSizeWrapper<VideoStream> wrappedVideoStreams) {
        String[] urls;
        String psName = null;
        String[] psArgs = null;
        String secondaryStreamUrl = null;
        long nearLength = 0;

        if (fileName.isEmpty())
            fileName = FilenameUtils.createFilename(context, streamInfo.getName());

        if (String.valueOf(kind).equals(DownloadSetting.SETTING_SUBTITLES)) {
            fileName += ".srt";// final subtitle format
        } else {
            fileName += "." + selectedStream.getFormat().getSuffix();
        }

        final String finalFileName = fileName;

        if (selectedStream instanceof VideoStream) {
            SecondaryStreamHelper<AudioStream> secondaryStream = videoStreamsAdapter
                    .getAllSecondary()
                    .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

            if (secondaryStream != null) {
                secondaryStreamUrl = secondaryStream.getStream().getUrl();
                psName = selectedStream.getFormat() == MediaFormat.MPEG_4 ? Postprocessing.ALGORITHM_MP4_DASH_MUXER : Postprocessing.ALGORITHM_WEBM_MUXER;
                psArgs = null;
                long videoSize = wrappedVideoStreams.getSizeInBytes((VideoStream) selectedStream);

                // set nearLength, only, if both sizes are fetched or known. this probably does not work on weak internet connections
                if (secondaryStream.getSizeInBytes() > 0 && videoSize > 0) {
                    nearLength = secondaryStream.getSizeInBytes() + videoSize;
                }
            }
        } else if ((selectedStream instanceof SubtitlesStream) && selectedStream.getFormat() == MediaFormat.TTML) {
            psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
            psArgs = new String[]{
                    selectedStream.getFormat().getSuffix(),
                    "false",// ignore empty frames
                    "false",// detect youtube duplicate lines
            };
        }

        if (secondaryStreamUrl == null) {
            urls = new String[]{selectedStream.getUrl()};
        } else {
            urls = new String[]{selectedStream.getUrl(), secondaryStreamUrl};
        }

        DownloadManagerService.startMission(context, urls, location, finalFileName, kind, threads, url, psName, psArgs, nearLength);
    }

    default int getDefaultSubtitleStreamIndex(List<SubtitlesStream> streams) {
        Localization loc = NewPipe.getPreferredLocalization();

        for (int i = 0; i < streams.size(); i++) {
            Locale streamLocale = streams.get(i).getLocale();
            String tag = streamLocale.getLanguage().concat("-").concat(streamLocale.getCountry());
            if (tag.equalsIgnoreCase(loc.getLanguage())) {
                return i;
            }
        }

        // fallback
        // 1st loop match country & language
        // 2nd loop match language only
        int index = loc.getLanguage().indexOf("-");
        String lang = index > 0 ? loc.getLanguage().substring(0, index) : loc.getLanguage();

        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < streams.size(); i++) {
                Locale streamLocale = streams.get(i).getLocale();

                if (streamLocale.getLanguage().equalsIgnoreCase(lang)) {
                    if (j > 0 || streamLocale.getCountry().equalsIgnoreCase(loc.getCountry())) {
                        return i;
                    }
                }
            }
        }

        return 0;
    }

    default SparseArray<SecondaryStreamHelper<AudioStream>> getSecondaryStream(StreamItemAdapter.StreamSizeWrapper<VideoStream> wrappedVideoStreams,
                                                                               StreamItemAdapter.StreamSizeWrapper<AudioStream> wrappedAudioStreams) {
        SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams = new SparseArray<>(4);
        List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) continue;
            AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(wrappedAudioStreams.getStreamsList(), videoStreams.get(i));

            if (audioStream != null) {
                secondaryStreams.append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            }
        }
        return secondaryStreams;
    }
}
