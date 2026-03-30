package org.schabi.newpipe.platform

import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Destination
import org.schabi.newpipe.NewPipeComposeActivity

fun Context.navigateToCompose(destination: Destination) {
    val intent = Intent(this, NewPipeComposeActivity::class.java).apply {
        putExtra(Constants.INTENT_SCREEN_KEY,
            Json.encodeToString(destination))
    }
    startActivity(intent)
}