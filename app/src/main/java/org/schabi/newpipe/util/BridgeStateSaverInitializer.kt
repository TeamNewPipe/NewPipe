package org.schabi.newpipe.util

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import com.evernote.android.state.StateSaver
import com.livefront.bridge.Bridge
import com.livefront.bridge.SavedStateHandler
import com.livefront.bridge.ViewSavedStateHandler

/**
 * Configures Bridge's state saver.
 */
object BridgeStateSaverInitializer {

    @JvmStatic
    fun init(context: Context) {
        Bridge.initialize(
            context,
            object : SavedStateHandler {
                override fun saveInstanceState(target: Any, state: Bundle) {
                    StateSaver.saveInstanceState(target, state)
                }

                override fun restoreInstanceState(target: Any, state: Bundle?) {
                    StateSaver.restoreInstanceState(target, state)
                }
            },
            object : ViewSavedStateHandler {
                override fun <T : View> saveInstanceState(target: T, parentState: Parcelable?): Parcelable {
                    return StateSaver.saveInstanceState(target, parentState)
                }

                override fun <T : View> restoreInstanceState(target: T, state: Parcelable?): Parcelable? {
                    return StateSaver.restoreInstanceState(target, state)
                }
            }
        )
    }
}
