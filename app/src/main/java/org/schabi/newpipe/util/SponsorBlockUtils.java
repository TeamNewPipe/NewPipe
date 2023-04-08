package org.schabi.newpipe.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.App;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.views.MarkableSeekBar;
import org.schabi.newpipe.views.SeekBarMarker;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class SponsorBlockUtils {
    private static final Application APP = App.getApp();
    private static final String TAG = SponsorBlockUtils.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static Map<String, VideoSegment[]> videoSegmentsCache = new HashMap<>();

    private SponsorBlockUtils() {
    }

    @SuppressWarnings("CheckStyle")
    public static VideoSegment[] getYouTubeVideoSegments(final Context context,
                                                         final StreamInfo streamInfo)
            throws UnsupportedEncodingException {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean isSponsorBlockEnabled = prefs.getBoolean(context
                .getString(R.string.sponsor_block_enable_key), false);

        if (!isSponsorBlockEnabled) {
            return null;
        }

        final String apiUrl = prefs.getString(context
                .getString(R.string.sponsor_block_api_url_key), null);

        if (streamInfo.getServiceId() != ServiceList.YouTube.getServiceId()
                || apiUrl == null
                || apiUrl.isEmpty()) {
            return null;
        }

        final boolean includeSponsorCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_sponsor_key), false);
        final boolean includeIntroCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_intro_key), false);
        final boolean includeOutroCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_outro_key), false);
        final boolean includeInteractionCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_interaction_key), false);
        final boolean includeSelfPromoCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_self_promo_key), false);
        final boolean includeMusicCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_non_music_key), false);
        final boolean includePreviewCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_preview_key), false);
        final boolean includeFillerCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_filler_key), false);

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
        if (includePreviewCategory) {
            categoryParamList.add("preview");
        }

        if (includeFillerCategory) {
            categoryParamList.add("filler");
        }

        if (categoryParamList.size() == 0) {
            return null;
        }

        String categoryParams = "[\"" + TextUtils.join("\",\"", categoryParamList) + "\"]";
        categoryParams = URLEncoder.encode(categoryParams, "utf-8");

        final String videoIdHash = toSha256(streamInfo.getId());

        if (videoIdHash == null) {
            return null;
        }

        final String params = "skipSegments/" + videoIdHash.substring(0, 4)
                + "?categories=" + categoryParams;

        final VideoSegment[] alreadyFetchedVideoSegments = videoSegmentsCache.get(params);
        if (alreadyFetchedVideoSegments != null) {
            return alreadyFetchedVideoSegments;
        }

        if (!isConnected()) {
            return null;
        }

        JsonArray responseArray = null;

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .setCustomTimeout(3)
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
            if (!responseVideoId.equals(streamInfo.getId())) {
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
        final VideoSegment[] segments = result.toArray(new VideoSegment[0]);
        videoSegmentsCache.put(params, segments);
        return segments;
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

    static Integer parseSegmentCategory(
            final String category,
            final Context context,
            final SharedPreferences prefs
    ) {
        String key;
        final String colorStr;
        switch (category) {
            case "sponsor":
                key = context.getString(R.string.sponsor_block_category_sponsor_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_sponsor_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.sponsor_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "intro":
                key = context.getString(R.string.sponsor_block_category_intro_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_intro_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.intro_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "outro":
                key = context.getString(R.string.sponsor_block_category_outro_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_outro_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.outro_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "interaction":
                key = context.getString(R.string.sponsor_block_category_interaction_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_interaction_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.interaction_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "selfpromo":
                key = context.getString(R.string.sponsor_block_category_self_promo_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_self_promo_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.self_promo_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "music_offtopic":
                key = context.getString(R.string.sponsor_block_category_non_music_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_non_music_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.non_music_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "preview":
                key = context.getString(R.string.sponsor_block_category_preview_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_preview_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.preview_segment)
                            : Color.parseColor(colorStr);
                }
                break;
            case "filler":
                key = context.getString(R.string.sponsor_block_category_filler_key);
                if (prefs.getBoolean(key, false)) {
                    key = context.getString(R.string.sponsor_block_category_filler_color_key);
                    colorStr = prefs.getString(key, null);
                    return colorStr == null
                            ? context.getResources().getColor(R.color.filler_segment)
                            : Color.parseColor(colorStr);
                }
                break;
        }

        return null;
    }

    public static void markSegments(
            final PlayQueueItem currentItem,
            final MarkableSeekBar seekBar,
            final Context context,
            final SharedPreferences prefs
    ) {
        seekBar.clearMarkers();

        if (currentItem == null) {
            return;
        }

        final VideoSegment[] segments = currentItem.getVideoSegments();

        if (segments == null || segments.length == 0) {
            return;
        }

        for (final VideoSegment segment : segments) {
            final Integer color = parseSegmentCategory(segment.category, context, prefs);

            // if null, then this category should not be marked
            if (color == null) {
                continue;
            }

            // Duration is in seconds, we need millis
            final int length = (int) currentItem.getDuration() * 1000;

            final SeekBarMarker seekBarMarker =
                    new SeekBarMarker(segment.startTime, segment.endTime,
                            length, color);
            seekBar.seekBarMarkers.add(seekBarMarker);
        }

        seekBar.drawMarkers();
    }
}
