package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.os.Build
import android.text.TextUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.math.MathUtils
import androidx.preference.PreferenceManager
import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.units.Decade
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Arrays
import java.util.Locale
import java.util.stream.Collectors

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
object Localization {
    const val DOT_SEPARATOR = " • "
    private var prettyTime: PrettyTime? = null
    fun concatenateStrings(vararg strings: String?): String {
        return concatenateStrings(DOT_SEPARATOR, Arrays.asList(*strings))
    }

    fun concatenateStrings(delimiter: String?, strings: List<String?>): String {
        return strings.stream()
                .filter({ string: String? -> !TextUtils.isEmpty(string) })
                .collect(Collectors.joining(delimiter))
    }

    fun getPreferredLocalization(
            context: Context): Localization {
        return Localization
                .fromLocale(getPreferredLocale(context))
    }

    fun getPreferredContentCountry(context: Context): ContentCountry {
        val contentCountry = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_country_key),
                        context.getString(R.string.default_localization_key))
        return if ((contentCountry == context.getString(R.string.default_localization_key))) {
            ContentCountry(Locale.getDefault().country)
        } else ContentCountry((contentCountry)!!)
    }

    fun getPreferredLocale(context: Context): Locale {
        return getLocaleFromPrefs(context, R.string.content_language_key)
    }

    fun getAppLocale(context: Context): Locale {
        return getLocaleFromPrefs(context, R.string.app_language_key)
    }

    fun localizeNumber(context: Context, number: Long): String {
        return localizeNumber(context, number.toDouble())
    }

    fun localizeNumber(context: Context, number: Double): String {
        val nf = NumberFormat.getInstance(getAppLocale(context))
        return nf.format(number)
    }

    fun formatDate(context: Context,
                   offsetDateTime: OffsetDateTime): String {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(getAppLocale(context)).format(offsetDateTime
                        .atZoneSameInstant(ZoneId.systemDefault()))
    }

    @SuppressLint("StringFormatInvalid")
    fun localizeUploadDate(context: Context,
                           offsetDateTime: OffsetDateTime): String {
        return context.getString(R.string.upload_date_text, formatDate(context, offsetDateTime))
    }

    fun localizeViewCount(context: Context, viewCount: Long): String {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                localizeNumber(context, viewCount))
    }

    fun localizeStreamCount(context: Context,
                            streamCount: Long): String {
        return when (streamCount.toInt()) {
            ListExtractor.ITEM_COUNT_UNKNOWN.toInt() -> ""
            ListExtractor.ITEM_COUNT_INFINITE.toInt() -> context.resources.getString(R.string.infinite_videos)
            ListExtractor.ITEM_COUNT_MORE_THAN_100.toInt() -> context.resources.getString(R.string.more_than_100_videos)
            else -> getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount,
                    localizeNumber(context, streamCount))
        }
    }

    fun localizeStreamCountMini(context: Context,
                                streamCount: Long): String {
        return when (streamCount.toInt()) {
            ListExtractor.ITEM_COUNT_UNKNOWN.toInt() -> ""
            ListExtractor.ITEM_COUNT_INFINITE.toInt() -> context.resources.getString(R.string.infinite_videos_mini)
            ListExtractor.ITEM_COUNT_MORE_THAN_100.toInt() -> context.resources.getString(R.string.more_than_100_videos_mini)
            else -> streamCount.toString()
        }
    }

    fun localizeWatchingCount(context: Context,
                              watchingCount: Long): String {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                localizeNumber(context, watchingCount))
    }

    fun shortCount(context: Context, count: Long): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompactDecimalFormat.getInstance(getAppLocale(context),
                    CompactDecimalFormat.CompactStyle.SHORT).format(count)
        }
        val value = count.toDouble()
        return if (count >= 1000000000) {
            (localizeNumber(context, round(value / 1000000000))
                    + context.getString(R.string.short_billion))
        } else if (count >= 1000000) {
            (localizeNumber(context, round(value / 1000000))
                    + context.getString(R.string.short_million))
        } else if (count >= 1000) {
            (localizeNumber(context, round(value / 1000))
                    + context.getString(R.string.short_thousand))
        } else {
            localizeNumber(context, value)
        }
    }

    fun listeningCount(context: Context, listeningCount: Long): String {
        return getQuantity(context, R.plurals.listening, R.string.no_one_listening, listeningCount,
                shortCount(context, listeningCount))
    }

    fun shortWatchingCount(context: Context,
                           watchingCount: Long): String {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                shortCount(context, watchingCount))
    }

    fun shortViewCount(context: Context, viewCount: Long): String {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                shortCount(context, viewCount))
    }

    fun shortSubscriberCount(context: Context,
                             subscriberCount: Long): String {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount,
                shortCount(context, subscriberCount))
    }

    fun downloadCount(context: Context, downloadCount: Int): String {
        return getQuantity(context, R.plurals.download_finished_notification, 0,
                downloadCount.toLong(), shortCount(context, downloadCount.toLong()))
    }

    fun deletedDownloadCount(context: Context,
                             deletedCount: Int): String {
        return getQuantity(context, R.plurals.deleted_downloads_toast, 0,
                deletedCount.toLong(), shortCount(context, deletedCount.toLong()))
    }

    fun replyCount(context: Context, replyCount: Int): String {
        return getQuantity(context, R.plurals.replies, 0, replyCount.toLong(), replyCount.toString())
    }

    /**
     * @param context the Android context
     * @param likeCount the like count, possibly negative if unknown
     * @return if `likeCount` is smaller than `0`, the string `"-"`, otherwise
     * the result of calling [.shortCount] on the like count
     */
    fun likeCount(context: Context, likeCount: Int): String {
        return if (likeCount < 0) {
            "-"
        } else {
            shortCount(context, likeCount.toLong())
        }
    }

    /**
     * Get a readable text for a duration in the format `days:hours:minutes:seconds`.
     * Prepended zeros are removed.
     * @param duration the duration in seconds
     * @return a formatted duration String or `0:00` if the duration is zero.
     */
    fun getDurationString(duration: Long): String {
        return getDurationString(duration, true)
    }

    /**
     * Get a readable text for a duration in the format `days:hours:minutes:seconds+`.
     * Prepended zeros are removed. If the given duration is incomplete, a plus is appended to the
     * duration string.
     * @param duration the duration in seconds
     * @param isDurationComplete whether the given duration is complete or whether info is missing
     * @return a formatted duration String or `0:00` if the duration is zero.
     */
    fun getDurationString(duration: Long, isDurationComplete: Boolean): String {
        val output: String
        val days = duration / (24 * 60 * 60L) /* greater than a day */
        val hours = duration % (24 * 60 * 60L) / (60 * 60L) /* greater than an hour */
        val minutes = duration % (24 * 60 * 60L) % (60 * 60L) / 60L
        val seconds = duration % 60L
        output = if (duration < 0) {
            "0:00"
        } else if (days > 0) {
            //handle days
            String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds)
        } else if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
        val durationPostfix = if (isDurationComplete) "" else "+"
        return output + durationPostfix
    }

    /**
     * Localize an amount of seconds into a human readable string.
     *
     *
     * The seconds will be converted to the closest whole time unit.
     *
     * For example, 60 seconds would give "1 minute", 119 would also give "1 minute".
     *
     * @param context        used to get plurals resources.
     * @param durationInSecs an amount of seconds.
     * @return duration in a human readable string.
     */
    fun localizeDuration(context: Context,
                         durationInSecs: Int): String {
        require(!(durationInSecs < 0)) { "duration can not be negative" }
        val days = (durationInSecs / (24 * 60 * 60L)).toInt()
        val hours = (durationInSecs % (24 * 60 * 60L) / (60 * 60L)).toInt()
        val minutes = (durationInSecs % (24 * 60 * 60L) % (60 * 60L) / 60L).toInt()
        val seconds = (durationInSecs % (24 * 60 * 60L) % (60 * 60L) % 60L).toInt()
        val resources = context.resources
        return if (days > 0) {
            resources.getQuantityString(R.plurals.days, days, days)
        } else if (hours > 0) {
            resources.getQuantityString(R.plurals.hours, hours, hours)
        } else if (minutes > 0) {
            resources.getQuantityString(R.plurals.minutes, minutes, minutes)
        } else {
            resources.getQuantityString(R.plurals.seconds, seconds, seconds)
        }
    }

    /**
     * Get the localized name of an audio track.
     *
     *
     * Examples of results returned by this method:
     *
     *  * English (original)
     *  * English (descriptive)
     *  * Spanish (dubbed)
     *
     *
     * @param context the context used to get the app language
     * @param track   an [AudioStream] of the track
     * @return the localized name of the audio track
     */
    fun audioTrackName(context: Context, track: AudioStream?): String? {
        val name: String?
        name = if (track!!.audioLocale != null) {
            track.audioLocale!!.getDisplayLanguage(getAppLocale(context))
        } else if (track.audioTrackName != null) {
            track.audioTrackName
        } else {
            context.getString(R.string.unknown_audio_track)
        }
        if (track.audioTrackType != null) {
            val trackType = audioTrackType(context, track.audioTrackType)
            if (trackType != null) {
                return context.getString(R.string.audio_track_name, name, trackType)
            }
        }
        return name
    }

    private fun audioTrackType(context: Context,
                               trackType: AudioTrackType?): String? {
        when (trackType) {
            AudioTrackType.ORIGINAL -> return context.getString(R.string.audio_track_type_original)
            AudioTrackType.DUBBED -> return context.getString(R.string.audio_track_type_dubbed)
            AudioTrackType.DESCRIPTIVE -> return context.getString(R.string.audio_track_type_descriptive)
        }
        return null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Pretty Time
    ////////////////////////////////////////////////////////////////////////// */
    fun initPrettyTime(time: PrettyTime) {
        prettyTime = time
        // Do not use decades as YouTube doesn't either.
        prettyTime!!.removeUnit(Decade::class.java)
    }

    fun resolvePrettyTime(context: Context): PrettyTime {
        return PrettyTime(getAppLocale(context))
    }

    fun relativeTime(offsetDateTime: OffsetDateTime): String {
        return prettyTime!!.formatUnrounded(offsetDateTime)
    }

    /**
     * @param context the Android context; if `null` then even if in debug mode and the
     * setting is enabled, `textual` will not be shown next to `parsed`
     * @param parsed  the textual date or time ago parsed by NewPipeExtractor, or `null` if
     * the extractor could not parse it
     * @param textual the original textual date or time ago string as provided by services
     * @return [.relativeTime] is used if `parsed != null`, otherwise
     * `textual` is returned. If in debug mode, `context != null`,
     * `parsed != null` and the relevant setting is enabled, `textual` will
     * be appended to the returned string for debugging purposes.
     */
    fun relativeTimeOrTextual(context: Context?,
                              parsed: DateWrapper?,
                              textual: String?): String? {
        return if (parsed == null) {
            textual
        } else if (MainActivity.Companion.DEBUG && (context != null) && PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.show_original_time_ago_key), false)) {
            relativeTime(parsed.offsetDateTime()) + " (" + textual + ")"
        } else {
            relativeTime(parsed.offsetDateTime())
        }
    }

    fun assureCorrectAppLanguage(c: Context?) {
        val res = c!!.resources
        val dm = res.displayMetrics
        val conf = res.configuration
        conf.setLocale(getAppLocale(c))
        res.updateConfiguration(conf, dm)
    }

    private fun getLocaleFromPrefs(context: Context,
                                   @StringRes prefKey: Int): Locale {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultKey = context.getString(R.string.default_localization_key)
        val languageCode = sp.getString(context.getString(prefKey), defaultKey)
        return if ((languageCode == defaultKey)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
    }

    private fun round(value: Double): Double {
        return BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    private fun getQuantity(context: Context,
                            @PluralsRes pluralId: Int,
                            @StringRes zeroCaseStringId: Int,
                            count: Long,
                            formattedCount: String): String {
        if (count == 0L) {
            return context.getString(zeroCaseStringId)
        }

        // As we use the already formatted count
        // is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category,
        // or some language have some specific rule... then we have to change it)
        val safeCount: Int = MathUtils.clamp(count, Int.MIN_VALUE, Int.MAX_VALUE).toInt()
        return context.resources.getQuantityString(pluralId, safeCount, formattedCount)
    }
}
