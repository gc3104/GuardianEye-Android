package com.example.guardianeye.webrtc

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebRtcClient(
    private val application: Application,
    private val serverUrl: String,
    private val listener: Listener
) {

    companion object {
        private var isInitialized = false
        fun initialize(context: Context) {
            if (!isInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .createInitializationOptions()
                )
                isInitialized = true
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    val eglBase: EglBase = EglBase.create()

    private val audioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val factory: PeerConnectionFactory
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnection: PeerConnection? = null
    private var webSocket: WebSocket? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    init {
        initialize(application)

        audioDeviceModule = JavaAudioDeviceModule.builder(application)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /* ======================= CONNECTION ======================= */

    fun connect() {
        executor.execute {
            Log.d("WebRtcClient", "Connecting to $serverUrl")
            listener.onStatusChanged(ConnectionStatus.CONNECTING)

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            routeToSpeaker()

            createLocalAudioTrack()

            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()

            val origin = serverUrl.replace("wss://", "https://").replace("ws://", "http://")
                .substringBefore("/ws")

            val request = Request.Builder()
                .url(serverUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Bypass-Tunnel-Reminder", "true")
                .header("X-Tunnel-Skip-Gui", "true")
                .header("ngrok-skip-browser-warning", "true")
                .header("Origin", origin)
                .build()

            webSocket = client.newWebSocket(request, socketListener)
        }
    }

    private fun createLocalAudioTrack() {
        if (localAudioTrack == null || localAudioTrack?.isDisposed == true) {
            Log.d("WebRtcClient", "Creating local audio track")
            val constraints = MediaConstraints()
            audioSource = factory.createAudioSource(constraints)
            localAudioTrack = factory.createAudioTrack("mic", audioSource)
            localAudioTrack?.setEnabled(false) // Start muted
        }
    }

    fun disconnect() {
        executor.execute {
            cleanup()
            listener.onStatusChanged(ConnectionStatus.DISCONNECTED)
            listener.onDisconnected()
        }
    }

    fun release() {
        executor.execute {
            cleanup()
            factory.dispose()
            audioDeviceModule?.release()
            eglBase.release()
            executor.shutdown()
        }
    }

    /* ======================= AUDIO ======================= */

    fun toggleAudio(enabled: Boolean) {
        executor.execute {
            Log.d("WebRtcClient", "Toggling local audio: $enabled")
            val track = localAudioTrack ?: return@execute
            if (!track.isDisposed) {
                track.setEnabled(enabled)
                if (enabled) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    routeToSpeaker()
                }
            }
        }
    }

    private val MediaStreamTrack.isDisposed: Boolean
        get() = try {
            state() == null
            false
        } catch (e: Exception) {
            true
        }

    fun toggleRemoteAudio(enabled: Boolean) {
        executor.execute {
            remoteAudioTrack?.let {
                if (!it.isDisposed) it.setEnabled(enabled)
            }
        }
    }

    fun setRemoteAudioVolume(volume: Double) {
        executor.execute {
            remoteAudioTrack?.let {
                if (!it.isDisposed) it.setVolume(volume)
            }
        }
    }

    /* ======================= PEER ======================= */

    private fun createPeerConnection() {
        Log.d("WebRtcClient", "Creating PeerConnection")
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(config, pcObserver)

        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        val audioTransceiver = peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        )
        audioTransceiver?.sender?.setTrack(localAudioTrack, true)

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                executor.execute {
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            executor.execute { sendSdp(sdp) }
                        }
                    }, sdp)
                }
            }
        }, MediaConstraints())
    }

    /* ======================= SIGNALING ======================= */

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            executor.execute { createPeerConnection() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            executor.execute { handleMessage(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            executor.execute {
                Log.e("WebRtcClient", "WS failure", t)
                disconnect()
                listener.onStatusChanged(ConnectionStatus.ERROR)
            }
        }
    }

    private fun handleMessage(message: String) {
        val json = JSONObject(message)

        when {
            json.has("sdp") -> {
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(json.getString("type")),
                    json.getString("sdp")
                )
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
            }

            json.has("candidate") -> {
                val c = json.getJSONObject("candidate")
                peerConnection?.addIceCandidate(
                    IceCandidate(
                        c.getString("sdpMid"),
                        c.getInt("sdpMLineIndex"),
                        c.getString("candidate")
                    )
                )
            }
        }
    }

    private fun sendSdp(sdp: SessionDescription) {
        webSocket?.send(
            JSONObject()
                .put("type", sdp.type.canonicalForm())
                .put("sdp", sdp.description)
                .toString()
        )
    }

    /* ======================= OBSERVERS ======================= */

    private val pcObserver = object : PeerConnection.Observer {
        override fun onTrack(transceiver: RtpTransceiver) {
            executor.execute {
                val track = transceiver.receiver.track() ?: return@execute
                when (track) {
                    is VideoTrack -> listener.onVideoTrack(track)
                    is AudioTrack -> {
                        remoteAudioTrack = track
                        track.setEnabled(true)
                        listener.onAudioTrack(track)
                    }
                }
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            executor.execute {
                webSocket?.send(
                    JSONObject()
                        .put("type", "candidate")
                        .put("candidate", JSONObject()
                            .put("candidate", candidate.sdp)
                            .put("sdpMid", candidate.sdpMid)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex))
                        .toString()
                )
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            executor.execute {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED ->
                        listener.onStatusChanged(ConnectionStatus.CONNECTED)

                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        listener.onStatusChanged(ConnectionStatus.ERROR)
                        disconnect()
                    }
                    else -> {}
                }
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(p0: Array<IceCandidate>) {}
        override fun onAddStream(p0: MediaStream) {}
        override fun onRemoveStream(p0: MediaStream) {}
        override fun onDataChannel(p0: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
    }

    /* ======================= UTIL ======================= */

    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false

        localAudioTrack?.let { if (!it.isDisposed) it.dispose() }
        audioSource?.let { it.dispose() }
        localAudioTrack = null
        audioSource = null
        remoteAudioTrack = null

        peerConnection?.close()
        peerConnection = null

        webSocket?.close(1000, "bye")
        webSocket = null
    }

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    interface Listener {
        fun onStatusChanged(status: ConnectionStatus)
        fun onVideoTrack(videoTrack: VideoTrack)
        fun onAudioTrack(audioTrack: AudioTrack)
        fun onDisconnected()
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}
