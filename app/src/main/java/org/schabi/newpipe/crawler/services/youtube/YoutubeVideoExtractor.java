package org.schabi.newpipe.crawler.services.youtube;

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
import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.Downloader;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.VideoExtractor;
import org.schabi.newpipe.crawler.MediaFormat;
import org.schabi.newpipe.crawler.VideoInfo;
import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeVideoExtractor.java is part of NewPipe.
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

public class YoutubeVideoExtractor extends VideoExtractor {

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

    private static final String TAG = YoutubeVideoExtractor.class.toString();
    private final Document doc;
    private JSONObject playerArgs;
    private String errorMessage = "";

    // static values
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    // cached values
    private static volatile String decryptionCode = "";

    private Downloader downloader;

    public YoutubeVideoExtractor(String pageUrl, Downloader dl) throws CrawlingException, IOException {
        //most common videoInfo fields are now set in our superclass, for all services
        super(pageUrl, dl);
        downloader = dl;
        String pageContent = downloader.download(cleanUrl(pageUrl));
        doc = Jsoup.parse(pageContent, pageUrl);
        String ytPlayerConfigRaw;
        JSONObject ytPlayerConfig;

        //attempt to load the youtube js player JSON arguments
        try {
            ytPlayerConfigRaw = matchGroup1("ytplayer.config\\s*=\\s*(\\{.*?\\});", pageContent);
            ytPlayerConfig = new JSONObject(ytPlayerConfigRaw);
            playerArgs = ytPlayerConfig.getJSONObject("args");
        } catch (RegexException e) {
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
        } try { //fall through to fallback
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
        try {
            return playerArgs.getString("dashmpd");
        } catch(NullPointerException e) {
            throw new ParsingException(
                    "Could not find \"dashmpd\" upon the player args (maybe no dash manifest available).", e);
        } catch (Exception e) {
            throw new ParsingException(e);
        }
    }

    @Override
    public VideoInfo.AudioStream[] getAudioStreams() throws ParsingException {
        try {
            String dashManifest = playerArgs.getString("dashmpd");
            return parseDashManifest(dashManifest, decryptionCode);
        } catch (NullPointerException e) {
            throw new ParsingException(
                    "Could not find \"dashmpd\" upon the player args (maybe no dash manifest available).", e);
        } catch (Exception e) {
            throw new ParsingException(e);
        }
    }

    @Override
    public VideoInfo.VideoStream[] getVideoStreams() throws ParsingException {
        Vector<VideoInfo.VideoStream> videoStreams = new Vector<>();
        try{
            String encoded_url_map = playerArgs.getString("url_encoded_fmt_stream_map");
            for(String url_data_str : encoded_url_map.split(",")) {
                try {
                    Map<String, String> tags = new HashMap<>();
                    for (String raw_tag : Parser.unescapeEntities(url_data_str, true).split("&")) {
                        String[] split_tag = raw_tag.split("=");
                        tags.put(split_tag[0], split_tag[1]);
                    }

                    int itag = Integer.parseInt(tags.get("itag"));
                    String streamUrl = URLDecoder.decode(tags.get("url"), "UTF-8");

                    // if video has a signature: decrypt it and add it to the url
                    if (tags.get("s") != null) {
                        streamUrl = streamUrl + "&signature="
                                + decryptSignature(tags.get("s"), decryptionCode);
                    }

                    if (resolveFormat(itag) != -1) {
                        videoStreams.add(new VideoInfo.VideoStream(
                                streamUrl,
                                resolveFormat(itag),
                                resolveResolutionString(itag)));
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

        return videoStreams.toArray(new VideoInfo.VideoStream[videoStreams.size()]);
    }

    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoId(String url) throws ParsingException {
        String id;
        String pat;

        if(url.contains("youtube")) {
            pat = "youtube\\.com/watch\\?v=([\\-a-zA-Z0-9_]{11})";
        }
        else if(url.contains("youtu.be")) {
            pat = "youtu\\.be/([a-zA-Z0-9_-]{11})";
        }
        else {
            throw new ParsingException("Error no suitable url: " + url);
        }

        id = matchGroup1(pat, url);
        if(!id.isEmpty()){
            //Log.i(TAG, "string \""+url+"\" matches!");
            return id;
        } else {
            throw new ParsingException("Error could not parse url: " + url);
        }
    }

    @SuppressWarnings("WeakerAccess")
    @Override
    public String getVideoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /**Attempts to parse (and return) the offset to start playing the video from.
     * @return the offset (in seconds), or 0 if no timestamp is found.*/
    @Override
    public int getTimeStamp() throws ParsingException {
        //todo: add unit test for timestamp
        String timeStamp;
        try {
            timeStamp = matchGroup1("((#|&|\\?)t=\\d{0,3}h?\\d{0,3}m?\\d{1,3}s?)", pageUrl);
        } catch (RegexException e) {
            // catch this instantly since an url does not necessarily have to have a time stamp

            // -2 because well the testing system will then know its the regex that failed :/
            // not good i know
            return -2;
        }

        //TODO: test this
        if(!timeStamp.isEmpty()) {
            try {
                String secondsString = "";
                String minutesString = "";
                String hoursString = "";
                try {
                    secondsString = matchGroup1("(\\d{1,3})s", timeStamp);
                    minutesString = matchGroup1("(\\d{1,3})m", timeStamp);
                    hoursString = matchGroup1("(\\d{1,3})h", timeStamp);
                } catch (Exception e) {
                    //it could be that time is given in another method
                    if (secondsString.isEmpty() //if nothing was got,
                            && minutesString.isEmpty()//treat as unlabelled seconds
                            && hoursString.isEmpty()) {
                        secondsString = matchGroup1("t=(\\d{1,3})", timeStamp);
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
            return -1;
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

    private VideoInfo.AudioStream[] parseDashManifest(String dashManifest, String decryptoinCode) throws RegexException, DecryptException {
        if(!dashManifest.contains("/signature/")) {
            String encryptedSig = matchGroup1("/s/([a-fA-F0-9\\.]+)", dashManifest);
            String decryptedSig;

            decryptedSig = decryptSignature(encryptedSig, decryptoinCode);
            dashManifest = dashManifest.replace("/s/" + encryptedSig, "/signature/" + decryptedSig);
        }
        String dashDoc;
        try {
            dashDoc = downloader.download(dashManifest);
        } catch(IOException ioe) {
            throw new DecryptException("Could not get dash mpd", ioe);
        }
        Vector<VideoInfo.AudioStream> audioStreams = new Vector<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(dashDoc));
            String tagName = "";
            String currentMimeType = "";
            int currentBandwidth = -1;
            int currentSamplingRate = -1;
            boolean currentTagIsBaseUrl = false;
            for(int eventType = parser.getEventType();
                eventType != XmlPullParser.END_DOCUMENT;
                eventType = parser.next() ) {
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
                        //missing break here?
                    case XmlPullParser.END_TAG:
                        if(tagName.equals("AdaptationSet")) {
                            currentMimeType = "";
                        } else if(tagName.equals("BaseURL")) {
                            currentTagIsBaseUrl = false;
                        }//no break needed here
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return audioStreams.toArray(new VideoInfo.AudioStream[audioStreams.size()]);
    }

    /**Provides information about links to other videos on the video page, such as related videos.
     * This is encapsulated in a VideoPreviewInfo object,
     * which is a subset of the fields in a full VideoInfo.*/
    private VideoPreviewInfo extractVideoPreviewInfo(Element li) throws ParsingException {
        VideoPreviewInfo info = new VideoPreviewInfo();

        try {
            info.webpage_url = li.select("a.content-link").first()
                    .attr("abs:href");

            info.id = matchGroup1("v=([0-9a-zA-Z-]*)", info.webpage_url);

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

            decryptionFuncName = matchGroup1("\\.sig\\|\\|([a-zA-Z0-9$]+)\\(", playerCode);

            String functionPattern = "(" + decryptionFuncName.replace("$", "\\$") + "=function\\([a-zA-Z0-9_]*\\)\\{.+?\\})";
            decryptionFunc = "var " + matchGroup1(functionPattern, playerCode) + ";";

            helperObjectName = matchGroup1(";([A-Za-z0-9_\\$]{2})\\...\\(", decryptionFunc);

            String helperPattern = "(var " + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)";
            helperObject = matchGroup1(helperPattern, playerCode);


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

    private String cleanUrl(String complexUrl) throws ParsingException {
        return getVideoUrl(getVideoId(complexUrl));
    }

    private String matchGroup1(String pattern, String input) throws RegexException {
        Pattern pat = Pattern.compile(pattern);
        Matcher mat = pat.matcher(input);
        boolean foundMatch = mat.find();
        if (foundMatch) {
            return mat.group(1);
        }
        else {
            //Log.e(TAG, "failed to find pattern \""+pattern+"\" inside of \""+input+"\"");
            throw new RegexException("failed to find pattern \""+pattern+" inside of "+input+"\"");
        }
    }

    private String findErrorReason(Document doc) {
        errorMessage = doc.select("h1[id=\"unavailable-message\"]").first().text();
        if(errorMessage.contains("GEMA")) {
            // Gema sometimes blocks youtube music content in germany:
            // https://www.gema.de/en/
            // Detailed description:
            // https://en.wikipedia.org/wiki/GEMA_%28German_organization%29
            return "GEMA";
        }
        return "";
    }

    /**These lists only contain itag formats that are supported by the common Android Video player.
     However if you are looking for a list showing all itag formats, look at
     https://github.com/rg3/youtube-dl/issues/1687 */

    @SuppressWarnings("WeakerAccess")
    public static int resolveFormat(int itag) {
        switch(itag) {
            // !!! lists only supported formats !!!
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

    @SuppressWarnings("WeakerAccess")
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
}
