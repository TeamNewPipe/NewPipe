package org.schabi.newpipe.extractor;


import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.mapper.VideoFormat;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Localization;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class OtherStreamExtractor extends StreamExtractor {

    private VideoInfo info;

    public OtherStreamExtractor(StreamingService service, LinkHandler linkHandler, Localization localization) {
        super(service, linkHandler, localization);
    }

    @Nonnull
    @Override
    public String getUploadDate() throws ParsingException {
        assertPageFetched();
        if(null == info.uploadDate) return info.uploadDate;

        SimpleDateFormat formatterTarget = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat formatterSource = new SimpleDateFormat("yyyyMMdd");
        try {
            return formatterTarget.format(formatterSource.parse(info.uploadDate));
        } catch (ParseException e) {
            // hmm
        }
        return null;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        assertPageFetched();
        return info.thumbnail;
    }

    @Nonnull
    @Override
    public String getDescription() throws ParsingException {
        assertPageFetched();
        return info.description;
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        return 0;
    }

    @Override
    public long getLength() throws ParsingException {
        assertPageFetched();
        return info.duration;
    }

    @Override
    public long getTimeStamp() throws ParsingException {
        return 0;
    }

    @Override
    public long getViewCount() throws ParsingException {
        assertPageFetched();
        if(null != info.viewCount){
            return Long.parseLong(info.viewCount);
        }
        return -1;
    }

    @Override
    public long getLikeCount() throws ParsingException {
        assertPageFetched();
        if(null != info.likeCount){
            return Long.parseLong(info.likeCount);
        }
        return -1;
    }

    @Override
    public long getDislikeCount() throws ParsingException {
        assertPageFetched();
        if(null != info.dislikeCount){
            return Long.parseLong(info.dislikeCount);
        }
        return -1;
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        assertPageFetched();
        return info.uploader;
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() throws ParsingException {
        return null;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        assertPageFetched();
        return null;
    }

    @Override
    public List<AudioStream> getAudioStreams() throws IOException, ExtractionException {
        assertPageFetched();
        List<AudioStream> audioStreams = new ArrayList<>();
        for (VideoFormat f: info.formats){
            if("none".equals(f.vcodec) && f.acodec != null && !"none".equals(f.acodec)){
                MediaFormat format = null;
                if (f.acodec.contains("mp4a")) {
                    format = MediaFormat.M4A;
                } else if (f.acodec.contains("opus")) {
                    format = MediaFormat.OPUS;
                }
                if(format != null){
                    AudioStream audioStream = new AudioStream(f.url, format, f.abr);
                    if (!Stream.containSimilarStream(audioStream, audioStreams)) {
                        audioStreams.add(audioStream);
                    }
                }
            }
        }
        return audioStreams;
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        assertPageFetched();
        List<VideoStream> videoStreams = new ArrayList<>();
        for (VideoFormat f: info.formats){
            if("mp4".equals(f.ext) && !"none".equals(f.acodec)){
                int res = f.width > f.height ? f.width : f.height;
                MediaFormat format = f.url.contains("m3u8") ? MediaFormat.HLS : MediaFormat.MPEG_4;
                VideoStream videoStream = new VideoStream(f.url, format, String.valueOf(res) + "p");
                if (!Stream.containSimilarStream(videoStream, videoStreams)) {
                    videoStreams.add(videoStream);
                }
            }
        }
        return videoStreams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws IOException, ExtractionException {
        assertPageFetched();
        List<VideoStream> videoStreams = new ArrayList<>();
        for (VideoFormat f: info.formats){
            if("mp4".equals(f.ext) && "none".equals(f.acodec)){
                int res = f.width > f.height ? f.width : f.height;
                MediaFormat format = f.url.contains("m3u8") ? MediaFormat.HLS : MediaFormat.MPEG_4;
                VideoStream videoStream = new VideoStream(f.url, format, String.valueOf(res) + "p", true);
                if (!Stream.containSimilarStream(videoStream, videoStreams)) {
                    videoStreams.add(videoStream);
                }
            }
        }
        return videoStreams;
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitlesDefault() throws IOException, ExtractionException {
        return null;
    }

    @Nonnull
    @Override
    public List<SubtitlesStream> getSubtitles(MediaFormat format) throws IOException, ExtractionException {
        return null;
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        assertPageFetched();
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public StreamInfoItem getNextStream() throws IOException, ExtractionException {
        return null;
    }

    @Override
    public StreamInfoItemsCollector getRelatedStreams() throws IOException, ExtractionException {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public void onFetchPage(@Nonnull Downloader downloader) throws IOException, ExtractionException {
        try {
            info = YoutubeDL.getInstance().getInfo(getUrl());
        } catch (YoutubeDLException e) {
            throw new ExtractionException("unable to extract stream info", e);
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        return info.getTitle();
    }
}
