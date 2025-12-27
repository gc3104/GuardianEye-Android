package com.example.guardianeye.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter

class AlertDetailFragment : Fragment() {

    // Arguments
    private var alertType: String? = null
    private var alertDesc: String? = null
    private var mediaUrl: String? = null
    private var mediaType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            alertType = it.getString("alertType")
            alertDesc = it.getString("alertDesc")
            mediaUrl = it.getString("mediaUrl")
            mediaType = it.getString("mediaType")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ResolvedAlertScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun ResolvedAlertScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = alertType ?: "Alert Detail",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alertDesc ?: "No description provided",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!mediaUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (mediaType) {
                        "IMAGE" -> {
                            Image(
                                painter = rememberAsyncImagePainter(mediaUrl),
                                contentDescription = "Alert Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "VIDEO" -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val player = remember { ExoPlayer.Builder(context).build() }
                            val mediaItem = MediaItem.fromUri(mediaUrl!!)
                            player.setMediaItem(mediaItem)
                            player.prepare()

                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        this.player = player
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}