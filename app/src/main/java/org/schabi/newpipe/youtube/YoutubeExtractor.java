package org.schabi.newpipe.youtube;

import android.util.Log;
import android.util.Xml;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.Extractor;
import org.schabi.newpipe.VideoInfo;
import org.schabi.newpipe.VideoInfoItem;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URI;
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

public class YoutubeExtractor implements Extractor {

    private static final String TAG = YoutubeExtractor.class.toString();

    // These lists only contain itag formats that are supported by the common Android Video player.
    // How ever if you are heading for a list showing all itag formats lock at
    // https://github.com/rg3/youtube-dl/issues/1687

    public static int resolveFormat(int itag) {
        switch(itag) {
            // video
            case 17: return VideoInfo.I_3GPP;
            case 18: return VideoInfo.I_MPEG_4;
            case 22: return VideoInfo.I_MPEG_4;
            case 36: return VideoInfo.I_3GPP;
            case 37: return VideoInfo.I_MPEG_4;
            case 38: return VideoInfo.I_MPEG_4;
            case 43: return VideoInfo.I_WEBM;
            case 44: return VideoInfo.I_WEBM;
            case 45: return VideoInfo.I_WEBM;
            case 46: return VideoInfo.I_WEBM;
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

    private String decryptionCode = "";
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    @Override
    public String getVideoId(String videoUrl) {
        try {
            URI uri = new URI(videoUrl);
            if(uri.getHost().contains("youtube")) {
                String query = uri.getFragment();
                if(query == null) {
                    query = uri.getQuery();
                } else {
                    query = query.replace("/watch?", "");
                }
                String queryElements[] = query.split("&");
                Map<String, String> queryArguments = new HashMap<>();
                for (String e : queryElements) {
                    String[] s = e.split("=");
                    queryArguments.put(s[0], s[1]);
                }
                return queryArguments.get("v");
            } else if(uri.getHost().contains("youtu.be")) {
                // uri.getRawPath() does somehow not return the last character.
                // so we do a workaround instead.
                //return uri.getRawPath();
                String url[] = videoUrl.split("/");
                return url[url.length-1];
            } else {
                Log.e(TAG, "Error could not parse url: " + videoUrl);

            }
        }  catch(Exception e) {
            Log.e(TAG, "Error could not parse url: " + videoUrl);
            e.printStackTrace();
            return "";
        }
        return null;
    }

    @Override
    public String getVideoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    @Override
    public VideoInfo getVideoInfo(String siteUrl) {
        String site = Downloader.download(siteUrl);
        VideoInfo videoInfo = new VideoInfo();

        Document doc = Jsoup.parse(site, siteUrl);

        try {
            Pattern p = Pattern.compile("v=([0-9a-zA-Z]*)");
            Matcher m = p.matcher(siteUrl);
            m.find();
            videoInfo.id = m.group(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        videoInfo.age_limit = 0;
        videoInfo.webpage_url = siteUrl;

        //-------------------------------------
        // extracting form player args
        //-------------------------------------
        JSONObject playerArgs = null;
        JSONObject ytAssets = null;
        String dashManifest;
        {
            Pattern p = Pattern.compile("ytplayer.config\\s*=\\s*(\\{.*?\\});");
            Matcher m = p.matcher(site);
            m.find();

            try {
                playerArgs = (new JSONObject(m.group(1)))
                        .getJSONObject("args");
                ytAssets = (new JSONObject(m.group(1)))
                        .getJSONObject("assets");
            }catch (Exception e) {
                e.printStackTrace();
                // If we fail in this part the video is most likely not available.
                // Determining why is done later.
                videoInfo.videoAvailableStatus = VideoInfo.VIDEO_UNAVAILABLE;
            }
        }

        try {
            videoInfo.uploader = playerArgs.getString("author");
            videoInfo.title = playerArgs.getString("title");
            //first attempt gating a small image version
            //in the html extracting part we try to get a thumbnail with a higher resolution
            videoInfo.thumbnail_url = playerArgs.getString("thumbnail_url");
            videoInfo.duration = playerArgs.getInt("length_seconds");
            videoInfo.average_rating = playerArgs.getString("avg_rating");
            String playerUrl = ytAssets.getString("js");
            if(playerUrl.startsWith("//")) {
                playerUrl = "https:" + playerUrl;
            }
            if(decryptionCode.isEmpty()) {
                decryptionCode = loadDecryptionCode(playerUrl);
            }

            // extract audio
            try {
                dashManifest = playerArgs.getString("dashmpd");
                videoInfo.audioStreams = parseDashManifest(dashManifest, decryptionCode);
            } catch (Exception e) {
                //todo: check if the following statement is true
                Log.e(TAG, "Dash manifest seems not to bee available.");
                e.printStackTrace();
            }

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
                String streamUrl = terrible_unescape_workaround_fuck(tags.get("url"));

                // if video has a signature: decrypt it and add it to the url
                if(tags.get("s") != null) {
                    if(decryptionCode.isEmpty()) {
                        decryptionCode = loadDecryptionCode(playerUrl);
                    }
                    streamUrl = streamUrl + "&signature=" + decryptSignature(tags.get("s"), decryptionCode);
                }

                if(resolveFormat(itag) != -1) {
                    videoStreams.add(new VideoInfo.VideoStream(
                            streamUrl,
                            resolveFormat(itag),
                            resolveResolutionString(itag)));
                }
            }
            videoInfo.videoStreams =
                    videoStreams.toArray(new VideoInfo.VideoStream[videoStreams.size()]);

        } catch (Exception e) {
            e.printStackTrace();
        }

        //-------------------------------
        // extracting from html page
        //-------------------------------


        // Determine what went wrong when the Video is not available
        if(videoInfo.videoAvailableStatus == VideoInfo.VIDEO_UNAVAILABLE) {
            if(doc.select("h1[id=\"unavailable-message\"]").first().text().contains("GEMA")) {
                videoInfo.videoAvailableStatus = VideoInfo.VIDEO_UNAVAILABLE_GEMA;
            }
        }

        // Try to get high resolution thumbnail if it fails use low res from the player instead
        try {
            videoInfo.thumbnail_url = doc.select("link[itemprop=\"thumbnailUrl\"]").first()
                    .attr("abs:href");
        } catch(Exception e) {
            Log.i(TAG, "Could not find high res Thumbnail. Use low res instead");
        }

        // upload date
        videoInfo.upload_date = doc.select("strong[class=\"watch-time-text\"").first()
                .text();
        // Try to only use date not the text around it
        try {
            Pattern p = Pattern.compile("([0-9.]*$)");
            Matcher m = p.matcher(videoInfo.upload_date);
            m.find();
            videoInfo.upload_date = m.group(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // description
        videoInfo.description = doc.select("p[id=\"eow-description\"]").first()
                .html();

        try {
            // likes
            videoInfo.like_count = doc.select("span[class=\"like-button-renderer \"]").first()
                    .getAllElements().select("button")
                    .select("span").get(0).text();


            // dislikes
            videoInfo.dislike_count = doc.select("span[class=\"like-button-renderer \"]").first()
                    .getAllElements().select("button")
                    .select("span").get(2).text();
        } catch(Exception e) {
            // if it fails we know that the video does not offer dislikes.
            videoInfo.like_count = "0";
            videoInfo.dislike_count = "0";
        }

        // uploader thumbnail
        videoInfo.uploader_thumbnail_url = doc.select("a[class*=\"yt-user-photo\"]").first()
                .select("img").first()
                .attr("abs:data-thumb");

        // view count
        videoInfo.view_count = doc.select("div[class=\"watch-view-count\"]").first().text();

        // next video
        videoInfo.nextVideo = extractVideoInfoItem(doc.select("div[class=\"watch-sidebar-section\"]").first()
                .select("li").first());

        int i = 0;
        // related videos
        Vector<VideoInfoItem> relatedVideos = new Vector<>();
        for(Element li : doc.select("ul[id=\"watch-related\"]").first().children()) {
            // first check if we have a playlist. If so leave them out
            if(li.select("a[class*=\"content-link\"]").first() != null) {
                relatedVideos.add(extractVideoInfoItem(li));
                i++;
            }
        }
        videoInfo.relatedVideos = relatedVideos.toArray(new VideoInfoItem[relatedVideos.size()]);
        return videoInfo;
    }

    private VideoInfo.AudioStream[] parseDashManifest(String dashManifest, String decryptoinCode) {
        if(!dashManifest.contains("/signature/")) {
            String encryptedSig = "";
            String decryptedSig;
            try {
                Pattern p = Pattern.compile("/s/([a-fA-F0-9\\.]+)");
                Matcher m = p.matcher(dashManifest);
                m.find();
                encryptedSig = m.group(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                            if(currentMimeType.equals(VideoInfo.M_WEBMA)) {
                                format = VideoInfo.I_WEBMA;
                            } else if(currentMimeType.equals(VideoInfo.M_M4A)) {
                                format = VideoInfo.I_M4A;
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

    private VideoInfoItem extractVideoInfoItem(Element li) {
        VideoInfoItem info = new VideoInfoItem();
        info.webpage_url = li.select("a[class*=\"content-link\"]").first()
                .attr("abs:href");
        try {
            Pattern p = Pattern.compile("v=([0-9a-zA-Z-]*)");
            Matcher m = p.matcher(info.webpage_url);
            m.find();
            info.id=m.group(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        info.title = li.select("span[class=\"title\"]").first().text();
        info.view_count = li.select("span[class*=\"view-count\"]").first().text();
        info.uploader = li.select("span[class=\"g-hovercard\"]").first().text();
        info.duration = li.select("span[class=\"video-time\"]").first().text();

        Element img = li.select("img").first();
        info.thumbnail_url = img.attr("abs:src");
        // Sometimes youtube sends links to gif files witch somehow seam to not exist
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

    private String terrible_unescape_workaround_fuck(String shit) {
        String[] splitAtEscape = shit.split("%");
        String retval = "";
        retval += splitAtEscape[0];
        for(int i = 1; i < splitAtEscape.length; i++) {
            String escNum = splitAtEscape[i].substring(0, 2);
            char c = (char) Integer.parseInt(escNum,16);
            retval += c;
            retval += splitAtEscape[i].substring(2);
        }
        return retval;
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
            Pattern p = Pattern.compile("\\.sig\\|\\|([a-zA-Z0-9$]+)\\(");
            Matcher m = p.matcher(playerCode);
            m.find();
            decryptionFuncName = m.group(1);

            String functionPattern = "(function " + decryptionFuncName.replace("$", "\\$") + "\\([a-zA-Z0-9_]*\\)\\{.+?\\})";
            p = Pattern.compile(functionPattern);
            m = p.matcher(playerCode);
            m.find();
            decryptionFunc = m.group(1);

            p = Pattern.compile(";([A-Za-z0-9_\\$]{2})\\...\\(");
            m = p.matcher(decryptionFunc);
            m.find();
            helperObjectName = m.group(1);

            String helperPattern = "(var " + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)function";
            p = Pattern.compile(helperPattern);
            m = p.matcher(playerCode);
            m.find();
            helperObject = m.group(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        callerFunc = callerFunc.replace("%%", decryptionFuncName);
        decryptionCode = helperObject + decryptionFunc + callerFunc;

        return decryptionCode;
    }

    private String decryptSignature(String encryptedSig, String decryptoinCode) {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        Object result = null;
        try {
            ScriptableObject scope = context.initStandardObjects();
            context.evaluateString(scope, decryptoinCode, "decryptionCode", 1, null);
            Function decryptionFunc = (Function) scope.get("decrypt", scope);
            result = decryptionFunc.call(context, scope, scope, new Object[]{encryptedSig});
        } catch (Exception e) {
            e.printStackTrace();
        }
        Context.exit();
        return result.toString();
    }
}
