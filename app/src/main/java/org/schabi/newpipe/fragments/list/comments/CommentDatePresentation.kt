package org.schabi.newpipe.fragments.list.comments

import android.content.Context
import androidx.preference.PreferenceManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.util.Localization

/** The date control uses local formatting and never invents a precise source timestamp. */
object CommentDatePresentation {
    private const val EXACT_DATES = "comments_show_exact_date"

    @JvmStatic
    fun isExact(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(EXACT_DATES, false)
    }

    @JvmStatic
    fun setExact(context: Context, exact: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(EXACT_DATES, exact).apply()
    }

    @JvmStatic
    fun label(context: Context, item: CommentsInfoItem): String {
        val relative = Localization.relativeTimeOrTextual(
            context,
            item.uploadDate,
            item.textualUploadDate
        )
        if (!isExact(context)) return relative.orEmpty()
        val date = item.uploadDate ?: return relative.orEmpty()
        // An estimated upload date remains explicitly approximate.
        val localized = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(date.offsetDateTime().toInstant())
        return if (date.isApproximation) "≈ $localized" else localized
    }
}
