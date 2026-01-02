package com.example.guardianeye.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.guardianeye.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    alertId: String?,
    alertType: String?,
    alertDesc: String?,
    mediaUrl: String?,
    mediaType: String?,
    viewModel: ChatViewModel = viewModel()
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }
    var showMediaPickerDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    val visualMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            val mimeType = context.contentResolver.getType(uri)
            selectedMediaType = if (mimeType?.startsWith("video") == true) "VIDEO" else "IMAGE"
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            selectedMediaType = "AUDIO"
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeChat(alertId, alertType, alertDesc, mediaUrl, mediaType)
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    if (showMediaPickerDialog) {
        AlertDialog(
            onDismissRequest = { showMediaPickerDialog = false },
            title = { Text("Select Media Type") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMediaPickerDialog = false
                                visualMediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Image or Video")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMediaPickerDialog = false
                                audioPickerLauncher.launch("audio/*")
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Audio")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMediaPickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                items(chatMessages) { message ->
                    ChatBubble(message)
                }
            }

            // Preview of selected media to send
            if (selectedMediaUri != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(100.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (selectedMediaType) {
                            "IMAGE" -> {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedMediaUri),
                                    contentDescription = "Selected Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .padding(end = 8.dp)
                                )
                            }
                            "VIDEO" -> {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image, // Use a generic icon or video placeholder
                                        contentDescription = "Video",
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text("Video")
                                }
                            }
                            "AUDIO" -> {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .padding(end = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Audiotrack,
                                        contentDescription = "Audio",
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text("Audio")
                                }
                            }
                        }
                        IconButton(onClick = {
                            selectedMediaUri = null
                            selectedMediaType = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Media")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    showMediaPickerDialog = true
                }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach Media")
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() || selectedMediaUri != null) {
                            viewModel.sendMessage(
                                inputText,
                                alertId,
                                alertType,
                                selectedMediaUri?.toString(),
                                selectedMediaType
                            )
                            inputText = ""
                            selectedMediaUri = null
                            selectedMediaType = null
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank() || selectedMediaUri != null) {
                            viewModel.sendMessage(
                                inputText,
                                alertId,
                                alertType,
                                selectedMediaUri?.toString(),
                                selectedMediaType
                            )
                            inputText = ""
                            selectedMediaUri = null
                            selectedMediaType = null
                        }
                    },
                    enabled = inputText.isNotBlank() || selectedMediaUri != null
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!message.mediaUrl.isNullOrEmpty()) {
                    val mediaModifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(if (message.mediaType == "AUDIO") 80.dp else 200.dp)
                        .padding(bottom = 8.dp)

                    Box(modifier = mediaModifier) {
                        when (message.mediaType) {
                            "IMAGE" -> {
                                Image(
                                    painter = rememberAsyncImagePainter(message.mediaUrl),
                                    contentDescription = "Sent Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            "VIDEO", "AUDIO" -> {
                                VideoPlayer(url = message.mediaUrl, isAudio = message.mediaType == "AUDIO")
                            }
                            else -> {
                                // Fallback
                            }
                        }
                    }
                }
                
                if (message.message.isNotBlank()) {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(url: String, isAudio: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(url) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false

        onDispose { }
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
                Lifecycle.Event.ON_RESUME -> {}
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
                this.useController = true
                if (isAudio) {
                    controllerShowTimeoutMs = 0 // Keep controller visible for audio
                    controllerHideOnTouch = false
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
