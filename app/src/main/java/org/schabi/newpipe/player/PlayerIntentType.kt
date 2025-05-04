package org.schabi.newpipe.player

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// We model this as an enum class plus one struct for each enum value
// so we can consume it from Java properly. After converting to Kotlin,
// we could switch to a sealed enum class & a proper Kotlin `when` match.

@Parcelize
enum class PlayerIntentType : Parcelable {
    Enqueue,
    EnqueueNext,
    AllOthers
}
