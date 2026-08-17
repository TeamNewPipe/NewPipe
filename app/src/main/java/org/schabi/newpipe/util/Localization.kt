package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.os.Build
import android.text.BidiFormatter
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.math.MathUtils
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.units.Decade
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Localization {
    private val TAG = Localization::class.java.toString()
    const val DOT_SEPARATOR = " • "
    private lateinit var prettyTime: PrettyTime

    @JvmStatic
    fun concatenateStrings(vararg strings: String): String {
        return concatenateStrings(DOT_SEPARATOR, strings.toList())
    }

    @JvmStatic
    fun concatenateStrings(delimiter: String, strings: List<String>): String {
        return strings.filter { it.isNotEmpty() }.joinToString(delimiter)
    }

    @JvmStatic
    fun localizeUserName(plainName: String): String {
        return BidiFormatter.getInstance().unicodeWrap(plainName)
    }

    @JvmStatic
    fun getPreferredLocalization(context: Context): org.schabi.newpipe.extractor.localization.Localization {
        return org.schabi.newpipe.extractor.localization.Localization.fromLocale(getPreferredLocale(context))
    }

    @JvmStatic
    fun getPreferredContentCountry(context: Context): ContentCountry {
        val contentCountry = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                context.getString(R.string.content_country_key),
                context.getString(R.string.default_localization_key)
            ) ?: context.getString(R.string.default_localization_key)
        return if (contentCountry == context.getString(R.string.default_localization_key)) {
            ContentCountry(Locale.getDefault().country)
        } else {
            ContentCountry(contentCountry)
        }
    }

    @JvmStatic
    fun getPreferredLocale(context: Context): Locale {
        return getLocaleFromPrefs(context, R.string.content_language_key)
    }

    @JvmStatic
    fun getAppLocale(): Locale {
        val customLocale = AppCompatDelegate.getApplicationLocales()[0]
        return customLocale ?: Locale.getDefault()
    }

    @JvmStatic
    fun localizeNumber(number: Long): String {
        return localizeNumber(number.toDouble())
    }

    @JvmStatic
    fun localizeNumber(number: Double): String {
        return NumberFormat.getInstance(getAppLocale()).format(number)
    }

    @JvmStatic
    fun formatDate(offsetDateTime: OffsetDateTime): String {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(getAppLocale())
            .format(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()))
    }

    @JvmStatic
    @SuppressLint("StringFormatInvalid")
    fun localizeUploadDate(context: Context, offsetDateTime: OffsetDateTime): String {
        return context.getString(R.string.upload_date_text, formatDate(offsetDateTime))
    }

    @JvmStatic
    fun localizeViewCount(context: Context, viewCount: Long): String {
        return getQuantity(
            context, R.plurals.views, R.string.no_views, viewCount,
            localizeNumber(viewCount)
        )
    }

    @JvmStatic
    fun localizeStreamCount(context: Context, streamCount: Long): String {
        return when (streamCount) {
            ListExtractor.ITEM_COUNT_UNKNOWN -> ""
            ListExtractor.ITEM_COUNT_INFINITE -> context.getString(R.string.infinite_videos)
            ListExtractor.ITEM_COUNT_MORE_THAN_100 -> context.getString(R.string.more_than_100_videos)
            else -> getQuantity(
                context, R.plurals.videos, R.string.no_videos, streamCount,
                localizeNumber(streamCount)
            )
        }
    }

    @JvmStatic
    fun localizeStreamCountMini(context: Context, streamCount: Long): String {
        return when (streamCount) {
            ListExtractor.ITEM_COUNT_UNKNOWN -> ""
            ListExtractor.ITEM_COUNT_INFINITE -> context.getString(R.string.infinite_videos_mini)
            ListExtractor.ITEM_COUNT_MORE_THAN_100 -> context.getString(R.string.more_than_100_videos_mini)
            else -> streamCount.toString()
        }
    }

    @JvmStatic
    fun localizeWatchingCount(context: Context, watchingCount: Long): String {
        return getQuantity(
            context, R.plurals.watching, R.string.no_one_watching, watchingCount,
            localizeNumber(watchingCount)
        )
    }

    @JvmStatic
    fun shortCount(context: Context, count: Long): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompactDecimalFormat.getInstance(
                getAppLocale(),
                CompactDecimalFormat.CompactStyle.SHORT
            ).format(count)
        }

        val value = count.toDouble()
        return if (count >= 1000000000) {
            val shortenedValue = value / 1000000000
            val scale = if (shortenedValue >= 100) 0 else 1
            context.getString(
                R.string.short_billion,
                localizeNumber(round(shortenedValue, scale))
            )
        } else if (count >= 1000000) {
            val shortenedValue = value / 1000000
            val scale = if (shortenedValue >= 100) 0 else 1
            context.getString(
                R.string.short_million,
                localizeNumber(round(shortenedValue, scale))
            )
        } else if (count >= 1000) {
            val shortenedValue = value / 1000
            val scale = if (shortenedValue >= 100) 0 else 1
            context.getString(
                R.string.short_thousand,
                localizeNumber(round(shortenedValue, scale))
            )
        } else {
            localizeNumber(value)
        }
    }

    @JvmStatic
    fun listeningCount(context: Context, listeningCount: Long): String {
        return getQuantity(
            context, R.plurals.listening, R.string.no_one_listening, listeningCount,
            shortCount(context, listeningCount)
        )
    }

    @JvmStatic
    fun shortWatchingCount(context: Context, watchingCount: Long): String {
        return getQuantity(
            context, R.plurals.watching, R.string.no_one_watching, watchingCount,
            shortCount(context, watchingCount)
        )
    }

    @JvmStatic
    fun shortViewCount(context: Context, viewCount: Long): String {
        return getQuantity(
            context, R.plurals.views, R.string.no_views, viewCount,
            shortCount(context, viewCount)
        )
    }

    @JvmStatic
    fun shortSubscriberCount(context: Context, subscriberCount: Long): String {
        return getQuantity(
            context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount,
            shortCount(context, subscriberCount)
        )
    }

    @JvmStatic
    fun downloadCount(context: Context, downloadCount: Int): String {
        return getQuantity(
            context, R.plurals.download_finished_notification, 0,
            downloadCount.toLong(), shortCount(context, downloadCount.toLong())
        )
    }

    @JvmStatic
    fun deletedDownloadCount(context: Context, deletedCount: Int): String {
        return getQuantity(
            context, R.plurals.deleted_downloads_toast, 0,
            deletedCount.toLong(), shortCount(context, deletedCount.toLong())
        )
    }

    @JvmStatic
    fun replyCount(context: Context, replyCount: Int): String {
        return getQuantity(
            context, R.plurals.replies, 0, replyCount.toLong(),
            replyCount.toString()
        )
    }

    @JvmStatic
    fun likeCount(context: Context, likeCount: Int): String {
        return if (likeCount < 0) {
            "-"
        } else {
            shortCount(context, likeCount.toLong())
        }
    }

    @JvmStatic
    fun getDurationString(duration: Long): String {
        return DateUtils.formatElapsedTime(duration.coerceAtLeast(0))
    }

    @JvmStatic
    fun getDurationString(
        duration: Long,
        isDurationComplete: Boolean,
        showDurationPrefix: Boolean
    ): String {
        val output = getDurationString(duration)
        val durationPrefix = if (showDurationPrefix) "⏱ " else ""
        val durationPostfix = if (isDurationComplete) "" else "+"
        return durationPrefix + output + durationPostfix
    }

    @JvmStatic
    fun localizeDuration(context: Context, durationInSecs: Int): String {
        require(durationInSecs >= 0) { "duration can not be negative" }

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

    @JvmStatic
    fun audioTrackName(context: Context, track: AudioStream): String {
        val name = when {
            track.audioLocale != null -> track.audioLocale!!.displayName
            track.audioTrackName != null -> track.audioTrackName!!
            else -> context.getString(R.string.unknown_audio_track)
        }

        return if (track.audioTrackType != null) {
            val trackType = audioTrackType(context, track.audioTrackType!!)
            context.getString(R.string.audio_track_name, name, trackType)
        } else {
            name
        }
    }

    private fun audioTrackType(context: Context, trackType: AudioTrackType): String {
        return when (trackType) {
            AudioTrackType.ORIGINAL -> context.getString(R.string.audio_track_type_original)
            AudioTrackType.DUBBED -> context.getString(R.string.audio_track_type_dubbed)
            AudioTrackType.DESCRIPTIVE -> context.getString(R.string.audio_track_type_descriptive)
            AudioTrackType.SECONDARY -> context.getString(R.string.audio_track_type_secondary)
        }
    }

    @JvmStatic
    fun initPrettyTime(time: PrettyTime) {
        prettyTime = time
        // Do not use decades as YouTube doesn't either.
        prettyTime.removeUnit(Decade::class.java)
    }

    @JvmStatic
    fun resolvePrettyTime(): PrettyTime {
        return PrettyTime(getAppLocale())
    }

    @JvmStatic
    fun relativeTime(offsetDateTime: OffsetDateTime): String {
        return prettyTime.formatUnrounded(java.util.Date.from(offsetDateTime.toInstant()))
    }

    @JvmStatic
    fun relativeTimeOrTextual(
        context: Context?,
        parsed: DateWrapper?,
        textual: String?
    ): String? {
        return if (parsed == null) {
            textual
        } else if (DEBUG && context != null && PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.show_original_time_ago_key), false)
        ) {
            relativeTime(parsed.offsetDateTime()!!) + " (" + textual + ")"
        } else {
            relativeTime(parsed.offsetDateTime()!!)
        }
    }

    private fun getLocaleFromPrefs(context: Context, prefKey: Int): Locale {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultKey = context.getString(R.string.default_localization_key)
        val languageCode = sp.getString(context.getString(prefKey), defaultKey) ?: defaultKey

        return if (languageCode == defaultKey) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
    }

    private fun round(value: Double, scale: Int): Double {
        return BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    private fun getQuantity(
        context: Context,
        @PluralsRes pluralId: Int,
        @StringRes zeroCaseStringId: Int,
        count: Long,
        formattedCount: String
    ): String {
        if (count == 0L && zeroCaseStringId != 0) {
            return context.getString(zeroCaseStringId)
        }

        val safeCount = MathUtils.clamp(count, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        return context.resources.getQuantityString(pluralId, safeCount, formattedCount)
    }

    @JvmStatic
    fun migrateAppLanguageSettingIfNecessary(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val appLanguageKey = context.getString(R.string.app_language_key)
        val appLanguageValue = sp.getString(appLanguageKey, null)
        if (appLanguageValue != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sp.edit().remove(appLanguageKey).apply()
            }
            val appLanguageDefaultValue = context.getString(R.string.default_localization_key)
            if (appLanguageValue != appLanguageDefaultValue) {
                try {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(appLanguageValue)
                    )
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Failed to migrate previous custom app language setting to public per-app language APIs")
                }
            }
        }
    }
}
