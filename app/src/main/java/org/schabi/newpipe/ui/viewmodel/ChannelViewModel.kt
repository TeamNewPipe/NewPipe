package org.schabi.newpipe.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo

class ChannelViewModel : ViewModel() {
    private val _channelInfo = MutableStateFlow<ChannelInfo?>(null)
    val channelInfo: StateFlow<ChannelInfo?> = _channelInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadChannel(serviceId: Int, url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val info = withContext(Dispatchers.IO) {
                    ChannelInfo.getInfo(NewPipe.getService(serviceId), url)
                }
                _channelInfo.value = info
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
