package org.schabi.newpipe.util;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
//import com.yausername.youtubedl_android.YoutubeDL;
//import com.yausername.youtubedl_android.YoutubeDLRequest;
//import com.yausername.youtubedl_android.mapper.VideoFormat;
//import com.yausername.youtubedl_android.mapper.VideoInfo;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.schabi.newpipe.extractor.localization.Localization.getEnglishName;

public class YtdlpHelper {
    private static final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    public static String cookieFile;
    public static boolean noCheckCert = false;
    public static StreamInfo getFallbackStreams(final String url) throws IOException, ExtractionException {
        ReentrantLock lock = locks.computeIfAbsent(url, k -> new ReentrantLock());
        return new StreamInfo();
//        try {
//            StreamInfo streamInfo;
//            System.out.println("Getting fallback streams for " + url);
//
//            YoutubeDLRequest request = new YoutubeDLRequest(url);
//            String config = ServiceList.YouTube.getYtdlpConfig() == null ? "": ServiceList.YouTube.getYtdlpConfig();
//            request.addOption("--extractor-args", "youtube:player_client=default,-ios;player_skip=webpage;" + config);
//            request.addOption("--cookies", cookieFile);
//            if (noCheckCert) {
//                request.addOption("--no-check-certificate");
//            }
//            VideoInfo originStreamInfo = YoutubeDL.getInstance().getInfo(request);
//            streamInfo = parseInfo(originStreamInfo, url);
//
//            System.out.println("Successfully got fallback streams for " + url);
//            return streamInfo;
//        } catch (InterruptedException | YoutubeDL.CanceledException e) {
//            throw new IOException(e);
//        }  catch (Exception e) {
//            if (e.getMessage() == null) {
//                throw new ExtractionException(e);
//            }
//            if (e.getMessage().contains("Sign in")) {
//                throw new NotLoginException(e);
//            } else if (e.getMessage().contains("in your country")) {
//                throw new GeographicRestrictionException(e.getMessage());
//            } else if (e.getMessage().contains("private")) {
//                throw new PrivateContentException(e.getMessage());
//            }
//            throw new ExtractionException(e);
//        } finally {
//            lock.unlock(); // Release the lock
//            // Clean up the lock if it's no longer needed
//            locks.remove(url, lock);
//        }
    }

//    public static StreamInfo parseInfo(VideoInfo originStreamInfo, String url) throws ParsingException {
//        StreamInfo streamInfo = new StreamInfo(ServiceList.YouTube.getServiceId(), ServiceList.YouTube.getStreamLHFactory().getId(url), url, "");
//        ArrayList<AudioStream> audioStreams = new ArrayList<>();
//        ArrayList<VideoStream> videoStreams = new ArrayList<>();
//        ArrayList<VideoStream> videoOnlyStreams = new ArrayList<>();
//
//        String contentLanguage = getEnglishName(ServiceList.YouTube.getAudioLanguage());
//        boolean hasContentLanguage = false;
//        boolean hasOriginal = false;
//        for(VideoFormat videoFormat : originStreamInfo.getFormats()) {
//            if (Objects.equals(videoFormat.getVcodec(), "none") && !Objects.equals(videoFormat.getAcodec(), "none")) {
//                if (videoFormat.getFormatNote().contains(contentLanguage)) {
//                    hasContentLanguage = true;
//                }
//                if (videoFormat.getFormatNote().contains("original")) {
//                    hasOriginal = true;
//                }
//            }
//        }
//
//        for(VideoFormat videoFormat : originStreamInfo.getFormats()) {
//            if (Objects.equals(videoFormat.getVcodec(), "none") && Objects.equals(videoFormat.getAcodec(), "none")) {
//                continue;
//            }
//            if (Objects.equals(videoFormat.getVcodec(), "none")) {
//                if (hasContentLanguage) {
//                    if (!videoFormat.getFormatNote().contains(contentLanguage)) {
//                        continue;
//                    }
//                } else {
//                    if (hasOriginal) {
//                        if (!videoFormat.getFormatNote().contains("original")) {
//                            continue;
//                        }
//                    }
//
//                }
//                MediaFormat format = videoFormat.getAcodec().equals("opus")?MediaFormat.WEBMA_OPUS:MediaFormat.getFromSuffix(videoFormat.getExt());
//                ItagItem itag = new ItagItem(Integer.parseInt(videoFormat.getFormatId().split("-")[0]), ItagItem.ItagType.AUDIO,
//                       format, videoFormat.getTbr());
//                itag.setCodec(videoFormat.getAcodec());
//                itag.setBitrate(videoFormat.getTbr());
//                itag.setSampleRate(videoFormat.getAsr());
//                try {
//                    itag.setInitStart((int) videoFormat.getInitRange().getBegin());
//                    itag.setInitEnd((int) videoFormat.getInitRange().getEnd());
//                    itag.setIndexStart((int) videoFormat.getIndexRange().getBegin());
//                    itag.setIndexEnd((int) videoFormat.getIndexRange().getEnd());
//                } catch (Exception e) {
//                    itag.setInitStart(-1);
//                    itag.setInitEnd(-1);
//                    itag.setIndexStart(-1);
//                    itag.setIndexEnd(-1);
//                    e.printStackTrace();
//                }
//
//                audioStreams.add(new AudioStream.Builder().setId(originStreamInfo.getId() + UUID.randomUUID().toString().replaceAll("[^a-zA-Z]", ""))
//                        .setContent(videoFormat.getUrl() + "&pppid=" + originStreamInfo.getId(), true)
//                        .setItagItem(itag)
//                        .setMediaFormat(format).setAverageBitrate(videoFormat.getTbr()).build());
//                Collections.sort(audioStreams, Comparator.comparingInt(AudioStream::getBitrate).reversed());
//            } else if (Objects.equals(videoFormat.getAcodec(), "none")) {
//                ItagItem itag = new ItagItem(Integer.parseInt(videoFormat.getFormatId().split("-")[0]), ItagItem.ItagType.VIDEO_ONLY, MediaFormat.getFromSuffix(videoFormat.getExt()), videoFormat.getFormatNote(), videoFormat.getFps());
//                itag.setCodec(videoFormat.getVcodec());
//                itag.setBitrate(videoFormat.getTbr());
//                itag.setWidth(videoFormat.getWidth());
//                itag.setHeight(videoFormat.getHeight());
//                try {
//                    itag.setInitStart((int) videoFormat.getInitRange().getBegin());
//                    itag.setInitEnd((int) videoFormat.getInitRange().getEnd());
//                    itag.setIndexStart((int) videoFormat.getIndexRange().getBegin());
//                    itag.setIndexEnd((int) videoFormat.getIndexRange().getEnd());
//                } catch (Exception e) {
//                    itag.setInitStart(-1);
//                    itag.setInitEnd(-1);
//                    itag.setIndexStart(-1);
//                    itag.setIndexEnd(-1);
//                    e.printStackTrace();
//                }
//                String resolution = videoFormat.getFormatNote();
//                if (resolution == null) {
//                    resolution = videoFormat.getHeight() + "p";
//                }
//                videoOnlyStreams.add(new VideoStream.Builder().setContent(videoFormat.getUrl() + "&pppid=" + originStreamInfo.getId(), true)
//                        .setMediaFormat(MediaFormat.getFromSuffix(videoFormat.getExt())).setId(originStreamInfo.getId())
//                        .setItagItem(itag)
//                        .setIsVideoOnly(true).setResolution(resolution).build());
//            } else {
//                ItagItem itag = new ItagItem(Integer.parseInt(videoFormat.getFormatId().split("-")[0]), ItagItem.ItagType.VIDEO_ONLY, MediaFormat.getFromSuffix(videoFormat.getExt()), videoFormat.getFormatNote(), videoFormat.getFps());
//                itag.setCodec(videoFormat.getVcodec());
//                itag.setBitrate(videoFormat.getTbr());
//                itag.setWidth(videoFormat.getWidth());
//                itag.setHeight(videoFormat.getHeight());
//                try {
//                    itag.setInitStart((int) videoFormat.getInitRange().getBegin());
//                    itag.setInitEnd((int) videoFormat.getInitRange().getEnd());
//                    itag.setIndexStart((int) videoFormat.getIndexRange().getBegin());
//                    itag.setIndexEnd((int) videoFormat.getIndexRange().getEnd());
//                } catch (Exception e) {
//                    itag.setInitStart(-1);
//                    itag.setInitEnd(-1);
//                    itag.setIndexStart(-1);
//                    itag.setIndexEnd(-1);
//                    e.printStackTrace();
//                }
//
//                String resolution = videoFormat.getFormatNote();
//                if (resolution == null) {
//                    resolution = videoFormat.getHeight() + "p";
//                }
//                videoStreams.add(new VideoStream.Builder().setContent(videoFormat.getUrl() + "&pppid=" + originStreamInfo.getId(), true)
//                        .setMediaFormat(MediaFormat.getFromSuffix(videoFormat.getExt())).setId(originStreamInfo.getId())
//                        .setItagItem(itag)
//                        .setIsVideoOnly(false).setResolution(resolution).build());
//            }
//        }
//        streamInfo.setSupportComments(true);
//        if (audioStreams.size() > 0 && videoStreams.size() == 0 && videoOnlyStreams.size() == 0) {
//            streamInfo.setStreamType(StreamType.AUDIO_STREAM);
//        } else {
//            streamInfo.setStreamType(StreamType.VIDEO_STREAM);
//            if (streamInfo.getService() == ServiceList.YouTube && videoOnlyStreams.isEmpty()) {
//                streamInfo.setStreamType(StreamType.LIVE_STREAM);
//                streamInfo.setHlsUrl(videoStreams.get(videoStreams.size() - 1).getContent());
//                streamInfo.setSupportComments(false);
//            }
//        }
//        streamInfo.setName(originStreamInfo.getTitle());
//        streamInfo.setAgeLimit(0);
//        streamInfo.setThumbnailUrl(originStreamInfo.getThumbnail());
//        streamInfo.setDuration(originStreamInfo.getDuration());
//        streamInfo.setUploaderName(originStreamInfo.getUploader());
//        streamInfo.setUploaderUrl("https://www.youtube.com/" + originStreamInfo.getUploaderId());
//        streamInfo.setUploaderAvatarUrl(originStreamInfo.getChannelAvatar());
//        streamInfo.setUploaderSubscriberCount(originStreamInfo.getChannelFollowerCount());
//        streamInfo.setUploaderVerified(originStreamInfo.getChannelIsVerified());
//        try {
//            streamInfo.setMetaInfo(YoutubeParsingHelper.getMetaInfo(JsonParser.array().from(originStreamInfo.getContentsRaw())));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        try {
//            YoutubeStreamExtractor streamExtractor = (YoutubeStreamExtractor) ServiceList.YouTube.getStreamExtractor(url);
//            streamInfo.setRelatedItems(streamExtractor.getRelatedItemsFromResults(JsonParser.array().from(originStreamInfo.getRelatedItemsRaw())).getItems());
//            streamInfo.setSupportRelatedItems(true);
//        }  catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        streamInfo.setDescription(new Description(originStreamInfo.getDescription(), Description.PLAIN_TEXT));
//        streamInfo.setTags(originStreamInfo.getTags());
//        streamInfo.setCategory(originStreamInfo.getCategories() == null ? "" : originStreamInfo.getCategories().get(0));
//        streamInfo.setLikeCount(Utils.parseLong(originStreamInfo.getLikeCount()));
//        streamInfo.setDislikeCount(Utils.parseLong(originStreamInfo.getDislikeCount()));
//        streamInfo.setUploadDate(new DateWrapper(LocalDate.parse(originStreamInfo.getUploadDate(), DateTimeFormatter.BASIC_ISO_DATE)
//                .atStartOfDay()
//                .atOffset(ZoneOffset.UTC)));
//        streamInfo.setViewCount(Utils.parseLong(originStreamInfo.getViewCount()));
//        streamInfo.setAudioStreams(audioStreams);
//        streamInfo.setVideoStreams(videoStreams);
//        streamInfo.setVideoOnlyStreams(videoOnlyStreams);
//        return streamInfo;
//    }
}
