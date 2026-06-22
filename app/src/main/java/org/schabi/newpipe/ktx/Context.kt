package org.schabi.newpipe.ktx

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.ComposeActivity
import net.newpipe.app.navigation.Destination

tailrec fun Context.findFragmentActivity(): FragmentActivity {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> throw IllegalStateException("Unable to find FragmentActivity")
    }
}

/**
 * Navigates to a given compose destination
 */
fun Context.navigateTo(destination: Destination) {
    val intent = Intent(this, ComposeActivity::class.java)
        .putExtra(Constants.INTENT_SCREEN_KEY, Json.encodeToString(destination))
    startActivity(intent)
}
