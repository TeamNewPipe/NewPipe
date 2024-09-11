package com.kt.apps.video.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kt.apps.video.domain.CheckNewVersion
import com.kt.apps.video.utils.AppUpdateUtils
import com.kt.apps.video.viewmodel.data.Event
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.schabi.newpipe.App
import org.schabi.newpipe.R

class ITubeAppViewModel : ViewModel() {
    private val _hintNewVersion = MutableStateFlow<CheckNewVersion.HintNewVersion?>(null)
    val hintNewVersion = _hintNewVersion.asStateFlow()
    private val _videoDetailOverlaySlideOffset = MutableStateFlow(0f)
    val videoDetailOverlaySlideOffset = _videoDetailOverlaySlideOffset.asStateFlow()
    private val miniPlayerHeight = App.getApp().resources.getDimensionPixelSize(R.dimen.mini_player_height)
    private val _miniPlayerPeekHeight = MutableStateFlow(miniPlayerHeight)
    val miniPlayerPeekHeight = _miniPlayerPeekHeight.asStateFlow()
    private val _event = MutableSharedFlow<Event>()
    val event = merge(_event.asSharedFlow(), App.getApp().iTubeIntegration.commonRepository.registerCommonEvents())
    fun checkNewVersion() = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        App.getApp().iTubeIntegration.commonRepository.checkNewVersion().collect { version ->
            when (version) {
                is CheckNewVersion.UnsupportedVersion -> {
                    _hintNewVersion.emit(null)
                    _event.emit(Event.ShowUpdateRequired(version.title, version.subtitle, version.action, version.apkFile))
                }

                is CheckNewVersion.HintNewVersion -> {
                    // update state view model
                    _hintNewVersion.emit(version)
                }

                is CheckNewVersion.Gone -> {
                    _hintNewVersion.emit(null)
                }
            }
        }
    }

    fun onTapDialogPositiveAction(dialog: Event.ShowUpdateRequired) {
        AppUpdateUtils.installApkFile(App.getApp(), dialog.file)
    }

    fun onTapNewVersionHintAction(newVersion: CheckNewVersion.HintNewVersion) {
        AppUpdateUtils.installApkFile(App.getApp(), newVersion.apkFile)
    }

    fun onTapNewVersionHintCloseAction(hintNewVersion: CheckNewVersion.HintNewVersion) = viewModelScope.launch {
        App.getApp().iTubeIntegration.commonRepository.hideNewVersionHint(hintNewVersion)
    }

    fun hideVideoDetail() = viewModelScope.launch {
        _event.emit(Event.HideVideoDetail.Origin)
    }

    fun onVideoDetailOverlaySlide(slideOffset: Float) = viewModelScope.launch {
        _videoDetailOverlaySlideOffset.emit(slideOffset)
    }

    fun onUpdateBottomNavigationViewHeight(value: Int) = viewModelScope.launch {
        _miniPlayerPeekHeight.emit(miniPlayerHeight + value)
    }

    companion object {
        fun getFactory() = viewModelFactory {
            initializer {
                ITubeAppViewModel()
            }
        }
    }
}
