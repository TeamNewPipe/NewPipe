package org.schabi.newpipe.util;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.Log;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.App;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

public final class SponsorBlockUtils {
    private static final Application APP = App.getApp();
    private static final String TAG = SponsorBlockUtils.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private SponsorBlockUtils() {
    }

    @SuppressWarnings("CheckStyle")
    public static VideoSegment[] getYouTubeVideoSegments(final String apiUrl,
                                                  final String videoId,
                                                  final boolean includeSponsorCategory,
                                                  final boolean includeIntroCategory,
                                                  final boolean includeOutroCategory,
                                                  final boolean includeInteractionCategory,
                                                  final boolean includeSelfPromoCategory,
                                                  final boolean includeMusicCategory)
            throws UnsupportedEncodingException {
        final ArrayList<String> categoryParamList = new ArrayList<>();

        if (includeSponsorCategory) {
            categoryParamList.add("sponsor");
        }
        if (includeIntroCategory) {
            categoryParamList.add("intro");
        }
        if (includeOutroCategory) {
            categoryParamList.add("outro");
        }
        if (includeInteractionCategory) {
            categoryParamList.add("interaction");
        }
        if (includeSelfPromoCategory) {
            categoryParamList.add("selfpromo");
        }
        if (includeMusicCategory) {
            categoryParamList.add("music_offtopic");
        }

        if (categoryParamList.size() == 0) {
            return null;
        }

        String categoryParams = "[\"" + TextUtils.join("\",\"", categoryParamList) + "\"]";
        categoryParams = URLEncoder.encode(categoryParams, "utf-8");

        final String videoIdHash = toSha256(videoId);

        if (videoIdHash == null) {
            return null;
        }

        final String params = "skipSegments/" + videoIdHash.substring(0, 4)
                + "?categories=" + categoryParams;

        if (!isConnected()) {
            return null;
        }

        JsonArray responseArray = null;

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .get(apiUrl + params)
                            .responseBody();

            responseArray = JsonParser.array().from(responseBody);

        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        if (responseArray == null) {
            return null;
        }

        final ArrayList<VideoSegment> result = new ArrayList<>();

        for (final Object obj1 : responseArray) {
            final JsonObject jObj1 = (JsonObject) obj1;

            final String responseVideoId = jObj1.getString("videoID");
            if (!responseVideoId.equals(videoId)) {
                continue;
            }

            final JsonArray segmentArray = (JsonArray) jObj1.get("segments");
            if (segmentArray == null) {
                continue;
            }

            for (final Object obj2 : segmentArray) {
                final JsonObject jObj2 = (JsonObject) obj2;

                final JsonArray segmentInfo = (JsonArray) jObj2.get("segment");
                if (segmentInfo == null) {
                    continue;
                }

                final double startTime = segmentInfo.getDouble(0) * 1000;
                final double endTime = segmentInfo.getDouble(1) * 1000;
                final String category = jObj2.getString("category");

                final VideoSegment segment = new VideoSegment(startTime, endTime, category);
                result.add(segment);
            }
        }

        return result.toArray(new VideoSegment[0]);
    }

    private static boolean isConnected() {
        final ConnectivityManager cm =
                (ConnectivityManager) APP.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnected();
    }

    private static String toSha256(final String videoId) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(videoId.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();

            for (final byte b : bytes) {
                final String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    sb.append('0');
                }

                sb.append(hex);
            }

            return sb.toString();
        } catch (final Exception e) {
            Log.e("SPONSOR_BLOCK", "Error getting video ID hash.", e);
            return null;
        }
    }
}
