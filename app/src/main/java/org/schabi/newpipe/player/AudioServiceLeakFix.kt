package org.schabi.newpipe.player

import android.content.Context
import android.content.ContextWrapper

/**
 * Fixes a leak caused by AudioManager using an Activity context.
 * Tracked at https://android-review.googlesource.com/#/c/140481/1 and
 * https://github.com/square/leakcanary/issues/205
 * Source:
 * https://gist.github.com/jankovd/891d96f476f7a9ce24e2
 */
class AudioServiceLeakFix(base: Context) : ContextWrapper(base) {

    override fun getSystemService(name: String): Any? {
        return if (Context.AUDIO_SERVICE == name) {
            applicationContext.getSystemService(name)
        } else {
            super.getSystemService(name)
        }
    }

    companion object {
        @JvmStatic
        fun preventLeakOf(base: Context): ContextWrapper {
            return AudioServiceLeakFix(base)
        }
    }
}
