package com.example.guardianeye.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.example.guardianeye.webrtc.WebRtcClient
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFootage: () -> Unit
) {
    val viewState by viewModel.viewState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-connect when screen becomes active
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.startStream()
            } else if (event == Lifecycle.Event.ON_STOP) {
                // Optional: Disconnect on stop to save battery, or keep running
                // viewModel.disconnect() 
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Live Surveillance",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Video Player Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (viewState.videoTrack != null) {
                        WebRtcVideoView(viewState.videoTrack!!, viewState.eglBaseContext)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (viewState.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTING) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = when (viewState.connectionStatus) {
                                        WebRtcClient.ConnectionStatus.CONNECTING -> "Connecting..."
                                        WebRtcClient.ConnectionStatus.DISCONNECTED -> "Disconnected"
                                        WebRtcClient.ConnectionStatus.ERROR -> "Connection Failed"
                                        else -> "Waiting for video..."
                                    },
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                
                // Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connect/Reconnect Button
                    IconButton(onClick = { 
                        if (viewState.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTED) {
                             viewModel.disconnect()
                        } else {
                             viewModel.startStream()
                        }
                    }) {
                        Icon(
                            imageVector = if (viewState.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTED) 
                                Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = if (viewState.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTED) 
                                "Disconnect" else "Connect",
                            tint = if (viewState.connectionStatus == WebRtcClient.ConnectionStatus.CONNECTED) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    // Push to Talk Button
                    PushToTalkButton(viewModel)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Footage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Footage",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onNavigateToFootage) {
                Text("View All")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (viewState.recentFootage.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                    contentAlignment = Alignment.Center
            ) {
                Text("No recent footage found")
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(viewState.recentFootage) { uri ->
                    FootageThumbnail(uri) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(uri, "video/*")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Handle error
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebRtcVideoView(videoTrack: VideoTrack, eglBaseContext: EglBase.Context?) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                videoTrack.addSink(this)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { view ->
            videoTrack.removeSink(view)
            view.release()
        }
    )
}

@Composable
fun PushToTalkButton(viewModel: HomeViewModel) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        viewModel.toggleAudio(isPressed)
    }

    Button(
        onClick = { /* interaction handled by interactionSource */ },
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isPressed) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(if (isPressed) "Listening..." else "Hold to Talk")
    }
}

@Composable
fun FootageThumbnail(uri: Uri, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(160.dp, 100.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Play icon overlay
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            }
        }
    }
}
