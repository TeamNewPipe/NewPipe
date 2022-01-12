package org.schabi.newpipe.settings.custom

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import org.schabi.newpipe.util.Localization

/**
 * An extension of a common ListPreference where it sets the duration values to human readable strings.
 *
 * The values in the entry values array will be interpreted as seconds. If the value of a specific position
 * is less than or equals to zero, its original entry title will be used.
 *
 * If the entry values array have anything other than numbers in it, an exception will be raised.
 */
class DurationListPreference : ListPreference {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    override fun onAttached() {
        super.onAttached()

        val originalEntryTitles = entries
        val originalEntryValues = entryValues
        val newEntryTitles = arrayOfNulls<CharSequence>(originalEntryValues.size)

        for (i in originalEntryValues.indices) {
            val currentDurationValue: Int
            try {
                currentDurationValue = (originalEntryValues[i] as String).toInt()
            } catch (e: NumberFormatException) {
                throw RuntimeException("Invalid number was set in the preference entry values array", e)
            }

            if (currentDurationValue <= 0) {
                newEntryTitles[i] = originalEntryTitles[i]
            } else {
                newEntryTitles[i] = Localization.localizeDuration(context, currentDurationValue)
            }
        }

        entries = newEntryTitles
    }
}
