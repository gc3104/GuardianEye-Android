package com.example.guardianeye.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.webrtc.WebRtcClient
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

sealed class HomeIntent {
    object StartStream : HomeIntent()
    object Disconnect : HomeIntent()
    data class ToggleAudio(val enabled: Boolean) : HomeIntent()
    data class ToggleRemoteAudio(val enabled: Boolean) : HomeIntent()
    data class SetRemoteAudioVolume(val volume: Float) : HomeIntent()
    object NavigateToFootage : HomeIntent()
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFootage: () -> Unit
) {
    val viewState by viewModel.viewState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.startStream()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeContent(
        state = viewState,
        onIntent = { intent ->
            when (intent) {
                HomeIntent.StartStream -> viewModel.startStream()
                HomeIntent.Disconnect -> viewModel.disconnect()
                is HomeIntent.ToggleAudio -> viewModel.toggleAudio(intent.enabled)
                is HomeIntent.ToggleRemoteAudio -> viewModel.toggleRemoteAudio(intent.enabled)
                is HomeIntent.SetRemoteAudioVolume -> viewModel.setRemoteAudioVolume(intent.volume)
                HomeIntent.NavigateToFootage -> onNavigateToFootage()
            }
        }
    )
}

@Composable
private fun HomeContent(
    state: HomeViewState,
    onIntent: (HomeIntent) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Live Surveillance",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    if (state.videoTrack != null) {
                        WebRtcVideoView(state.videoTrack, state.eglBaseContext)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (state.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTING) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }
                
                StreamControls(state, onIntent)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        FootageSection(state.recentFootage, onIntent)
    }
}

@Composable
private fun StreamControls(state: HomeViewState, onIntent: (HomeIntent) -> Unit) {
    var showVolumeSlider by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isConnected = state.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTED
            
            FloatingActionButton(
                onClick = { if (isConnected) onIntent(HomeIntent.Disconnect) else onIntent(HomeIntent.StartStream) },
                containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(if (isConnected) Icons.Default.Videocam else Icons.Default.VideocamOff, null)
            }

            IconButton(
                onClick = { onIntent(HomeIntent.ToggleRemoteAudio(!state.isRemoteAudioEnabled)) },
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(if (state.isRemoteAudioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, null)
            }

            Button(
                onClick = { onIntent(HomeIntent.ToggleAudio(!state.isAudioTransmitting)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isAudioTransmitting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(if (state.isAudioTransmitting) Icons.Default.Mic else Icons.Default.MicOff, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isAudioTransmitting) "LIVE" else "PUSH TO TALK")
            }
        }
    }
}

@Composable
private fun FootageSection(footage: List<Uri>, onIntent: (HomeIntent) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Recent Footage", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { onIntent(HomeIntent.NavigateToFootage) }) {
            Text("See all")
        }
    }
    
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(footage) { uri ->
            FootageThumbnail(uri = uri) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    }
}

@Composable
fun WebRtcVideoView(videoTrack: VideoTrack, eglBaseContext: EglBase.Context?) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                videoTrack.addSink(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun FootageThumbnail(uri: Uri, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(200.dp, 120.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Box {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircleOutline, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GuardianEyeTheme {
        HomeContent(
            state = HomeViewState(connectionStatus = WebRtcClient.ConnectionStatus.DISCONNECTED),
            onIntent = {}
        )
    }
}
