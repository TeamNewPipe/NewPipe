package org.schabi.newpipe.player.ui

import java.util.concurrent.CopyOnWriteArrayList

class PlayerUiList(vararg uis: PlayerUi) {
    private val uiList = CopyOnWriteArrayList<PlayerUi>()

    init {
        uiList.addAll(uis)
    }

    fun add(ui: PlayerUi) {
        uiList.add(ui)
    }

    fun remove(ui: PlayerUi) {
        uiList.remove(ui)
    }

    fun <T : PlayerUi> removeAll(clazz: Class<T>) {
        uiList.removeIf { clazz.isInstance(it) }
    }

    fun call(action: (PlayerUi) -> Unit) {
        uiList.forEach(action)
    }
}
