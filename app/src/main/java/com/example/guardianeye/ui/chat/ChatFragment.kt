package com.example.guardianeye.ui.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.guardianeye.R
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.model.ChatMessage
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class ChatFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager

    private var alertId: String? = null
    private var alertType: String? = null
    private var alertDesc: String? = null
    private var mediaUrl: String? = null
    private var mediaType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            alertId = it.getString("alertId")
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
        preferenceManager = PreferenceManager(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                GuardianEyeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ChatScreen(
                            alertId = alertId,
                            alertType = alertType,
                            alertDesc = alertDesc,
                            mediaUrl = mediaUrl,
                            mediaType = mediaType,
                            preferenceManager = preferenceManager
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    alertId: String? = null,
    alertType: String? = null,
    alertDesc: String? = null,
    mediaUrl: String? = null,
    mediaType: String? = null,
    preferenceManager: PreferenceManager
) {
    val context = LocalContext.current
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // LLM State
    var llmInference: LlmInference? by remember { mutableStateOf(null) }
    var isModelDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var modelReady by remember { mutableStateOf(false) }
    
    val modelName = "gemma-3n-E4B-it-int4.task"
    // TODO: Replace with the actual URL where the model is hosted
    val modelUrl = "https://example.com/path/to/$modelName" 

    val genericHello = stringResource(R.string.generic_hello_message)
    val panicButtonText = stringResource(R.string.panic_button_text)
    val titleChat = stringResource(R.string.title_chat)

    // Check and Initialize LLM
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, modelName)
            if (modelFile.exists()) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                    modelReady = true
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Error initializing LLM", e)
                }
            }
        }
    }

    fun downloadModel() {
        scope.launch(Dispatchers.IO) {
            isModelDownloading = true
            downloadProgress = 0f
            val modelFile = File(context.filesDir, modelName)
            val client = OkHttpClient()
            val request = Request.Builder().url(modelUrl).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    
                    val body = response.body ?: throw IOException("Empty body")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(modelFile)
                    
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            withContext(Dispatchers.Main) {
                                downloadProgress = totalBytesRead.toFloat() / contentLength.toFloat()
                            }
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }

                // Initialize after download
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                modelReady = true
                withContext(Dispatchers.Main) {
                    chatMessages = chatMessages + ChatMessage(message = "Model downloaded and ready!", isUser = false)
                }

            } catch (e: Exception) {
                Log.e("ChatScreen", "Download failed", e)
                withContext(Dispatchers.Main) {
                    chatMessages = chatMessages + ChatMessage(message = "Failed to download model: ${e.message}", isUser = false)
                    // Clean up partial file
                    if (modelFile.exists()) modelFile.delete()
                }
            } finally {
                isModelDownloading = false
            }
        }
    }

    fun markResolved() {
        alertId?.let {
            FirebaseFirestore.getInstance().collection("alerts").document(it)
                .update("isActionTaken", true)
        }
    }

    fun handleAction(action: String) {
        scope.launch {
            when (action) {
                "Call" -> {
                    val contact = preferenceManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) makeCall(context, contact) else Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                }
                "SMS" -> {
                    val contact = preferenceManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) sendSms(context, contact, "Alert: ${alertType ?: "Security Event"} detected via GuardianEye.") else Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                }
                "Ignore" -> {
                    markResolved()
                }
                else -> {
                }
            }
        }
    }

    fun generateLlmResponse(prompt: String) {
        val inference = llmInference
        if (inference != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val response = inference.generateResponse(prompt)
                    withContext(Dispatchers.Main) {
                        chatMessages = chatMessages + ChatMessage(message = response, isUser = false)
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "LLM Inference Error", e)
                    withContext(Dispatchers.Main) {
                        chatMessages = chatMessages + ChatMessage(message = "I'm having trouble thinking right now.", isUser = false)
                    }
                }
            }
        } else {
             // Fallback if LLM not loaded
             val action = when {
                prompt.contains("call", ignoreCase = true) -> "Call"
                prompt.contains("sms", ignoreCase = true) || prompt.contains("text", ignoreCase = true) -> "SMS"
                prompt.contains("ignore", ignoreCase = true) || prompt.contains("dismiss", ignoreCase = true) -> "Ignore"
                else -> "UNKNOWN"
            }
            if (action != "UNKNOWN") {
                handleAction(action)
                chatMessages = chatMessages + ChatMessage(message = "Action $action taken.", isUser = false)
            } else {
                 if (!isModelDownloading && !modelReady) {
                     chatMessages = chatMessages + ChatMessage(message = "I need to download my brain first.", isUser = false)
                 } else {
                     chatMessages = chatMessages + ChatMessage(message = "I'm not connected to my brain (LLM) right now.", isUser = false)
                 }
            }
        }
    }

    fun handleUserMessage(text: String) {
        chatMessages = chatMessages + ChatMessage(message = text, isUser = true)
        
        // Use LLM if available, otherwise fallback
        if (llmInference != null) {
             generateLlmResponse(text)
        } else {
            val action = when {
                text.contains("call", ignoreCase = true) -> "Call"
                text.contains("sms", ignoreCase = true) || text.contains("text", ignoreCase = true) -> "SMS"
                text.contains("ignore", ignoreCase = true) || text.contains("dismiss", ignoreCase = true) -> "Ignore"
                else -> "UNKNOWN"
            }
            if (action != "UNKNOWN") {
                handleAction(action)
            } else if (!isModelDownloading && !modelReady) {
                // If model is missing, maybe prompt to download?
            }
        }
    }

    LaunchedEffect(alertId) {
        if (alertId != null) {
            delay(500)
            chatMessages = chatMessages + ChatMessage(message = "New Alert: ${alertType ?: "event"}\n${alertDesc ?: ""}", isUser = false)

            delay(1000)
            val recommendation = generateRecommendation(alertType)
            chatMessages = chatMessages + ChatMessage(message = recommendation, isUser = false)

            delay(1000)
            chatMessages = chatMessages + ChatMessage(message = "How should I respond? You can say things like 'call for help' or 'ignore it'.", isUser = false)
        } else if (chatMessages.isEmpty()) {
            chatMessages = chatMessages + ChatMessage(message = genericHello, isUser = false)
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleChat) },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                val contact = preferenceManager.getEmergencyContact()
                                if (!contact.isNullOrEmpty()) {
                                    makeCall(context, contact)
                                } else {
                                    Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(panicButtonText)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
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
                            VideoPlayer(url = mediaUrl)
                        }
                    }
                }
            }
            
            // Download Progress / Button
            if (!modelReady && !isModelDownloading) {
                 Button(
                     onClick = { downloadModel() },
                     modifier = Modifier.padding(16.dp).fillMaxWidth()
                 ) {
                     Text("Download AI Model")
                 }
            } else if (isModelDownloading) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Downloading AI Model...", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(chatMessages) { message ->
                    ChatBubble(message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            handleUserMessage(inputText)
                            inputText = ""
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            handleUserMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }
}

private fun generateRecommendation(type: String?): String {
    return try {
        val enumType = AlertType.valueOf(type?.uppercase(Locale.getDefault()) ?: "UNKNOWN")
        when (enumType) {
            AlertType.INTRUDER -> "I've detected an unauthorized person. What should I do?"
            AlertType.WEAPON -> "A weapon has been detected. This is a critical situation. I strongly recommend you call emergency services immediately."
            AlertType.SCREAM -> "A scream was detected. This could be a sign of distress. What should I do?"
            AlertType.FACE_RECOGNITION -> "An authorized person has been recognized."
            AlertType.MASK_DETECTION -> "A person with a mask was detected. This might be a security risk."
            AlertType.UNKNOWN -> "Unknown event detected. Please review and tell me what to do."
            AlertType.UNKNOWN_FACE -> "An unknown face has been detected. Please verify."
        }
    } catch (e: IllegalArgumentException) {
        "Please review the alert details and choose an action."
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
            Text(
                text = message.message,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun VideoPlayer(url: String) {
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
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}