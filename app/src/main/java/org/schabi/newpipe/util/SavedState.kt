package org.schabi.newpipe.util

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about the saved state on the disk.
 */
@Parcelize
class SavedState(
    /**
     * Get the prefix of the saved file.
     *
     * @return the file prefix
     */
    val prefixFileSaved: String,
    /**
     * Get the path to the saved file.
     *
     * @return the path to the saved file
     */
    val pathFileSaved: String
) : Parcelable {
    override fun toString() = "$prefixFileSaved > $pathFileSaved"
}
