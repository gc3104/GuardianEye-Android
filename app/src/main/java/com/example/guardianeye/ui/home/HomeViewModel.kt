package com.example.guardianeye.ui.home

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.webrtc.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val _webRtcClient = MutableStateFlow<WebRtcClient?>(null)

    private val _viewState = MutableStateFlow(HomeViewState())
    val viewState: StateFlow<HomeViewState> = _viewState.asStateFlow()

    init {
        loadStreamUrl()
        loadRecentFootage()
    }

    private fun loadStreamUrl() {
        viewModelScope.launch {
            val url = preferenceManager.getStreamUrl()
            if (!url.isNullOrEmpty()) {
                _viewState.update { it.copy(streamUrl = url) }
            }
        }
    }

    private fun loadRecentFootage() {
        viewModelScope.launch {
            val savedUriString = preferenceManager.getFootageDirectory()
            if (!savedUriString.isNullOrEmpty()) {
                try {
                    val uri = savedUriString.toUri()
                    val context = getApplication<Application>()
                    val persistedUriPermissions = context.contentResolver.persistedUriPermissions
                    val hasPermission =
                        persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

                    if (hasPermission) {
                        val loadedFootage = withContext(Dispatchers.IO) {
                            val list = mutableListOf<Uri>()
                            val docFile = DocumentFile.fromTreeUri(context, uri)
                            docFile?.listFiles()?.sortedByDescending { it.lastModified() }?.take(5)
                                ?.forEach { file ->
                                    if (file.isFile && (file.type?.startsWith("video/") == true || file.name?.endsWith(
                                            ".mp4"
                                        ) == true)
                                    ) {
                                        list.add(file.uri)
                                    }
                                }
                            list
                        }
                        _viewState.update { it.copy(recentFootage = loadedFootage) }
                    }
                } catch (e: Exception) {
                    // Handle exception
                }
            }
        }
    }

    fun startStream() {
        val userId = auth.currentUser?.uid ?: return
        _viewState.value.streamUrl?.let { baseUrl ->
            // Normalize the URL to avoid double "/ws/" prefixes
            val cleanedBaseUrl = baseUrl.removeSuffix("/")
            val wsUrl = when {
                cleanedBaseUrl.endsWith("/ws") -> "$cleanedBaseUrl/$userId"
                cleanedBaseUrl.contains("/ws/") -> cleanedBaseUrl // Likely already correct
                else -> "$cleanedBaseUrl/ws/$userId"
            }

            if (_webRtcClient.value == null) {
                val client = WebRtcClient(
                    getApplication(), wsUrl,
                    object : WebRtcClient.Listener {
                        override fun onStatusChanged(status: WebRtcClient.ConnectionStatus) {
                            _viewState.update { it.copy(connectionStatus = status) }
                        }

                        override fun onVideoTrack(videoTrack: VideoTrack) {
                            _viewState.update { it.copy(videoTrack = videoTrack) }
                        }

                        override fun onAudioTrack(audioTrack: AudioTrack) {
                            _viewState.update { it.copy(audioTrack = audioTrack) }
                        }

                        override fun onDisconnected() {
                            _viewState.update {
                                it.copy(
                                    videoTrack = null,
                                    audioTrack = null,
                                    eglBaseContext = null,
                                    connectionStatus = WebRtcClient.ConnectionStatus.DISCONNECTED,
                                    isAudioTransmitting = false,
                                    isRemoteAudioEnabled = true,
                                    remoteAudioVolume = 1f
                                )
                            }
                            _webRtcClient.value = null
                        }
                    })
                _webRtcClient.value = client
                _viewState.update { it.copy(eglBaseContext = client.eglBase.eglBaseContext) }
                client.connect()
            }
        }
    }

    fun disconnect() {
        _webRtcClient.value?.disconnect()
    }

    fun toggleAudio(enabled: Boolean) {
        _webRtcClient.value?.toggleAudio(enabled)
        _viewState.update { it.copy(isAudioTransmitting = enabled) }
    }

    fun toggleRemoteAudio(enabled: Boolean) {
        _webRtcClient.value?.toggleRemoteAudio(enabled)
        _viewState.update { it.copy(isRemoteAudioEnabled = enabled) }
    }

    fun setRemoteAudioVolume(volume: Float) {
        _webRtcClient.value?.setRemoteAudioVolume(volume.toDouble())
        _viewState.update { it.copy(remoteAudioVolume = volume) }
    }

    override fun onCleared() {
        _webRtcClient.value?.release()
        super.onCleared()
    }
}

data class HomeViewState(
    val streamUrl: String? = null,
    val videoTrack: VideoTrack? = null,
    val audioTrack: AudioTrack? = null,
    val eglBaseContext: EglBase.Context? = null,
    val connectionStatus: WebRtcClient.ConnectionStatus = WebRtcClient.ConnectionStatus.DISCONNECTED,
    val isAudioTransmitting: Boolean = false,
    val isRemoteAudioEnabled: Boolean = true,
    val remoteAudioVolume: Float = 1f,
    val recentFootage: List<Uri> = emptyList()
)
