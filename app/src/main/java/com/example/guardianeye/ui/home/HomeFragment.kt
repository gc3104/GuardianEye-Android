package com.example.guardianeye.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        preferenceManager = PreferenceManager(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GuardianEyeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen() {
        val context = LocalContext.current
        var streamUrl by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // Load stream URL
        LaunchedEffect(Unit) {
            streamUrl = preferenceManager.getStreamUrl()
            if (streamUrl.isNullOrEmpty()) {
                Toast.makeText(context, "Please set Stream URL in Settings", Toast.LENGTH_LONG).show()
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

            // Video Player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(bottom = 32.dp)
            ) {
                if (!streamUrl.isNullOrEmpty()) {
                    VideoPlayer(url = streamUrl!!)
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Stream URL configured")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Panic Button
            Button(
                onClick = {
                    Toast.makeText(context, "PANIC: Alerting Authorities!", Toast.LENGTH_LONG).show()
                    scope.launch {
                        val contact = preferenceManager.getEmergencyContact()
                        if (!contact.isNullOrEmpty()) {
                            sendSms(context, contact, "PANIC ALERT! I need immediate help. Sent from GuardianEye.")
                            makeCall(context, contact)
                        } else {
                            Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("EMERGENCY ALERT")
            }
        }
    }

    @Composable
    fun VideoPlayer(url: String) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        // Create and manage ExoPlayer instance
        val player = remember { ExoPlayer.Builder(context).build() }

        DisposableEffect(url) {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            
            onDispose {
                // Player is released in the main DisposableEffect
            }
        }
        
        DisposableEffect(Unit) {
            onDispose {
                player.release()
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> player.play()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

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