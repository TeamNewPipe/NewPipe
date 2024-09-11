package com.kt.apps.video.viewmodel.data

import java.io.File

sealed interface Event {
    data class ShowUpdateRequired(val title: CharSequence, val subtitle: CharSequence, val action: CharSequence, val file: File) : Event
    sealed interface HideVideoDetail : Event {
        data object Origin : HideVideoDetail
        data object Web : HideVideoDetail
    }
}
