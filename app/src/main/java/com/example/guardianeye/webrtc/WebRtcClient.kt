package com.example.guardianeye.webrtc

import android.app.Application
import android.util.Log
import okhttp3.OkHttpClient
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
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebRtcClient(
    private val application: Application,
    private val serverUrl: String,
    private val listener: Listener
) {
    private val executor = Executors.newSingleThreadExecutor()
    
    // Shared EglBase for video encoding/decoding
    val eglBase: EglBase = EglBase.create()
    
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var webSocket: WebSocket? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(application)
                .createInitializationOptions()
        )
        
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun connect() {
        executor.execute {
            listener.onStatusChanged(ConnectionStatus.CONNECTING)
            // Increased timeouts to 30 seconds to prevent SocketTimeoutException on slow emulators/servers
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()


            val request = Request.Builder().url(serverUrl).addHeader("bypass-tunnel-reminder", "true").build()
            Log.d("WebRtcClient", "Connecting to $serverUrl")
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("WebRtcClient", "WebSocket Connected")
                    createPeerConnection()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebRtcClient", "WebSocket Closing: $code / $reason")
                    listener.onStatusChanged(ConnectionStatus.DISCONNECTED)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebRtcClient", "WebSocket connection failed", t)
                    disconnect()
                    listener.onStatusChanged(ConnectionStatus.ERROR)
                }
            })
        }
    }

    fun disconnect() {
        executor.execute {
            try {
                peerConnection?.close()
                peerConnection = null
                webSocket?.close(1000, "Client disconnected")
                webSocket = null
                
                localAudioTrack?.dispose()
                localAudioTrack = null
                audioSource?.dispose()
                audioSource = null
            } catch (e: Exception) {
                Log.e("WebRtcClient", "Error during disconnect", e)
            } finally {
                listener.onStatusChanged(ConnectionStatus.DISCONNECTED)
                listener.onDisconnected()
            }
        }
    }

    // Call this when the ViewModel/Activity is destroyed
    fun release() {
        executor.execute {
            disconnect()
            factory.dispose()
            eglBase.release()
        }
    }

    fun toggleAudio(enabled: Boolean) {
        executor.execute {
            localAudioTrack?.setEnabled(enabled)
            Log.d("WebRtcClient", "Local Audio Enabled: $enabled")
        }
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Force the use of Relay if direct connection fails
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d("WebRtcClient", "IceConnectionState: $newState")
                if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                    listener.onStatusChanged(ConnectionStatus.CONNECTED)
                } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED || 
                           newState == PeerConnection.IceConnectionState.FAILED) {
                    listener.onStatusChanged(ConnectionStatus.DISCONNECTED)
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { sendIceCandidate(it) }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let {
                    when (it.kind()) {
                        "video" -> listener.onVideoTrack(it as VideoTrack)
                        "audio" -> {
                            val audioTrack = it as AudioTrack
                            audioTrack.setEnabled(true)
                            listener.onAudioTrack(audioTrack)
                        }
                    }
                }
            }
        })

        // Add audio track for push-to-talk
        // Use default audio constraints
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(false) // Start muted
        peerConnection?.addTrack(localAudioTrack)

        // Request video (RecvOnly) to ensure we get the video stream from server
        // Also ensure we offer to send audio if needed
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, 
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        
        // Ensure Audio Transceiver is set to SEND_RECV or SEND_ONLY if we want to talk
        // By adding track, it should default to SEND_RECV or SEND_ONLY based on offer.
        // Explicitly adding transceiver for audio to ensure compatibility
        // peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
        //    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))

        // Create offer
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            sendSdp(sdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.e("WebRtcClient", "Failed to create offer: $p0")
                listener.onStatusChanged(ConnectionStatus.ERROR)
            }
            override fun onSetFailure(p0: String?) {
                Log.e("WebRtcClient", "Failed to set failure: $p0")
                listener.onStatusChanged(ConnectionStatus.ERROR)
            }
        }, MediaConstraints())
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            when {
                json.has("sdp") -> {
                    val sdp = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(json.getString("type")),
                        json.getString("sdp")
                    )
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, sdp)
                }
                json.has("candidate") -> {
                    // Handle candidate from Python server (may be dict or object)
                    // The server might send candidate inside a nested dict or as fields
                    val candidateStr = if (json.has("candidate") && json.optJSONObject("candidate") != null) {
                         json.getJSONObject("candidate").optString("candidate", "")
                    } else {
                         json.optString("candidate", "")
                    }
                    
                    val sdpMid = if (json.has("sdpMid")) json.optString("sdpMid") else {
                         if (json.has("candidate") && json.optJSONObject("candidate") != null)
                             json.getJSONObject("candidate").optString("sdpMid") else ""
                    }
                    
                    val sdpMLineIndex = if (json.has("sdpMLineIndex")) json.optInt("sdpMLineIndex") else {
                         if (json.has("candidate") && json.optJSONObject("candidate") != null)
                             json.getJSONObject("candidate").optInt("sdpMLineIndex") else 0
                    }

                    if (candidateStr.isNotEmpty()) {
                        val candidate = IceCandidate(
                            sdpMid,
                            sdpMLineIndex,
                            candidateStr
                        )
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error handling message", e)
        }
    }

    private fun sendSdp(sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        Log.d("WebRtcClient", "Sending SDP: $json")
        webSocket?.send(json.toString())
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("type", "candidate")
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        Log.d("WebRtcClient", "Sending ICE Candidate: $json")
        webSocket?.send(json.toString())
    }

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    interface Listener {
        fun onStatusChanged(status: ConnectionStatus)
        fun onVideoTrack(videoTrack: VideoTrack)
        fun onAudioTrack(audioTrack: AudioTrack)
        fun onDisconnected()
    }
}
