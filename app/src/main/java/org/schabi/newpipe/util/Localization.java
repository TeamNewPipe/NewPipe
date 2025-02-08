package org.schabi.newpipe.util;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.icu.text.CompactDecimalFormat;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.math.MathUtils;
import androidx.preference.PreferenceManager;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.units.Decade;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


/*
 * Created by chschtsch on 12/29/15.
 *
 * Copyright (C) Gregory Arkhipov 2015
 * Localization.java is part of NewPipe.
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

public final class Localization {
    public static final String DOT_SEPARATOR = " • ";
    private static PrettyTime prettyTime;

    private Localization() { }

    @NonNull
    public static String concatenateStrings(final String... strings) {
        return concatenateStrings(DOT_SEPARATOR, Arrays.asList(strings));
    }

    @NonNull
    public static String concatenateStrings(final String delimiter, final List<String> strings) {
        return strings.stream()
                .filter(string -> !TextUtils.isEmpty(string))
                .collect(Collectors.joining(delimiter));
    }

    public static org.schabi.newpipe.extractor.localization.Localization getPreferredLocalization(
            final Context context) {
        return org.schabi.newpipe.extractor.localization.Localization
                .fromLocale(getPreferredLocale(context));
    }

    public static ContentCountry getPreferredContentCountry(@NonNull final Context context) {
        final String contentCountry = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_country_key),
                        context.getString(R.string.default_localization_key));
        if (contentCountry.equals(context.getString(R.string.default_localization_key))) {
            return new ContentCountry(Locale.getDefault().getCountry());
        }
        return new ContentCountry(contentCountry);
    }

    public static Locale getPreferredLocale(@NonNull final Context context) {
        return getLocaleFromPrefs(context, R.string.content_language_key);
    }

    public static Locale getAppLocale(@NonNull final Context context) {
        return getLocaleFromPrefs(context, R.string.app_language_key);
    }

    public static String localizeNumber(@NonNull final Context context, final long number) {
        return localizeNumber(context, (double) number);
    }

    public static String localizeNumber(@NonNull final Context context, final double number) {
        final NumberFormat nf = NumberFormat.getInstance(getAppLocale(context));
        return nf.format(number);
    }

    public static String formatDate(@NonNull final Context context,
                                    @NonNull final OffsetDateTime offsetDateTime) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(getAppLocale(context)).format(offsetDateTime
                        .atZoneSameInstant(ZoneId.systemDefault()));
    }

    @SuppressLint("StringFormatInvalid")
    public static String localizeUploadDate(@NonNull final Context context,
                                            @NonNull final OffsetDateTime offsetDateTime) {
        return context.getString(R.string.upload_date_text, formatDate(context, offsetDateTime));
    }

    public static String localizeViewCount(@NonNull final Context context, final long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                localizeNumber(context, viewCount));
    }

    public static String localizeStreamCount(@NonNull final Context context,
                                             final long streamCount) {
        switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN:
                return "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE:
                return context.getResources().getString(R.string.infinite_videos);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100:
                return context.getResources().getString(R.string.more_than_100_videos);
            default:
                return getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount,
                        localizeNumber(context, streamCount));
        }
    }

    public static String localizeStreamCountMini(@NonNull final Context context,
                                                 final long streamCount) {
        switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN:
                return "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE:
                return context.getResources().getString(R.string.infinite_videos_mini);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100:
                return context.getResources().getString(R.string.more_than_100_videos_mini);
            default:
                return String.valueOf(streamCount);
        }
    }

    public static String localizeWatchingCount(@NonNull final Context context,
                                               final long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                localizeNumber(context, watchingCount));
    }

    public static String shortCount(@NonNull final Context context, final long count) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompactDecimalFormat.getInstance(getAppLocale(context),
                    CompactDecimalFormat.CompactStyle.SHORT).format(count);
        }

        final double value = (double) count;
        if (count >= 1000000000) {
            return localizeNumber(context, round(value / 1000000000))
                    + context.getString(R.string.short_billion);
        } else if (count >= 1000000) {
            return localizeNumber(context, round(value / 1000000))
                    + context.getString(R.string.short_million);
        } else if (count >= 1000) {
            return localizeNumber(context, round(value / 1000))
                    + context.getString(R.string.short_thousand);
        } else {
            return localizeNumber(context, value);
        }
    }

    public static String listeningCount(@NonNull final Context context, final long listeningCount) {
        return getQuantity(context, R.plurals.listening, R.string.no_one_listening, listeningCount,
                shortCount(context, listeningCount));
    }

    public static String shortWatchingCount(@NonNull final Context context,
                                            final long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                shortCount(context, watchingCount));
    }

    public static String shortViewCount(@NonNull final Context context, final long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                shortCount(context, viewCount));
    }

    public static String shortSubscriberCount(@NonNull final Context context,
                                              final long subscriberCount) {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount,
                shortCount(context, subscriberCount));
    }

    public static String downloadCount(@NonNull final Context context, final int downloadCount) {
        return getQuantity(context, R.plurals.download_finished_notification, 0,
                downloadCount, shortCount(context, downloadCount));
    }

    public static String deletedDownloadCount(@NonNull final Context context,
                                              final int deletedCount) {
        return getQuantity(context, R.plurals.deleted_downloads_toast, 0,
                deletedCount, shortCount(context, deletedCount));
    }

    public static String replyCount(@NonNull final Context context, final int replyCount) {
        return getQuantity(context, R.plurals.replies, 0, replyCount,
                String.valueOf(replyCount));
    }

    /**
     * @param context the Android context
     * @param likeCount the like count, possibly negative if unknown
     * @return if {@code likeCount} is smaller than {@code 0}, the string {@code "-"}, otherwise
     *         the result of calling {@link #shortCount(Context, long)} on the like count
     */
    public static String likeCount(@NonNull final Context context, final int likeCount) {
        if (likeCount < 0) {
            return "-";
        } else {
            return shortCount(context, likeCount);
        }
    }

    /**
     * Get a readable text for a duration in the format {@code hours:minutes:seconds}.
     *
     * @param duration the duration in seconds
     * @return a formatted duration String or {@code 00:00} if the duration is zero.
     */
    public static String getDurationString(final long duration) {
        return DateUtils.formatElapsedTime(Math.max(duration, 0));
    }

    /**
     * Get a readable text for a duration in the format {@code hours:minutes:seconds+}. If the given
     * duration is incomplete, a plus is appended to the duration string.
     *
     * @param duration the duration in seconds
     * @param isDurationComplete whether the given duration is complete or whether info is missing
     * @param showDurationPrefix whether the duration-prefix shall be shown
     * @return a formatted duration String or {@code 00:00} if the duration is zero.
     */
    public static String getDurationString(final long duration, final boolean isDurationComplete,
                                           final boolean showDurationPrefix) {
        final String output = getDurationString(duration);
        final String durationPrefix = showDurationPrefix ? "⏱ " : "";
        final String durationPostfix = isDurationComplete ? "" : "+";
        return durationPrefix + output + durationPostfix;
    }

    /**
     * Localize an amount of seconds into a human readable string.
     *
     * <p>The seconds will be converted to the closest whole time unit.
     * <p>For example, 60 seconds would give "1 minute", 119 would also give "1 minute".
     *
     * @param context        used to get plurals resources.
     * @param durationInSecs an amount of seconds.
     * @return duration in a human readable string.
     */
    @NonNull
    public static String localizeDuration(@NonNull final Context context,
                                          final int durationInSecs) {
        if (durationInSecs < 0) {
            throw new IllegalArgumentException("duration can not be negative");
        }

        final int days = (int) (durationInSecs / (24 * 60 * 60L));
        final int hours = (int) (durationInSecs % (24 * 60 * 60L) / (60 * 60L));
        final int minutes = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) / 60L);
        final int seconds = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) % 60L);

        final Resources resources = context.getResources();

        if (days > 0) {
            return resources.getQuantityString(R.plurals.days, days, days);
        } else if (hours > 0) {
            return resources.getQuantityString(R.plurals.hours, hours, hours);
        } else if (minutes > 0) {
            return resources.getQuantityString(R.plurals.minutes, minutes, minutes);
        } else {
            return resources.getQuantityString(R.plurals.seconds, seconds, seconds);
        }
    }

    /**
     * Get the localized name of an audio track.
     *
     * <p>Examples of results returned by this method:</p>
     * <ul>
     *     <li>English (original)</li>
     *     <li>English (descriptive)</li>
     *     <li>Spanish (dubbed)</li>
     * </ul>
     *
     * @param context the context used to get the app language
     * @param track   an {@link AudioStream} of the track
     * @return the localized name of the audio track
     */
    public static String audioTrackName(@NonNull final Context context, final AudioStream track) {
        final String name;
        if (track.getAudioLocale() != null) {
            name = track.getAudioLocale().getDisplayLanguage(getAppLocale(context));
        } else if (track.getAudioTrackName() != null) {
            name = track.getAudioTrackName();
        } else {
            name = context.getString(R.string.unknown_audio_track);
        }

        if (track.getAudioTrackType() != null) {
            final String trackType = audioTrackType(context, track.getAudioTrackType());
            return context.getString(R.string.audio_track_name, name, trackType);
        }
        return name;
    }

    @NonNull
    private static String audioTrackType(@NonNull final Context context,
                                         @NonNull final AudioTrackType trackType) {
        return switch (trackType) {
            case ORIGINAL -> context.getString(R.string.audio_track_type_original);
            case DUBBED -> context.getString(R.string.audio_track_type_dubbed);
            case DESCRIPTIVE -> context.getString(R.string.audio_track_type_descriptive);
            case SECONDARY -> context.getString(R.string.audio_track_type_secondary);
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Pretty Time
    //////////////////////////////////////////////////////////////////////////*/

    public static void initPrettyTime(@NonNull final PrettyTime time) {
        prettyTime = time;
        // Do not use decades as YouTube doesn't either.
        prettyTime.removeUnit(Decade.class);
    }

    public static PrettyTime resolvePrettyTime(@NonNull final Context context) {
        return new PrettyTime(getAppLocale(context));
    }

    public static String relativeTime(@NonNull final OffsetDateTime offsetDateTime) {
        return prettyTime.formatUnrounded(offsetDateTime);
    }

    /**
     * @param context the Android context; if {@code null} then even if in debug mode and the
     *                setting is enabled, {@code textual} will not be shown next to {@code parsed}
     * @param parsed  the textual date or time ago parsed by NewPipeExtractor, or {@code null} if
     *                the extractor could not parse it
     * @param textual the original textual date or time ago string as provided by services
     * @return {@link #relativeTime(OffsetDateTime)} is used if {@code parsed != null}, otherwise
     *         {@code textual} is returned. If in debug mode, {@code context != null},
     *         {@code parsed != null} and the relevant setting is enabled, {@code textual} will
     *         be appended to the returned string for debugging purposes.
     */
    public static String relativeTimeOrTextual(@Nullable final Context context,
                                               @Nullable final DateWrapper parsed,
                                               final String textual) {
        if (parsed == null) {
            return textual;
        } else if (DEBUG && context != null && PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_original_time_ago_key), false)) {
            return relativeTime(parsed.offsetDateTime()) + " (" + textual + ")";
        } else {
            return relativeTime(parsed.offsetDateTime());
        }
    }

    public static void assureCorrectAppLanguage(final Context c) {
        final Resources res = c.getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        final Configuration conf = res.getConfiguration();
        conf.setLocale(getAppLocale(c));
        res.updateConfiguration(conf, dm);
    }

    private static Locale getLocaleFromPrefs(@NonNull final Context context,
                                             @StringRes final int prefKey) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        final String defaultKey = context.getString(R.string.default_localization_key);
        final String languageCode = sp.getString(context.getString(prefKey), defaultKey);

        if (languageCode.equals(defaultKey)) {
            return Locale.getDefault();
        } else {
            return Locale.forLanguageTag(languageCode);
        }
    }

    private static double round(final double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String getQuantity(@NonNull final Context context,
                                      @PluralsRes final int pluralId,
                                      @StringRes final int zeroCaseStringId,
                                      final long count,
                                      final String formattedCount) {
        if (count == 0) {
            return context.getString(zeroCaseStringId);
        }

        // As we use the already formatted count
        // is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category,
        // or some language have some specific rule... then we have to change it)
        final int safeCount = (int) MathUtils.clamp(count, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return context.getResources().getQuantityString(pluralId, safeCount, formattedCount);
    }
}
