package org.schabi.newpipe.views

/** Keeps names optional and never duplicates them in the underlying message data. */
object DanmakuTextFormatter {
    @JvmStatic
    fun format(name: String?, text: String, includeName: Boolean): String {
        val author = name.orEmpty().trim().take(80)
        return if (includeName && author.isNotEmpty()) "$author: $text" else text
    }
}
