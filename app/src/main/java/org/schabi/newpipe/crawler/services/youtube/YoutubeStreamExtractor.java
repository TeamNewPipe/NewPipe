package org.schabi.newpipe.crawler.services.youtube;

import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.Downloader;
import org.schabi.newpipe.crawler.Parser;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.VideoUrlIdHandler;
import org.schabi.newpipe.crawler.StreamExtractor;
import org.schabi.newpipe.crawler.MediaFormat;
import org.schabi.newpipe.crawler.VideoInfo;
import org.schabi.newpipe.crawler.VideoPreviewInfo;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractor.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class YoutubeStreamExtractor implements StreamExtractor {

    public enum ItagType {
        AUDIO,
        VIDEO,
        VIDEO_ONLY
    }

    private static class ItagItem {
        public ItagItem(int id, ItagType type, MediaFormat format, String res, int fps) {
            this.id = id;
            this.itagType = type;
            this.mediaFormatId = format.id;
            this.resolutionString = res;
            this.fps = fps;
        }
        public ItagItem(int id, ItagType type, MediaFormat format, int samplingRate, int bandWidth) {
            this.id = id;
            this.itagType = type;
            this.mediaFormatId = format.id;
            this.samplingRate = samplingRate;
            this.bandWidth = bandWidth;
        }
        public int id;
        public ItagType itagType;
        public int mediaFormatId;
        public String resolutionString = null;
        public int fps = -1;
        public int samplingRate = -1;
        public int bandWidth = -1;
    }

    private static final ItagItem[] itagList = {
            // video streams
            //           id, ItagType,       MediaFormat,    Resolution, fps
            new ItagItem(17, ItagType.VIDEO, MediaFormat.v3GPP, "144p", 12),
            new ItagItem(18, ItagType.VIDEO, MediaFormat.MPEG_4, "360p", 24),
            new ItagItem(22, ItagType.VIDEO, MediaFormat.MPEG_4, "720p", 24),
            new ItagItem(36, ItagType.VIDEO, MediaFormat.v3GPP, "240p", 24),
            new ItagItem(37, ItagType.VIDEO, MediaFormat.MPEG_4, "1080p", 24),
            new ItagItem(38, ItagType.VIDEO, MediaFormat.MPEG_4, "1080p", 24),
            new ItagItem(43, ItagType.VIDEO, MediaFormat.WEBM, "360p", 24),
            new ItagItem(44, ItagType.VIDEO, MediaFormat.WEBM, "480p", 24),
            new ItagItem(45, ItagType.VIDEO, MediaFormat.WEBM, "720p", 24),
            new ItagItem(46, ItagType.VIDEO, MediaFormat.WEBM, "1080p", 24),
            // audio streams
            //           id, ItagType,       MediaFormat,    samplingR, bandwidth
            new ItagItem(249, ItagType.AUDIO, MediaFormat.WEBMA, 0, 0),  // bandwith/samplingR 0 because not known
            new ItagItem(250, ItagType.AUDIO, MediaFormat.WEBMA, 0, 0),
            new ItagItem(171, ItagType.AUDIO, MediaFormat.WEBMA, 0, 0),
            new ItagItem(140, ItagType.AUDIO, MediaFormat.M4A, 0, 0),
            new ItagItem(251, ItagType.AUDIO, MediaFormat.WEBMA, 0, 0),
            // video only streams
            new ItagItem(160, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "144p", 24),
            new ItagItem(133, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "240p", 24),
            new ItagItem(134, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "360p", 24),
            new ItagItem(135, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "480p", 24),
            new ItagItem(136, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "720p", 24),
            new ItagItem(137, ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "1080p", 24),
    };

    /**These lists only contain itag formats that are supported by the common Android Video player.
     However if you are looking for a list showing all itag formats, look at
     https://github.com/rg3/youtube-dl/issues/1687 */

    public static boolean itagIsSupported(int itag) {
        for(ItagItem item : itagList) {
            if(itag == item.id) {
                return true;
            }
        }
        return false;
    }

    public static ItagItem getItagItem(int itag) throws ParsingException {
        for(ItagItem item : itagList) {
            if(itag == item.id) {
                return item;
            }
        }
        throw new ParsingException("itag=" + Integer.toString(itag) + " not supported");
    }

    // Sometimes if the html page of youtube is already downloaded, youtube web page will internally
    // download the /get_video_info page. Since a certain date dashmpd url is only available over
    // this /get_video_info page, so we always need to download this one to.
    // %%video_id%% will be replaced by the actual video id
    // $$el_type$$ will be replaced by the actual el_type (se the declarations below)
    private static final String GET_VIDEO_INFO_URL =
            "https://www.youtube.com/get_video_info?video_id=%%video_id%%$$el_type$$&ps=default&eurl=&gl=US&hl=en";
    // eltype is nececeary for the url aboth
    private static final String EL_INFO = "el=info";

    public class DecryptException extends ParsingException {
        DecryptException(Throwable cause) {
            super(cause);
        }
        DecryptException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // special content not available exceptions

    public class GemaException extends ContentNotAvailableException {
        GemaException(String message) {
            super(message);
        }
    }

    // ----------------

    private static final String TAG = YoutubeStreamExtractor.class.toString();
    private final Document doc;
    private JSONObject playerArgs;
    private Map<String, String> videoInfoPage;

    // static values
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    // cached values
    private static volatile String decryptionCode = "";

    VideoUrlIdHandler urlidhandler = new YoutubeVideoUrlIdHandler();
    String pageUrl = "";

    private Downloader downloader;

    public YoutubeStreamExtractor(String pageUrl, Downloader dl) throws CrawlingException, IOException {
        //most common videoInfo fields are now set in our superclass, for all services
        downloader = dl;
        this.pageUrl = pageUrl;
        String pageContent = downloader.download(urlidhandler.cleanUrl(pageUrl));
        doc = Jsoup.parse(pageContent, pageUrl);
        String ytPlayerConfigRaw;
        JSONObject ytPlayerConfig;

        //attempt to load the youtube js player JSON arguments
        try {
            ytPlayerConfigRaw =
                    Parser.matchGroup1("ytplayer.config\\s*=\\s*(\\{.*?\\});", pageContent);
            ytPlayerConfig = new JSONObject(ytPlayerConfigRaw);
            playerArgs = ytPlayerConfig.getJSONObject("args");
        } catch (Parser.RegexException e) {
            String errorReason = findErrorReason(doc);
            switch(errorReason) {
                case "GEMA":
                    throw new GemaException(errorReason);
                case "":
                    throw new ParsingException("player config empty", e);
                default:
                    throw new ContentNotAvailableException("Content not available", e);
            }
        } catch (JSONException e) {
            throw new ParsingException("Could not parse yt player config");
        }


        // get videoInfo page
        try {
            //Parser.unescapeEntities(url_data_str, true).split("&")
            String getVideoInfoUrl = GET_VIDEO_INFO_URL.replace("%%video_id%%",
                    urlidhandler.getVideoId(pageUrl)).replace("$$el_type$$", "&" + EL_INFO);
            videoInfoPage = Parser.compatParseMap(downloader.download(getVideoInfoUrl));
        } catch(Exception e) {
            throw new ParsingException("Could not load video info page.", e);
        }

        //----------------------------------
        // load and parse description code, if it isn't already initialised
        //----------------------------------
        if (decryptionCode.isEmpty()) {
            try {
                // The Youtube service needs to be initialized by downloading the
                // js-Youtube-player. This is done in order to get the algorithm
                // for decrypting cryptic signatures inside certain stream urls.
                JSONObject ytAssets = ytPlayerConfig.getJSONObject("assets");
                String playerUrl = ytAssets.getString("js");

                if (playerUrl.startsWith("//")) {
                    playerUrl = "https:" + playerUrl;
                }
                decryptionCode = loadDecryptionCode(playerUrl);
            } catch (JSONException e) {
                throw new ParsingException(
                        "Could not load decryption code for the Youtube service.", e);
            }
        }
    }

    @Override
    public String getTitle() throws ParsingException {
        try {//json player args method
            return playerArgs.getString("title");
        } catch(JSONException je) {//html <meta> method
            je.printStackTrace();
            Log.w(TAG, "failed to load title from JSON args; trying to extract it from HTML");
            try { // fall through to fall-back
                return doc.select("meta[name=title]").attr("content");
            } catch (Exception e) {
                throw new ParsingException("failed permanently to load title.", e);
            }
        }
    }

    @Override
    public String getDescription() throws ParsingException {
        try {
            return doc.select("p[id=\"eow-description\"]").first().html();
        } catch (Exception e) {//todo: add fallback method <-- there is no ... as long as i know
            throw new ParsingException("failed to load description.", e);
        }
    }

    @Override
    public String getUploader() throws ParsingException {
        try {//json player args method
            return playerArgs.getString("author");
        } catch(JSONException je) {
            je.printStackTrace();
            Log.w(TAG,
                    "failed to load uploader name from JSON args; trying to extract it from HTML");
        } try {//fall through to fallback HTML method
            return doc.select("div.yt-user-info").first().text();
        } catch (Exception e) {
            throw new ParsingException("failed permanently to load uploader name.", e);
        }
    }

    @Override
    public int getLength() throws ParsingException {
        try {
            return playerArgs.getInt("length_seconds");
        } catch (JSONException e) {//todo: find fallback method
            throw new ParsingException("failed to load video duration from JSON args", e);
        }
    }

    @Override
    public long getViews() throws ParsingException {
        try {
            String viewCountString = doc.select("meta[itemprop=interactionCount]").attr("content");
            return Long.parseLong(viewCountString);
        } catch (Exception e) {//todo: find fallback method
            throw new ParsingException("failed to number of views", e);
        }
    }

    @Override
    public String getUploadDate() throws ParsingException {
        try {
            return doc.select("meta[itemprop=datePublished]").attr("content");
        } catch (Exception e) {//todo: add fallback method
            throw new ParsingException("failed to get upload date.", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        //first attempt getting a small image version
        //in the html extracting part we try to get a thumbnail with a higher resolution
        // Try to get high resolution thumbnail if it fails use low res from the player instead
        try {
            return doc.select("link[itemprop=\"thumbnailUrl\"]").first().attr("abs:href");
        } catch(Exception e) {
            Log.w(TAG, "Could not find high res Thumbnail. Using low res instead");
        }
        try { //fall through to fallback
            return playerArgs.getString("thumbnail_url");
        } catch (JSONException je) {
            throw new ParsingException(
                    "failed to extract thumbnail URL from JSON args; trying to extract it from HTML", je);
        }
    }

    @Override
    public String getUploaderThumbnailUrl() throws ParsingException {
        try {
            return doc.select("a[class*=\"yt-user-photo\"]").first()
                    .select("img").first()
                    .attr("abs:data-thumb");
        } catch (Exception e) {//todo: add fallback method
            throw new ParsingException("failed to get uploader thumbnail URL.", e);
        }
    }

    @Override
    public String getDashMpdUrl() throws ParsingException {
        /*
        try {
            String dashManifest = playerArgs.getString("dashmpd");
            if(!dashManifest.contains("/signature/")) {
                String encryptedSig = Parser.matchGroup1("/s/([a-fA-F0-9\\.]+)", dashManifest);
                String decryptedSig;

                decryptedSig = decryptSignature(encryptedSig, decryptionCode);
                dashManifest = dashManifest.replace("/s/" + encryptedSig, "/signature/" + decryptedSig);
            }

            return dashManifest;
        } catch(JSONException je) {
            throw new ParsingException(
                    "Could not find \"dashmpd\" upon the player args (maybe no dash manifest available).", je);
        } catch (Exception e) {
            throw new ParsingException(e);
        }
        */
        try {
            String dashManifestUrl = videoInfoPage.get("dashmpd");
            if(!dashManifestUrl.contains("/signature/")) {
                String encryptedSig = Parser.matchGroup1("/s/([a-fA-F0-9\\.]+)", dashManifestUrl);
                String decryptedSig;

                decryptedSig = decryptSignature(encryptedSig, decryptionCode);
                dashManifestUrl = dashManifestUrl.replace("/s/" + encryptedSig, "/signature/" + decryptedSig);
            }
            return dashManifestUrl;
        } catch (Exception e) {
            throw new ParsingException(
                    "Could not get \"dashmpd\" maybe VideoInfoPage is broken.", e);
        }
    }


    @Override
    public List<VideoInfo.AudioStream> getAudioStreams() throws ParsingException {
        Vector<VideoInfo.AudioStream> audioStreams = new Vector<>();
        try{
            String encoded_url_map = playerArgs.getString("adaptive_fmts");
            for(String url_data_str : encoded_url_map.split(",")) {
                // This loop iterates through multiple streams, therefor tags
                // is related to one and the same stream at a time.
                Map<String, String> tags = Parser.compatParseMap(
                        org.jsoup.parser.Parser.unescapeEntities(url_data_str, true));

                int itag = Integer.parseInt(tags.get("itag"));

                if (itagIsSupported(itag)) {
                    ItagItem itagItem = getItagItem(itag);
                    if (itagItem.itagType == ItagType.AUDIO) {
                        String streamUrl = tags.get("url");
                        // if video has a signature: decrypt it and add it to the url
                        if (tags.get("s") != null) {
                            streamUrl = streamUrl + "&signature="
                                    + decryptSignature(tags.get("s"), decryptionCode);
                        }

                        audioStreams.add(new VideoInfo.AudioStream(streamUrl,
                                itagItem.mediaFormatId,
                                itagItem.bandWidth,
                                itagItem.samplingRate));
                    }
                }
            }
        } catch (Exception e) {
            throw new ParsingException("Could not get audiostreams", e);
        }
        return audioStreams;
    }

    @Override
    public List<VideoInfo.VideoStream> getVideoStreams() throws ParsingException {
        Vector<VideoInfo.VideoStream> videoStreams = new Vector<>();

        try{
            String encoded_url_map = playerArgs.getString("url_encoded_fmt_stream_map");
            for(String url_data_str : encoded_url_map.split(",")) {
                try {
                    // This loop iterates through multiple streams, therefor tags
                    // is related to one and the same stream at a time.
                    Map<String, String> tags = Parser.compatParseMap(
                            org.jsoup.parser.Parser.unescapeEntities(url_data_str, true));

                    int itag = Integer.parseInt(tags.get("itag"));

                    if (itagIsSupported(itag)) {
                        ItagItem itagItem = getItagItem(itag);
                        if(itagItem.itagType == ItagType.VIDEO) {
                            String streamUrl = tags.get("url");
                            // if video has a signature: decrypt it and add it to the url
                            if (tags.get("s") != null) {
                                streamUrl = streamUrl + "&signature="
                                        + decryptSignature(tags.get("s"), decryptionCode);
                            }
                            videoStreams.add(new VideoInfo.VideoStream(
                                    streamUrl,
                                    itagItem.mediaFormatId,
                                    itagItem.resolutionString));
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not get Video stream.");
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            throw new ParsingException("Failed to get video streams", e);
        }

        if(videoStreams.isEmpty()) {
            throw new ParsingException("Failed to get any video stream");
        }
        return videoStreams;
    }

    @Override
    public List<VideoInfo.VideoStream> getVideoOnlyStreams() throws ParsingException {
        return null;
    }

    /**Attempts to parse (and return) the offset to start playing the video from.
     * @return the offset (in seconds), or 0 if no timestamp is found.*/
    @Override
    public int getTimeStamp() throws ParsingException {
        String timeStamp;
        try {
            timeStamp = Parser.matchGroup1("((#|&|\\?)t=\\d{0,3}h?\\d{0,3}m?\\d{1,3}s?)", pageUrl);
        } catch (Parser.RegexException e) {
            // catch this instantly since an url does not necessarily have to have a time stamp

            // -2 because well the testing system will then know its the regex that failed :/
            // not good i know
            return -2;
        }

        if(!timeStamp.isEmpty()) {
            try {
                String secondsString = "";
                String minutesString = "";
                String hoursString = "";
                try {
                    secondsString = Parser.matchGroup1("(\\d{1,3})s", timeStamp);
                    minutesString = Parser.matchGroup1("(\\d{1,3})m", timeStamp);
                    hoursString = Parser.matchGroup1("(\\d{1,3})h", timeStamp);
                } catch (Exception e) {
                    //it could be that time is given in another method
                    if (secondsString.isEmpty() //if nothing was got,
                            && minutesString.isEmpty()//treat as unlabelled seconds
                            && hoursString.isEmpty()) {
                        secondsString = Parser.matchGroup1("t=(\\d{1,3})", timeStamp);
                    }
                }

                int seconds = (secondsString.isEmpty() ? 0 : Integer.parseInt(secondsString));
                int minutes = (minutesString.isEmpty() ? 0 : Integer.parseInt(minutesString));
                int hours = (hoursString.isEmpty() ? 0 : Integer.parseInt(hoursString));

                int ret = seconds + (60 * minutes) + (3600 * hours);//don't trust BODMAS!
                //Log.d(TAG, "derived timestamp value:"+ret);
                return ret;
                //the ordering varies internationally
            } catch (ParsingException e) {
                throw new ParsingException("Could not get timestamp.", e);
            }
        } else {
            return 0;
        }
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        // Not yet implemented.
        // Also you need to be logged in to see age restricted videos on youtube,
        // therefore NP is not able to receive such videos.
        return 0;
    }

    @Override
    public String getAverageRating() throws ParsingException {
        try {
            return playerArgs.getString("avg_rating");
        } catch (JSONException e) {
            throw new ParsingException("Could not get Average rating", e);
        }
    }

    @Override
    public int getLikeCount() throws ParsingException {
        String likesString = "";
        try {
            likesString = doc.select("button.like-button-renderer-like-button").first()
                    .select("span.yt-uix-button-content").first().text();
            return Integer.parseInt(likesString.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException nfe) {
            throw new ParsingException(
                    "failed to parse likesString \"" + likesString + "\" as integers", nfe);
        } catch (Exception e) {
            throw new ParsingException("Could not get like count", e);
        }
    }

    @Override
    public int getDislikeCount() throws ParsingException {
        String dislikesString = "";
        try {
            dislikesString = doc.select("button.like-button-renderer-dislike-button").first()
                    .select("span.yt-uix-button-content").first().text();
            return Integer.parseInt(dislikesString.replaceAll("[^\\d]", ""));
        } catch(NumberFormatException nfe) {
            throw new ParsingException(
                    "failed to parse dislikesString \"" + dislikesString + "\" as integers", nfe);
        } catch(Exception e) {
            throw new ParsingException("Could not get dislike count", e);
        }
    }

    @Override
    public VideoPreviewInfo getNextVideo() throws ParsingException {
        try {
            return extractVideoPreviewInfo(doc.select("div[class=\"watch-sidebar-section\"]").first()
                    .select("li").first());
        } catch(Exception e) {
            throw new ParsingException("Could not get next video", e);
        }
    }

    @Override
    public Vector<VideoPreviewInfo> getRelatedVideos() throws ParsingException {
        try {
            Vector<VideoPreviewInfo> relatedVideos = new Vector<>();
            for (Element li : doc.select("ul[id=\"watch-related\"]").first().children()) {
                // first check if we have a playlist. If so leave them out
                if (li.select("a[class*=\"content-link\"]").first() != null) {
                    relatedVideos.add(extractVideoPreviewInfo(li));
                }
            }
            return relatedVideos;
        } catch(Exception e) {
            throw new ParsingException("Could not get related videos", e);
        }
    }

    @Override
    public VideoUrlIdHandler getUrlIdConverter() {
        return new YoutubeVideoUrlIdHandler();
    }

    @Override
    public String getPageUrl() {
        return pageUrl;
    }

    /**Provides information about links to other videos on the video page, such as related videos.
     * This is encapsulated in a VideoPreviewInfo object,
     * which is a subset of the fields in a full VideoInfo.*/
    private VideoPreviewInfo extractVideoPreviewInfo(Element li) throws ParsingException {
        VideoPreviewInfo info = new VideoPreviewInfo();

        try {
            info.webpage_url = li.select("a.content-link").first()
                    .attr("abs:href");

            info.id = Parser.matchGroup1("v=([0-9a-zA-Z-]*)", info.webpage_url);

            //todo: check NullPointerException causing
            info.title = li.select("span.title").first().text();
            //this page causes the NullPointerException, after finding it by searching for "tjvg":
            //https://www.youtube.com/watch?v=Uqg0aEhLFAg

            //this line is unused
            //String views = li.select("span.view-count").first().text();

            //Log.i(TAG, "title:"+info.title);
            //Log.i(TAG, "view count:"+views);

            try {
                info.view_count = Long.parseLong(li.select("span.view-count")
                        .first().text().replaceAll("[^\\d]", ""));
            } catch (NullPointerException e) {//related videos sometimes have no view count
                info.view_count = 0;
            }
            info.uploader = li.select("span.g-hovercard").first().text();

            info.duration = li.select("span.video-time").first().text();

            Element img = li.select("img").first();
            info.thumbnail_url = img.attr("abs:src");
            // Sometimes youtube sends links to gif files which somehow seem to not exist
            // anymore. Items with such gif also offer a secondary image source. So we are going
            // to use that if we caught such an item.
            if (info.thumbnail_url.contains(".gif")) {
                info.thumbnail_url = img.attr("data-thumb");
            }
            if (info.thumbnail_url.startsWith("//")) {
                info.thumbnail_url = "https:" + info.thumbnail_url;
            }
        } catch (Exception e) {
            throw new ParsingException(e);
        }
        return info;
    }

    private String loadDecryptionCode(String playerUrl) throws DecryptException {
        String decryptionFuncName;
        String decryptionFunc;
        String helperObjectName;
        String helperObject;
        String callerFunc = "function " + DECRYPTION_FUNC_NAME + "(a){return %%(a);}";
        String decryptionCode;

        try {
            String playerCode = downloader.download(playerUrl);

            decryptionFuncName =
                    Parser.matchGroup1("\\.sig\\|\\|([a-zA-Z0-9$]+)\\(", playerCode);

            String functionPattern = "("
                    + decryptionFuncName.replace("$", "\\$")
                    + "=function\\([a-zA-Z0-9_]*\\)\\{.+?\\})";
            decryptionFunc = "var " + Parser.matchGroup1(functionPattern, playerCode) + ";";

            helperObjectName = Parser
                    .matchGroup1(";([A-Za-z0-9_\\$]{2})\\...\\(", decryptionFunc);

            String helperPattern = "(var "
                    + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)";
            helperObject = Parser.matchGroup1(helperPattern, playerCode);


            callerFunc = callerFunc.replace("%%", decryptionFuncName);
            decryptionCode = helperObject + decryptionFunc + callerFunc;
        } catch(IOException ioe) {
            throw new DecryptException("Could not load decrypt function", ioe);
        } catch(Exception e) {
            throw new DecryptException("Could not parse decrypt function ", e);
        }

        return decryptionCode;
    }

    private String decryptSignature(String encryptedSig, String decryptionCode)
            throws DecryptException{
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        Object result = null;
        try {
            ScriptableObject scope = context.initStandardObjects();
            context.evaluateString(scope, decryptionCode, "decryptionCode", 1, null);
            Function decryptionFunc = (Function) scope.get("decrypt", scope);
            result = decryptionFunc.call(context, scope, scope, new Object[]{encryptedSig});
        } catch (Exception e) {
            throw new DecryptException(e);
        } finally {
            Context.exit();
        }
        return (result == null ? "" : result.toString());
    }

    private String findErrorReason(Document doc) {
        String errorMessage = doc.select("h1[id=\"unavailable-message\"]").first().text();
        if(errorMessage.contains("GEMA")) {
            // Gema sometimes blocks youtube music content in germany:
            // https://www.gema.de/en/
            // Detailed description:
            // https://en.wikipedia.org/wiki/GEMA_%28German_organization%29
            return "GEMA";
        }
        return "";
    }
}
