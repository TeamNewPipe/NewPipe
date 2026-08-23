package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.RandomStringFromAlphabetGenerator;
import org.schabi.newpipe.views.MarkableSeekBar;
import org.schabi.newpipe.views.SeekBarMarker;

import java.security.SecureRandom;

public final class SponsorBlockHelper {

    private static final String USER_ID_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private SponsorBlockHelper() {
    }

    public static String getUserId(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.sponsor_block_user_id_key);
        String userId = prefs.getString(key, null);
        if (userId == null) {
            userId = RandomStringFromAlphabetGenerator.generate(
                    USER_ID_ALPHABET, 32, new SecureRandom());
            prefs.edit().putString(key, userId).apply();
        }
        return userId;
    }

    public static Integer convertCategoryToColor(
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
            default:
                throw new IllegalArgumentException("Unknown category: " + category);
        }
    }

    public static void markSegments(
            final Context context,
            final MarkableSeekBar seekBar,
            @NonNull final StreamInfo streamInfo
    ) {
        seekBar.clearMarkers();

        final SponsorBlockSegment[] sponsorBlockSegments = streamInfo.getSponsorBlockSegments();

        if (sponsorBlockSegments == null) {
            return;
        }

        for (final SponsorBlockSegment sponsorBlockSegment : sponsorBlockSegments) {
            final Integer color = convertCategoryToColor(
                    sponsorBlockSegment.category, context);

            // if null, then this category should not be marked
            if (color == null) {
                continue;
            }

            // duration is in seconds, we need milliseconds
            final long length = streamInfo.getDuration() * 1000;

            final SeekBarMarker seekBarMarker =
                    new SeekBarMarker(sponsorBlockSegment.startTime, sponsorBlockSegment.endTime,
                            length, color);
            seekBar.seekBarMarkers.add(seekBarMarker);
        }

        seekBar.drawMarkers();
    }

    public static String convertCategoryToFriendlyName(final Context context,
                                                       final SponsorBlockCategory category) {
        switch (category) {
            case SPONSOR:
                return context.getString(R.string.sponsor_block_category_sponsor);
            case INTRO:
                return context.getString(R.string.sponsor_block_category_intro);
            case OUTRO:
                return context.getString(R.string.sponsor_block_category_outro);
            case INTERACTION:
                return context.getString(R.string.sponsor_block_category_interaction);
            case HIGHLIGHT:
                return context.getString(R.string.sponsor_block_category_highlight);
            case SELF_PROMO:
                return context.getString(R.string.sponsor_block_category_self_promo);
            case NON_MUSIC:
                return context.getString(R.string.sponsor_block_category_non_music);
            case PREVIEW:
                return context.getString(R.string.sponsor_block_category_preview);
            case FILLER:
                return context.getString(R.string.sponsor_block_category_filler);
            case PENDING:
                return context.getString(R.string.sponsor_block_category_pending);
            default:
                throw new IllegalArgumentException("Unknown category: " + category);
        }
    }

    public static String convertCategoryToSkipMessage(final Context context,
                                                      final SponsorBlockCategory category) {
        switch (category) {
            case SPONSOR:
                return context.getString(R.string.sponsor_block_skip_sponsor_toast);
            case INTRO:
                return context.getString(R.string.sponsor_block_skip_intro_toast);
            case OUTRO:
                return context.getString(R.string.sponsor_block_skip_outro_toast);
            case INTERACTION:
                return context.getString(R.string.sponsor_block_skip_interaction_toast);
            case HIGHLIGHT:
                return ""; // this should never happen
            case SELF_PROMO:
                return context.getString(R.string.sponsor_block_skip_self_promo_toast);
            case NON_MUSIC:
                return context.getString(R.string.sponsor_block_skip_non_music_toast);
            case PREVIEW:
                return context.getString(R.string.sponsor_block_skip_preview_toast);
            case FILLER:
                return context.getString(R.string.sponsor_block_skip_filler_toast);
            case PENDING:
                return context.getString(R.string.sponsor_block_skip_pending_toast);
            default:
                throw new IllegalArgumentException("Unknown category: " + category);
        }
    }
}
