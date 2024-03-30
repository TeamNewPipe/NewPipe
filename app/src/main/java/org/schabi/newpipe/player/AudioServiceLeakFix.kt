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
class AudioServiceLeakFix internal constructor(base: Context?) : ContextWrapper(base) {
    public override fun getSystemService(name: String): Any {
        if ((AUDIO_SERVICE == name)) {
            return getApplicationContext().getSystemService(name)
        }
        return super.getSystemService(name)
    }

    companion object {
        fun preventLeakOf(base: Context?): ContextWrapper {
            return AudioServiceLeakFix(base)
        }
    }
}
