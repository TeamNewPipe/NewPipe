package org.schabi.newpipe.util

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about the saved state on the disk.
 * Path to the saved file.
 *
 * @property prefixFileSaved Prefix of the saved file
 * @property pathFileSaved Path to the saved file
 */
@Parcelize
class SavedState(val prefixFileSaved: String, val pathFileSaved: String) : Parcelable {
    override fun toString() = "$prefixFileSaved > $pathFileSaved"
}
