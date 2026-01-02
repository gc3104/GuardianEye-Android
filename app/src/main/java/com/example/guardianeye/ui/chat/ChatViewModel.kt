package com.example.guardianeye.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.R
import com.example.guardianeye.ai.AiActionManager
import com.example.guardianeye.ai.AiModelManager
import com.example.guardianeye.data.local.AppDatabase
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.ChatRepository
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val aiModelManager = AiModelManager(application)
    private val aiActionManager = AiActionManager(application)
    private val chatRepository: ChatRepository
    private val alertRepository: AlertRepository

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _modelReady = MutableStateFlow(false)

    init {
        val database = AppDatabase.getDatabase(application)
        chatRepository = ChatRepository(database.chatDao())
        alertRepository = AlertRepository(database.alertDao())

        viewModelScope.launch {
            if (aiModelManager.isModelDownloaded()) {
                _modelReady.value = aiModelManager.initializeModel()
            }
        }
        
        // Observe messages from DB
        viewModelScope.launch {
            chatRepository.allMessages.collectLatest { messages ->
                _chatMessages.value = messages
            }
        }
    }

    fun initializeChat(
        alertId: String?, 
        alertType: String?, 
        alertDesc: String?,
        mediaUrl: String? = null,
        mediaType: String? = null
    ) {
        if (_chatMessages.value.isNotEmpty()) return

        viewModelScope.launch {
            if (alertId != null) {
                // Save the Alert to DB
                try {
                    val typeEnum = try {
                        AlertType.valueOf(alertType?.uppercase(Locale.getDefault()) ?: "UNKNOWN")
                    } catch (_: Exception) { AlertType.UNKNOWN }
                    
                    val alert = Alert(
                        id = alertId,
                        type = typeEnum,
                        description = alertDesc ?: "",
                        mediaUrl = mediaUrl,
                        mediaType = mediaType
                    )
                    alertRepository.insertAlert(alert)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving alert", e)
                }

                delay(500)
                addMessage(
                    text = "New Alert: ${alertType ?: "event"}\n${alertDesc ?: ""}", 
                    isUser = false,
                    mediaUrl = mediaUrl,
                    mediaType = mediaType
                )

                delay(1000)
                val recommendation = generateRecommendation(alertType)
                addMessage(recommendation, false)

                delay(1000)
                addMessage("How should I respond? You can say things like 'call for help' or 'ignore it'.", false)
            } else {
                addMessage(getApplication<Application>().getString(R.string.generic_hello_message), false)
            }
        }
    }

    fun sendMessage(text: String, alertId: String?, alertType: String?, mediaUri: String? = null, mediaType: String? = null) {
        addMessage(text, true, mediaUri, mediaType)

        val promptText = if (text.isBlank() && mediaUri != null) "I sent a ${mediaType?.lowercase() ?: "file"}." else text

        if (promptText.isNotBlank()) {
            if (_modelReady.value) {
                generateLlmResponse(promptText, alertId, alertType)
            } else {
                // Fallback parsing
                val action = aiActionManager.parseActionFromText(promptText)
                if (action != "UNKNOWN") {
                    executeAction(action, alertType, alertId)
                } else {
                    val fallbackMsg = "I'm running in basic mode. You can enable advanced AI in Settings."
                    addMessage(fallbackMsg, false)
                }
            }
        }
    }

    private fun generateLlmResponse(prompt: String, alertId: String?, alertType: String?) {
        val inference = aiModelManager.getInferenceEngine() ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullPrompt = "${aiModelManager.generateSystemInstructions()}\n\nUser: $prompt\nAI:"
                val response = inference.generateResponse(fullPrompt)
                Log.d("ChatViewModel", "LLM Response: $response")
                
                var displayMessage = response
                var actionToExecute: String? = null

                // Simplified parsing for Single JSON Object format
                val braceStart = response.indexOf("{")
                // Find matching closing brace using counter
                val braceEnd = findMatchingClosingBrace(response, braceStart)
                
                if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                    try {
                        val jsonString = response.substring(braceStart, braceEnd + 1)
                        val jsonObject = JSONObject(jsonString)
                        
                        if (jsonObject.has("text") && jsonObject.has("intent")) {
                            displayMessage = jsonObject.getString("text")
                            actionToExecute = jsonObject.getString("intent")
                        } else {
                             // Fallback if keys are missing but it is JSON
                             actionToExecute = jsonObject.optString("intent")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to parse JSON response", e)
                    }
                } else {
                    // Fallback to legacy parsing or raw text if no JSON found
                     Log.w("ChatViewModel", "No valid JSON found in response")
                }

                if (displayMessage.isNotBlank()) {
                    addMessage(displayMessage, false)
                }

                if (actionToExecute != null && actionToExecute != "UNKNOWN" && actionToExecute != "NONE") {
                    executeAction(actionToExecute, alertType, alertId)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "LLM Inference Error", e)
                addMessage("I'm having trouble thinking right now.", false)
            }
        }
    }

    private fun findMatchingClosingBrace(text: String, start: Int): Int {
        if (start == -1) return -1
        var depth = 0
        for (i in start until text.length) {
            if (text[i] == '{') depth++
            else if (text[i] == '}') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }

    private fun executeAction(action: String, alertType: String?, alertId: String?) {
        viewModelScope.launch {
            val resultMessage = aiActionManager.executeAction(action, alertType, alertId)
            addMessage(resultMessage, false)
        }
    }

    private fun addMessage(text: String, isUser: Boolean, mediaUrl: String? = null, mediaType: String? = null) {
        val newMessage = ChatMessage(
            message = text, 
            isUser = isUser,
            mediaUrl = mediaUrl,
            mediaType = mediaType
        )
        viewModelScope.launch {
            chatRepository.insertMessage(newMessage)
        }
    }

    private fun generateRecommendation(type: String?): String {
        return try {
            val enumType = AlertType.valueOf(type?.uppercase(Locale.getDefault()) ?: "UNKNOWN")
            when (enumType) {
                AlertType.INTRUDER -> "I've detected an unauthorized person. What should I do?"
                AlertType.WEAPON -> "A weapon has been detected. This is a critical situation. I strongly recommend you call emergency services immediately."
                AlertType.SCREAM -> "A scream was detected. This could be a sign of distress. What should I do?"
                AlertType.UNKNOWN -> "Unknown event detected. Please review and tell me what to do."
                AlertType.UNKNOWN_FACE -> "An unknown face has been detected. Please verify."
                AlertType.FACE_RECOGNITION -> "An authorized person has been recognized."
                AlertType.MASK_DETECTION -> "A person with a mask was detected. This might be a security risk."
            }
        } catch (_: IllegalArgumentException) {
            "Please review the alert details and choose an action."
        }
    }
}
