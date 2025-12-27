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
import com.example.guardianeye.ai.AiActionManager
import com.example.guardianeye.ai.AiModelManager
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.model.ChatMessage
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class ChatFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var aiActionManager: AiActionManager
    private lateinit var aiModelManager: AiModelManager

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
        val context = requireContext()
        preferenceManager = PreferenceManager(context)
        aiActionManager = AiActionManager(context)
        aiModelManager = AiModelManager(context)
        
        return ComposeView(context).apply {
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
                            preferenceManager = preferenceManager,
                            aiModelManager = aiModelManager,
                            aiActionManager = aiActionManager
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
    preferenceManager: PreferenceManager,
    aiModelManager: AiModelManager,
    aiActionManager: AiActionManager
) {
    val context = LocalContext.current
    var chatMessages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    var modelReady by remember { mutableStateOf(false) }

    val genericHello = stringResource(R.string.generic_hello_message)
    val panicButtonText = stringResource(R.string.panic_button_text)
    
    // Check and Initialize LLM
    LaunchedEffect(Unit) {
        if (aiModelManager.isModelDownloaded()) {
            modelReady = aiModelManager.initializeModel()
        }
    }

    fun handleAction(action: String) {
        scope.launch {
             val resultMessage = aiActionManager.executeAction(action, alertType, alertId)
             chatMessages = chatMessages + ChatMessage(message = resultMessage, isUser = false)
        }
    }

    fun generateLlmResponse(prompt: String) {
        val inference = aiModelManager.getInferenceEngine()
        if (inference != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // Combine system instructions with user prompt
                    val fullPrompt = "${aiModelManager.generateSystemInstructions()}\n\nUser: $prompt\nAI:"
                    val response = inference.generateResponse(fullPrompt)
                    
                    // Parse response for JSON intent
                    var displayMessage = response
                    var actionToExecute: String? = null
                    
                    val jsonStart = response.lastIndexOf("```json")
                    if (jsonStart != -1) {
                         val jsonEnd = response.lastIndexOf("```")
                         if (jsonEnd > jsonStart) {
                             val jsonString = response.substring(jsonStart + 7, jsonEnd).trim()
                             try {
                                 val jsonObject = JSONObject(jsonString)
                                 actionToExecute = jsonObject.optString("intent")
                                 // Remove the JSON block from the display message
                                 displayMessage = response.take(jsonStart).trim()
                             } catch (e: Exception) {
                                 Log.e("ChatScreen", "Failed to parse JSON intent", e)
                             }
                         }
                    }

                    withContext(Dispatchers.Main) {
                        if (displayMessage.isNotBlank()) {
                            chatMessages = chatMessages + ChatMessage(message = displayMessage, isUser = false)
                        }
                        
                        if (actionToExecute != null) {
                            handleAction(actionToExecute)
                        }
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
             val action = aiActionManager.parseActionFromText(prompt)
             
            if (action != "UNKNOWN") {
                handleAction(action)
            } else {
                val fallbackMsg = if (!modelReady) {
                    "I'm running in basic mode. You can enable advanced AI in Settings."
                } else {
                    "I'm not connected to my brain (LLM) right now."
                }
                chatMessages = chatMessages + ChatMessage(message = fallbackMsg, isUser = false)
            }
        }
    }

    fun handleUserMessage(text: String) {
        chatMessages = chatMessages + ChatMessage(message = text, isUser = true)
        
        if (modelReady) {
             generateLlmResponse(text)
        } else {
            // Fallback parsing
            val action = aiActionManager.parseActionFromText(text)
            if (action != "UNKNOWN") {
                handleAction(action)
            } else {
                 // No action found and no model
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
                title = { }, // Empty title
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
    } catch (_: IllegalArgumentException) {
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