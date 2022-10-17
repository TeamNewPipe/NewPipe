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
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.RandomStringFromAlphabetGenerator;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.views.MarkableSeekBar;
import org.schabi.newpipe.views.SeekBarMarker;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;

public final class SponsorBlockUtils {
    private static final Application APP = App.getApp();
    private static final String TAG = SponsorBlockUtils.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random NUMBER_GENERATOR = new SecureRandom();

    private SponsorBlockUtils() {
    }

    @SuppressWarnings("CheckStyle")
    public static SponsorBlockSegment[] getSponsorBlockSegments(final Context context,
                                                                final StreamInfo streamInfo)
            throws UnsupportedEncodingException {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean isSponsorBlockEnabled = prefs.getBoolean(context
                .getString(R.string.sponsor_block_enable_key), false);

        if (!isSponsorBlockEnabled) {
            return new SponsorBlockSegment[0];
        }

        final String apiUrl = prefs.getString(context
                .getString(R.string.sponsor_block_api_url_key), null);

        if (!streamInfo.getUrl().startsWith("https://www.youtube.com")
                || apiUrl == null
                || apiUrl.isEmpty()) {
            return new SponsorBlockSegment[0];
        }

        final String videoId = streamInfo.getId();

        final boolean includeSponsorCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_sponsor_key), false);
        final boolean includeIntroCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_intro_key), false);
        final boolean includeOutroCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_outro_key), false);
        final boolean includeInteractionCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_interaction_key), false);
        final boolean includeHighlightCategory = prefs.getBoolean(context
                .getString(R.string.sponsor_block_category_highlight_key), false);
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
            categoryParamList.add(SponsorBlockCategory.SPONSOR.getApiName());
        }
        if (includeIntroCategory) {
            categoryParamList.add(SponsorBlockCategory.INTRO.getApiName());
        }
        if (includeOutroCategory) {
            categoryParamList.add(SponsorBlockCategory.OUTRO.getApiName());
        }
        if (includeInteractionCategory) {
            categoryParamList.add(SponsorBlockCategory.INTERACTION.getApiName());
        }
        if (includeHighlightCategory) {
            categoryParamList.add(SponsorBlockCategory.HIGHLIGHT.getApiName());
        }
        if (includeSelfPromoCategory) {
            categoryParamList.add(SponsorBlockCategory.SELF_PROMO.getApiName());
        }
        if (includeMusicCategory) {
            categoryParamList.add(SponsorBlockCategory.NON_MUSIC.getApiName());
        }
        if (includePreviewCategory) {
            categoryParamList.add(SponsorBlockCategory.PREVIEW.getApiName());
        }

        if (includeFillerCategory) {
            categoryParamList.add(SponsorBlockCategory.FILLER.getApiName());
        }

        if (categoryParamList.size() == 0) {
            return new SponsorBlockSegment[0];
        }

        String categoryParams = "[\"" + TextUtils.join("\",\"", categoryParamList) + "\"]";
        categoryParams = URLEncoder.encode(categoryParams, "utf-8");

        String actionParams = "[\"skip\",\"poi\"]";
        actionParams = URLEncoder.encode(actionParams, "utf-8");

        final String videoIdHash = toSha256(videoId);

        if (videoIdHash == null) {
            return new SponsorBlockSegment[0];
        }

        final String url = apiUrl + "skipSegments/" + videoIdHash.substring(0, 4)
                + "?categories=" + categoryParams
                + "&actionTypes=" + actionParams
                + "&userAgent=Mozilla/5.0";

        if (!isConnected()) {
            return new SponsorBlockSegment[0];
        }

        JsonArray responseArray = null;

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .setCustomTimeout(3)
                            .get(url)
                            .responseBody();

            responseArray = JsonParser.array().from(responseBody);

        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        if (responseArray == null) {
            return new SponsorBlockSegment[0];
        }

        final ArrayList<SponsorBlockSegment> result = new ArrayList<>();

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

                final String uuid = jObj2.getString("UUID");
                final double startTime = segmentInfo.getDouble(0) * 1000;
                final double endTime = segmentInfo.getDouble(1) * 1000;
                final String category = jObj2.getString("category");
                final String action = jObj2.getString("actionType");

                final SponsorBlockSegment sponsorBlockSegment =
                        new SponsorBlockSegment(uuid, startTime, endTime,
                                SponsorBlockCategory.fromApiName(category),
                                SponsorBlockAction.fromApiName(action));
                result.add(sponsorBlockSegment);
            }
        }

        return result.toArray(new SponsorBlockSegment[0]);
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

    public static Integer parseColorFromSegmentCategory(
            final SponsorBlockCategory category,
            final Context context
    ) {
        final String key;
        final String colorStr;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        switch (category) {
            case SPONSOR:
                key = context.getString(R.string.sponsor_block_category_sponsor_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.sponsor_segment)
                        : Color.parseColor(colorStr);
            case INTRO:
                key = context.getString(R.string.sponsor_block_category_intro_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.intro_segment)
                        : Color.parseColor(colorStr);
            case OUTRO:
                key = context.getString(R.string.sponsor_block_category_outro_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.outro_segment)
                        : Color.parseColor(colorStr);
            case INTERACTION:
                key = context.getString(R.string.sponsor_block_category_interaction_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.interaction_segment)
                        : Color.parseColor(colorStr);
            case HIGHLIGHT:
                key = context.getString(R.string.sponsor_block_category_highlight_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.highlight_segment)
                        : Color.parseColor(colorStr);
            case SELF_PROMO:
                key = context.getString(R.string.sponsor_block_category_self_promo_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.self_promo_segment)
                        : Color.parseColor(colorStr);
            case NON_MUSIC:
                key = context.getString(R.string.sponsor_block_category_non_music_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.non_music_segment)
                        : Color.parseColor(colorStr);
            case PREVIEW:
                key = context.getString(R.string.sponsor_block_category_preview_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.preview_segment)
                        : Color.parseColor(colorStr);
            case FILLER:
                key = context.getString(R.string.sponsor_block_category_filler_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.filler_segment)
                        : Color.parseColor(colorStr);
            case PENDING:
                key = context.getString(R.string.sponsor_block_category_pending_color_key);
                colorStr = prefs.getString(key, null);
                return colorStr == null
                        ? context.getResources().getColor(R.color.pending_segment)
                        : Color.parseColor(colorStr);
        }

        return null;
    }

    public static void markSegments(
            final PlayQueueItem currentItem,
            final MarkableSeekBar seekBar,
            final Context context
    ) {
        seekBar.clearMarkers();

        if (currentItem == null) {
            return;
        }

        final ArrayList<SponsorBlockSegment> sponsorBlockSegments =
                currentItem.getSponsorBlockSegments();

        if (sponsorBlockSegments == null || sponsorBlockSegments.size() == 0) {
            return;
        }

        for (final SponsorBlockSegment sponsorBlockSegment : sponsorBlockSegments) {
            final Integer color = parseColorFromSegmentCategory(
                    sponsorBlockSegment.category, context);

            // if null, then this category should not be marked
            if (color == null) {
                continue;
            }

            // Duration is in seconds, we need millis
            final int length = (int) currentItem.getDuration() * 1000;

            final SeekBarMarker seekBarMarker =
                    new SeekBarMarker(sponsorBlockSegment.startTime, sponsorBlockSegment.endTime,
                            length, color);
            seekBar.seekBarMarkers.add(seekBarMarker);
        }

        seekBar.drawMarkers();
    }

    public static Response submitSponsorBlockSegment(
            final Context context,
            final StreamInfo streamInfo,
            final SponsorBlockSegment segment) {
        if (segment.category == SponsorBlockCategory.PENDING) {
            return null;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final String apiUrl = prefs.getString(context
                .getString(R.string.sponsor_block_api_url_key), null);
        if (apiUrl == null || apiUrl.isEmpty()) {
            return null;
        }

        if (!streamInfo.getUrl().startsWith("https://www.youtube.com")) {
            return null;
        }

        final String videoId = streamInfo.getId();

        final String localUserId =
                RandomStringFromAlphabetGenerator.generate(ALPHABET, 32, NUMBER_GENERATOR);

        final String actionType = segment.category == SponsorBlockCategory.HIGHLIGHT
                ? "poi"
                : "skip";

        final double startInSeconds = segment.startTime / 1000.0;
        final double endInSeconds = segment.category == SponsorBlockCategory.HIGHLIGHT
                ? startInSeconds
                : segment.endTime / 1000.0;

        final String url = apiUrl + "skipSegments?"
                + "videoID=" + videoId
                + "&startTime=" + startInSeconds
                + "&endTime=" + endInSeconds
                + "&category=" + segment.category.getApiName()
                + "&userID=" + localUserId
                + "&userAgent=Mozilla/5.0"
                + "&actionType=" + actionType;
        try {
            return DownloaderImpl.getInstance().post(url, null, new byte[0]);
        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }

            return null;
        }
    }

    public static Response submitSponsorBlockSegmentVote(final Context context,
                                                         final String uuid,
                                                         final int vote) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final String apiUrl = prefs.getString(context
                .getString(R.string.sponsor_block_api_url_key), null);
        if (apiUrl == null || apiUrl.isEmpty()) {
            return null;
        }

        final String localUserId =
                RandomStringFromAlphabetGenerator.generate(ALPHABET, 32, NUMBER_GENERATOR);

        final String url = apiUrl + "voteOnSponsorTime?"
                + "UUID=" + uuid
                + "&userID=" + localUserId
                + "&type=" + vote;

        try {
            return DownloaderImpl.getInstance().post(url, null, new byte[0]);
        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        return null;
    }
}
