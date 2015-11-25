package org.schabi.newpipe.services.youtube;

import android.util.Log;
import android.util.Xml;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.services.Extractor;
import org.schabi.newpipe.MediaFormat;
import org.schabi.newpipe.VideoInfo;
import org.schabi.newpipe.VideoPreviewInfo;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeExtractor.java is part of NewPipe.
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

public class YoutubeExtractor extends Extractor {

    private static final String TAG = YoutubeExtractor.class.toString();
    private String pageContents;
    private Document doc;
    private JSONObject jsonObj;
    private JSONObject playerArgs;

    // static values
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    // cached values
    private static volatile String decryptionCode = "";


    public YoutubeExtractor(String pageUrl) {
        super(pageUrl);//most common videoInfo fields are now set in our superclass, for all services
        pageContents = Downloader.download(cleanUrl(pageUrl));
        doc = Jsoup.parse(pageContents, pageUrl);

        //attempt to load the youtube js player JSON arguments
        try {
            String jsonString = matchGroup1("ytplayer.config\\s*=\\s*(\\{.*?\\});", pageContents);
            jsonObj = new JSONObject(jsonString);
            playerArgs = jsonObj.getJSONObject("args");

        } catch (Exception e) {//if this fails, the video is most likely not available.
            // Determining why is done later.
            videoInfo.videoAvailableStatus = VideoInfo.VIDEO_UNAVAILABLE;
            Log.d(TAG, "Could not load JSON data for Youtube video \""+pageUrl+"\". This most likely means the video is unavailable");
        }

        //----------------------------------
        // load and parse description code, if it isn't already initialised
        //----------------------------------
        if (decryptionCode.isEmpty()) {
            try {
            // The Youtube service needs to be initialized by downloading the
            // js-Youtube-player. This is done in order to get the algorithm
            // for decrypting cryptic signatures inside certain stream urls.
                JSONObject ytAssets = jsonObj.getJSONObject("assets");
                String playerUrl = ytAssets.getString("js");

                if (playerUrl.startsWith("//")) {
                    playerUrl = "https:" + playerUrl;
                }
                decryptionCode = loadDecryptionCode(playerUrl);
            } catch (Exception e){
                Log.d(TAG, "Could not load decryption code for the Youtube service.");
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getTitle() {
        try {//json player args method
            return playerArgs.getString("title");
        } catch(JSONException je) {//html <meta> method
            je.printStackTrace();
            Log.w(TAG, "failed to load title from JSON args; trying to extract it from HTML");
        } try { // fall through to fall-back
            return doc.select("meta[name=title]").attr("content");
        } catch (Exception e) {
            Log.e(TAG, "failed permanently to load title.");
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getDescription() {
        try {
            return doc.select("p[id=\"eow-description\"]").first().html();
        } catch (Exception e) {//todo: add fallback method
            Log.e(TAG, "failed to load description.");
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getUploader() {
        try {//json player args method
            return playerArgs.getString("author");
        } catch(JSONException je) {
            je.printStackTrace();
            Log.w(TAG, "failed to load uploader name from JSON args; trying to extract it from HTML");
        } try {//fall through to fallback HTML method
            return doc.select("div.yt-user-info").first().text();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "failed permanently to load uploader name.");
            return "";
        }
    }

    @Override
    public int getLength() {
        try {
            return playerArgs.getInt("length_seconds");
        } catch (JSONException je) {//todo: find fallback method
            Log.e(TAG, "failed to load video duration from JSON args");
            je.printStackTrace();
            return -1;
        }
    }

    @Override
    public int getViews() {
        try {
            String viewCountString = doc.select("meta[itemprop=interactionCount]").attr("content");
            return Integer.parseInt(viewCountString);
        } catch (Exception e) {//todo: find fallback method
            Log.e(TAG, "failed to number of views");
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public String getUploadDate() {
        try {
            return doc.select("meta[itemprop=datePublished]").attr("content");
        } catch (Exception e) {//todo: add fallback method
            Log.e(TAG, "failed to get upload date.");
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getThumbnailUrl() {
        //first attempt getting a small image version
        //in the html extracting part we try to get a thumbnail with a higher resolution
        // Try to get high resolution thumbnail if it fails use low res from the player instead
        try {
            return doc.select("link[itemprop=\"thumbnailUrl\"]").first().attr("abs:href");
        } catch(Exception e) {
            Log.w(TAG, "Could not find high res Thumbnail. Using low res instead");
            //fall through to fallback
        } try {
            return playerArgs.getString("thumbnail_url");
        } catch (JSONException je) {
            je.printStackTrace();
            Log.w(TAG, "failed to extract thumbnail URL from JSON args; trying to extract it from HTML");
            return "";
        }
    }

    @Override
    public String getUploaderThumbnailUrl() {
        try {
            return doc.select("a[class*=\"yt-user-photo\"]").first()
                    .select("img").first()
                    .attr("abs:data-thumb");
        } catch (Exception e) {//todo: add fallback method
            Log.e(TAG, "failed to get uploader thumbnail URL.");
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public VideoInfo.AudioStream[] getAudioStreams() {
        try {
            String dashManifest = playerArgs.getString("dashmpd");
            return parseDashManifest(dashManifest, decryptionCode);

        } catch (NullPointerException e) {
            Log.e(TAG, "Could not find \"dashmpd\" upon the player args (maybe no dash manifest available).");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new VideoInfo.AudioStream[0];
    }

    @Override
    public VideoInfo.VideoStream[] getVideoStreams() {
        try{
            //------------------------------------
            // extract video stream url
            //------------------------------------
            String encoded_url_map = playerArgs.getString("url_encoded_fmt_stream_map");
            Vector<VideoInfo.VideoStream> videoStreams = new Vector<>();
            for(String url_data_str : encoded_url_map.split(",")) {
                Map<String, String> tags = new HashMap<>();
                for(String raw_tag : Parser.unescapeEntities(url_data_str, true).split("&")) {
                    String[] split_tag = raw_tag.split("=");
                    tags.put(split_tag[0], split_tag[1]);
                }

                int itag = Integer.parseInt(tags.get("itag"));
                String streamUrl = URLDecoder.decode(tags.get("url"), "UTF-8");

                // if video has a signature: decrypt it and add it to the url
                if(tags.get("s") != null) {
                    streamUrl = streamUrl + "&signature=" + decryptSignature(tags.get("s"), decryptionCode);
                }

                if(resolveFormat(itag) != -1) {
                    videoStreams.add(new VideoInfo.VideoStream(
                            streamUrl,
                            resolveFormat(itag),
                            resolveResolutionString(itag)));
                }
            }
            return videoStreams.toArray(new VideoInfo.VideoStream[videoStreams.size()]);

        } catch (Exception e) {
            Log.e(TAG, "Failed to get video stream");
            e.printStackTrace();
            return new VideoInfo.VideoStream[0];
        }
    }

    /**These lists only contain itag formats that are supported by the common Android Video player.
    However if you are looking for a list showing all itag formats, look at
    https://github.com/rg3/youtube-dl/issues/1687 */
    public static int resolveFormat(int itag) {
        switch(itag) {
            // video
            case 17: return MediaFormat.v3GPP.id;
            case 18: return MediaFormat.MPEG_4.id;
            case 22: return MediaFormat.MPEG_4.id;
            case 36: return MediaFormat.v3GPP.id;
            case 37: return MediaFormat.MPEG_4.id;
            case 38: return MediaFormat.MPEG_4.id;
            case 43: return MediaFormat.WEBM.id;
            case 44: return MediaFormat.WEBM.id;
            case 45: return MediaFormat.WEBM.id;
            case 46: return MediaFormat.WEBM.id;
            default:
                //Log.i(TAG, "Itag " + Integer.toString(itag) + " not known or not supported.");
                return -1;
        }
    }

    public static String resolveResolutionString(int itag) {
        switch(itag) {
            case 17: return "144p";
            case 18: return "360p";
            case 22: return "720p";
            case 36: return "240p";
            case 37: return "1080p";
            case 38: return "1080p";
            case 43: return "360p";
            case 44: return "480p";
            case 45: return "720p";
            case 46: return "1080p";
            default:
                //Log.i(TAG, "Itag " + Integer.toString(itag) + " not known or not supported.");
                return null;
        }
    }

    @Override
    public String getVideoId(String url) {
        String id;
        String pat;

        if(url.contains("youtube")) {
            pat = "youtube\\.com/watch\\?v=([\\-a-zA-Z0-9_]{11})";
        }
        else if(url.contains("youtu.be")) {
            pat = "youtu\\.be/([a-zA-Z0-9_-]{11})";
        }
        else {
            Log.e(TAG, "Error could not parse url: " + url);
            return "";
        }
        id = matchGroup1(pat, url);
        if(!id.isEmpty()){
            Log.i(TAG, "string \""+url+"\" matches!");
            return id;
        }
        Log.i(TAG, "string \""+url+"\" does not match.");
        return "";
    }

    @Override
    public String getVideoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /**Attempts to parse (and return) the offset to start playing the video from.
     * @return the offset (in seconds), or 0 if no timestamp is found.*/
    @Override
    public int getTimeStamp(){
        String timeStamp = matchGroup1("((#|&)t=\\d{0,3}h?\\d{0,3}m?\\d{1,3}s?)", pageUrl);

        //TODO: test this
        if(!timeStamp.isEmpty()) {
            String secondsString = matchGroup1("(\\d{1,3})s", timeStamp);
            String minutesString = matchGroup1("(\\d{1,3})m", timeStamp);
            String hoursString = matchGroup1("(\\d{1,3})h", timeStamp);

            if(secondsString.isEmpty()//if nothing was got,
            && minutesString.isEmpty()//treat as unlabelled seconds
            && hoursString.isEmpty())
                secondsString = matchGroup1("t=(\\d{1,3})", timeStamp);

            int seconds = (secondsString.isEmpty() ? 0 : Integer.parseInt(secondsString));
            int minutes = (minutesString.isEmpty() ? 0 : Integer.parseInt(minutesString));
            int hours =   (hoursString.isEmpty()   ? 0 : Integer.parseInt(hoursString));

            int ret = seconds + (60*minutes) + (3600*hours);//don't trust BODMAS!
            Log.d(TAG, "derived timestamp value:"+ret);
            return ret;
            //the ordering varies internationally
        }//else, return default 0
        return 0;
    }

    @Override
    public VideoInfo getVideoInfo() {
        videoInfo = super.getVideoInfo();
        //todo: replace this with a call to getVideoId, if possible
        videoInfo.id = matchGroup1("v=([0-9a-zA-Z_-]{11})", pageUrl);

        videoInfo.age_limit = 0;

        //average rating
        try {
            videoInfo.average_rating = playerArgs.getString("avg_rating");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //---------------------------------------
        // extracting information from html page
        //---------------------------------------

        // Determine what went wrong when the Video is not available
        if(videoInfo.videoAvailableStatus == VideoInfo.VIDEO_UNAVAILABLE) {
            if(doc.select("h1[id=\"unavailable-message\"]").first().text().contains("GEMA")) {
                videoInfo.videoAvailableStatus = VideoInfo.VIDEO_UNAVAILABLE_GEMA;
            }
        }

        String likesString = "";
        String dislikesString = "";
        try {
            // likes
            likesString = doc.select("button.like-button-renderer-like-button").first()
                    .select("span.yt-uix-button-content").first().text();
            videoInfo.like_count = Integer.parseInt(likesString.replaceAll("[^\\d]", ""));
            // dislikes
            dislikesString = doc.select("button.like-button-renderer-dislike-button").first()
                            .select("span.yt-uix-button-content").first().text();

            videoInfo.dislike_count = Integer.parseInt(dislikesString.replaceAll("[^\\d]", ""));
        } catch(NumberFormatException nfe) {
            Log.e(TAG, "failed to parse likesString \""+likesString+"\" and dislikesString \""+
            dislikesString+"\" as integers");
        } catch(Exception e) {
            // if it fails we know that the video does not offer dislikes.
            e.printStackTrace();
            videoInfo.like_count = 0;
            videoInfo.dislike_count = 0;
        }

        // next video
        videoInfo.nextVideo = extractVideoPreviewInfo(doc.select("div[class=\"watch-sidebar-section\"]").first()
                .select("li").first());

        // related videos
        Vector<VideoPreviewInfo> relatedVideos = new Vector<>();
        for(Element li : doc.select("ul[id=\"watch-related\"]").first().children()) {
            // first check if we have a playlist. If so leave them out
            if(li.select("a[class*=\"content-link\"]").first() != null) {
                relatedVideos.add(extractVideoPreviewInfo(li));
            }
        }
        //todo: replace conversion
        videoInfo.relatedVideos = relatedVideos;
        //videoInfo.relatedVideos = relatedVideos.toArray(new VideoPreviewInfo[relatedVideos.size()]);
        return videoInfo;
    }


    private VideoInfo.AudioStream[] parseDashManifest(String dashManifest, String decryptoinCode) {
        if(!dashManifest.contains("/signature/")) {
            String encryptedSig = matchGroup1("/s/([a-fA-F0-9\\.]+)", dashManifest);
            String decryptedSig;

            decryptedSig = decryptSignature(encryptedSig, decryptoinCode);
            dashManifest = dashManifest.replace("/s/" + encryptedSig, "/signature/" + decryptedSig);
        }
        String dashDoc = Downloader.download(dashManifest);
        Vector<VideoInfo.AudioStream> audioStreams = new Vector<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(dashDoc));
            int eventType = parser.getEventType();
            String tagName = "";
            String currentMimeType = "";
            int currentBandwidth = -1;
            int currentSamplingRate = -1;
            boolean currentTagIsBaseUrl = false;
            while(eventType != XmlPullParser.END_DOCUMENT) {
                switch(eventType) {
                    case XmlPullParser.START_TAG:
                        tagName = parser.getName();
                        if(tagName.equals("AdaptationSet")) {
                            currentMimeType = parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "mimeType");
                        } else if(tagName.equals("Representation") && currentMimeType.contains("audio")) {
                            currentBandwidth = Integer.parseInt(
                                    parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "bandwidth"));
                            currentSamplingRate = Integer.parseInt(
                                    parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "audioSamplingRate"));
                        } else if(tagName.equals("BaseURL")) {
                            currentTagIsBaseUrl = true;
                        }

                        break;
                    case XmlPullParser.TEXT:
                        if(currentTagIsBaseUrl &&
                                (currentMimeType.contains("audio"))) {
                            int format = -1;
                            if(currentMimeType.equals(MediaFormat.WEBMA.mimeType)) {
                                format = MediaFormat.WEBMA.id;
                            } else if(currentMimeType.equals(MediaFormat.M4A.mimeType)) {
                                format = MediaFormat.M4A.id;
                            }
                            audioStreams.add(new VideoInfo.AudioStream(parser.getText(),
                                    format, currentBandwidth, currentSamplingRate));
                        }
                    case XmlPullParser.END_TAG:
                        if(tagName.equals("AdaptationSet")) {
                            currentMimeType = "";
                        } else if(tagName.equals("BaseURL")) {
                            currentTagIsBaseUrl = false;
                        }
                        break;
                    default:
                }
                eventType = parser.next();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return audioStreams.toArray(new VideoInfo.AudioStream[audioStreams.size()]);
    }
    /**Provides information about links to other videos on the video page, such as related videos.
     * This is encapsulated in a VideoPreviewInfo object,
     * which is a subset of the fields in a full VideoInfo.*/
    private VideoPreviewInfo extractVideoPreviewInfo(Element li) {
        VideoPreviewInfo info = new VideoPreviewInfo();
        info.webpage_url = li.select("a.content-link").first()
                .attr("abs:href");
        try {
            info.id = matchGroup1("v=([0-9a-zA-Z-]*)", info.webpage_url);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //todo: check NullPointerException causing
        info.title = li.select("span.title").first().text();
        //this page causes the NullPointerException, after finding it by searching for "tjvg":
        //https://www.youtube.com/watch?v=Uqg0aEhLFAg
        String views = li.select("span.view-count").first().text();
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
        if(info.thumbnail_url.contains(".gif")) {
            info.thumbnail_url = img.attr("data-thumb");
        }
        if(info.thumbnail_url.startsWith("//")) {
            info.thumbnail_url = "https:" + info.thumbnail_url;
        }
        return info;
    }

    private String loadDecryptionCode(String playerUrl) {
        String playerCode = Downloader.download(playerUrl);
        String decryptionFuncName = "";
        String decryptionFunc = "";
        String helperObjectName;
        String helperObject = "";
        String callerFunc = "function " + DECRYPTION_FUNC_NAME + "(a){return %%(a);}";
        String decryptionCode;

        try {
            decryptionFuncName = matchGroup1("\\.sig\\|\\|([a-zA-Z0-9$]+)\\(", playerCode);

            String functionPattern = "(var "+  decryptionFuncName.replace("$", "\\$") +"=function\\([a-zA-Z0-9_]*\\)\\{.+?\\})";
            decryptionFunc = matchGroup1(functionPattern, playerCode);
            decryptionFunc += ";";

            helperObjectName = matchGroup1(";([A-Za-z0-9_\\$]{2})\\...\\(", decryptionFunc);

            String helperPattern = "(var " + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)";
            helperObject = matchGroup1(helperPattern, playerCode);

        } catch (Exception e) {
            e.printStackTrace();
        }

        callerFunc = callerFunc.replace("%%", decryptionFuncName);
        decryptionCode = helperObject + decryptionFunc + callerFunc;

        return decryptionCode;
    }

    private String decryptSignature(String encryptedSig, String decryptionCode) {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        Object result = null;
        try {
            ScriptableObject scope = context.initStandardObjects();
            context.evaluateString(scope, decryptionCode, "decryptionCode", 1, null);
            Function decryptionFunc = (Function) scope.get("decrypt", scope);
            result = decryptionFunc.call(context, scope, scope, new Object[]{encryptedSig});
        } catch (Exception e) {
            e.printStackTrace();
        }
        Context.exit();
        return result.toString();
    }

    private String cleanUrl(String complexUrl) {
        return getVideoUrl(getVideoId(complexUrl));
    }

    private String matchGroup1(String pattern, String input) {
        Pattern pat = Pattern.compile(pattern);
        Matcher mat = pat.matcher(input);
        boolean foundMatch = mat.find();
        if (foundMatch) {
            return mat.group(1);
        }
        else {
            Log.w(TAG, "failed to find pattern \""+pattern+"\" inside of \""+input+"\"");
            new Exception("failed to find pattern \""+pattern+"\"").printStackTrace();
            return "";
        }

    }
}
