package org.schabi.newpipe.player.event

open interface OnKeyDownListener {
    fun onKeyDown(keyCode: Int): Boolean
}
