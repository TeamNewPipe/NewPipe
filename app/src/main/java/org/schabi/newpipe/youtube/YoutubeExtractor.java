package org.schabi.newpipe.youtube;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.Extractor;
import org.schabi.newpipe.VideoInfo;

import android.util.Log;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.schabi.newpipe.VideoInfoItem;

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

    public static String resolveFormat(int itag) {
        switch(itag) {
            case 17: return VideoInfo.F_3GPP;
            case 18: return VideoInfo.F_MPEG_4;
            case 22: return VideoInfo.F_MPEG_4;
            case 36: return VideoInfo.F_3GPP;
            case 37: return VideoInfo.F_MPEG_4;
            case 38: return VideoInfo.F_MPEG_4;
            case 43: return VideoInfo.F_WEBM;
            case 44: return VideoInfo.F_WEBM;
            case 45: return VideoInfo.F_WEBM;
            case 46: return VideoInfo.F_WEBM;
            default:
                //Log.i(TAG, "Itag " + Integer.toString(itag) + " not known or not supported.");
                return null;
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

    private String decryptoinCode = "";
    private static final String DECRYPTION_FUNC_NAME="decrypt";

    @Override
    public String getVideoId(String videoUrl) {
        try {
            String query = (new URI(videoUrl)).getQuery();
            String queryElements[] = query.split("&");
            Map<String, String> queryArguments = new HashMap<>();
            for(String e : queryElements) {
                String[] s = e.split("=");
                queryArguments.put(s[0], s[1]);
            }
            return queryArguments.get("v");
        } catch(Exception e) {
            Log.e(TAG, "Error could not parse url: " + videoUrl);
            e.printStackTrace();
            return "";
        }
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
            // View Count will be extracted from html
            //videoInfo.view_count = playerArgs.getString("view_count");

            //------------------------------------
            // extract stream url
            //------------------------------------
            String encoded_url_map = playerArgs.getString("url_encoded_fmt_stream_map");
            Vector<VideoInfo.Stream> streams = new Vector<>();
            for(String url_data_str : encoded_url_map.split(",")) {
                Map<String, String> tags = new HashMap<>();
                for(String raw_tag : Parser.unescapeEntities(url_data_str, true).split("&")) {
                    String[] split_tag = raw_tag.split("=");
                    tags.put(split_tag[0], split_tag[1]);
                }

                int itag = Integer.parseInt(tags.get("itag"));
                String streamUrl = terrible_unescape_workaround_fuck(tags.get("url"));

                // if video has a signature decrypt it and add it to the url
                if(tags.get("s") != null) {
                    String playerUrl = ytAssets.getString("js");
                    if(playerUrl.startsWith("//")) {
                        playerUrl = "https:" + playerUrl;
                    }
                    if(decryptoinCode.isEmpty()) {
                        decryptoinCode = loadDecryptioinCode(playerUrl);
                    }
                    streamUrl = streamUrl + "&signature=" + decriptSignature(tags.get("s"), decryptoinCode);
                }

                if(resolveFormat(itag) != null) {
                    streams.add(new VideoInfo.Stream(
                            streamUrl,   //sometimes i have no idea what im programming -.-
                            resolveFormat(itag),
                            resolveResolutionString(itag)));
                }
            }
            videoInfo.streams = new VideoInfo.Stream[streams.size()];
            for(int i = 0; i < streams.size(); i++) {
                videoInfo.streams[i] = streams.get(i);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        //-------------------------------
        // extrating from html page
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

        /* todo finish this code

        // next video
        videoInfo.nextVideo = extractVideoInfoItem(doc.select("div[class=\"watch-sidebar-section\"]").first()
                .select("li").first());

        int i = 0;
        // related videos
        for(Element li : doc.select("ul[id=\"watch-related\"]").first().children()) {
            // first check if we have a playlist. If so leave them out
            if(li.select("a[class*=\"content-link\"]").first() != null) {
                //videoInfo.relatedVideos.add(extractVideoInfoItem(li));
                //i++;
                //Log.d(TAG, Integer.toString(i));
            }
        }

        */

        return videoInfo;
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
        info.title = li.select("span[class=\"title\"]").first()
                .text();

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

    private String loadDecryptioinCode(String playerUrl) {
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

    private String decriptSignature(String encryptedSig, String decryptoinCode) {
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
