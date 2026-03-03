package com.example.guardianeye.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.ai.AssistantActionExecutor
import com.example.guardianeye.ai.AssistantActionPlan
import com.example.guardianeye.ai.AssistantModelManager
import com.example.guardianeye.ai.AssistantOrchestrator
import com.example.guardianeye.data.repository.ChatRepository
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.ChatMessage
import com.example.guardianeye.model.ContactResult
import com.example.guardianeye.model.PendingAction
import com.example.guardianeye.model.ResolutionSource
import com.example.guardianeye.utils.PreferenceManager
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionResponse
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.fc.ChatSession
import com.google.protobuf.Struct
import com.google.protobuf.Value
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val firebaseManager: FirebaseManager,
    private val assistantModelManager: AssistantModelManager,
    private val assistantActionExecutor: AssistantActionExecutor,
    private val assistantOrchestrator: AssistantOrchestrator,
    private val prefs: PreferenceManager
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var currentAlert: Alert? = null
    private val _modelReady = MutableStateFlow(false)
    private val _isModelInitializing = MutableStateFlow(false)
    
    private val actionJobs = mutableMapOf<String, Job>()
    private var autoActionJob: Job? = null
    
    private var chatSession: ChatSession? = null

    private val baseActions = listOf("Call Emergency", "Send SMS", "Mark as Resolved")

    companion object {
        const val GENERAL_CHAT_ID = "general_chat"
        private const val TIMER_EXTENSION_MS = 15000L
        private const val TAG = "ChatViewModel"
    }

    init {
        viewModelScope.launch {
            if (assistantModelManager.isModelDownloaded()) {
                _isModelInitializing.value = true
                _modelReady.value = assistantModelManager.initializeModel()
                _isModelInitializing.value = false
                
                if (_modelReady.value) {
                    try {
                        chatSession = assistantModelManager.getGenerativeModel()?.startChat()
                        
                        val useConstraints = prefs.getAlertPreference(PreferenceManager.USE_AI_CONSTRAINTS, defaultValue = false)
                        if (useConstraints) {
                            Log.d(TAG, "Enabling AI constraints as per settings toggle")
                            chatSession?.enableConstraint(assistantModelManager.getConstraintOptions())
                            assistantModelManager.isConstraintsWorking = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to initialize chat session or constraints: ${e.message}")
                        assistantModelManager.isConstraintsWorking = false
                    }
                }
            }
            updateSuggestions("")
        }
    }

    fun updateSuggestions(query: String) {
        viewModelScope.launch {
            val list = if (query.isBlank()) {
                baseActions
            } else {
                baseActions.filter { it.contains(query, ignoreCase = true) } }
            _suggestions.value = list.take(3)
        }
    }

    fun initializeChat(alert: Alert?) {
        if (alert == null) {
            currentAlert = null
            stopAutoActionTimer()
            loadMessages()
            updateSuggestions("")
            return
        }
        
        if (currentAlert == null || currentAlert?.id != alert.id) {
            currentAlert = alert
            loadMessages()
            if (alert.type.name == "UNKNOWN" && alert.description.isEmpty()) {
                fetchAlertDetails(alert.id)
            } else {
                checkForInitialGreeting(alert)
                startOrExtendAutoActionTimer(alert)
            }
        } else {
            currentAlert?.let { startOrExtendAutoActionTimer(it) }
            loadMessages()
        }
        updateSuggestions("")
    }
    
    private fun fetchAlertDetails(alertId: String) {
        viewModelScope.launch {
            try {
                val fullAlert = firebaseManager.fetchAlert(alertId)
                if (fullAlert != null) {
                    currentAlert = fullAlert
                    checkForInitialGreeting(fullAlert)
                    startOrExtendAutoActionTimer(fullAlert)
                    updateSuggestions("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch alert details", e)
            }
        }
    }

    private fun startOrExtendAutoActionTimer(alert: Alert) {
        if (autoActionJob?.isActive == true) {
            restartTimer(alert, TIMER_EXTENSION_MS)
            return
        }

        val delay = when (alert.priority) {
            AlertPriority.CRITICAL -> 15000L
            AlertPriority.HIGH -> 30000L
            AlertPriority.MEDIUM -> 60000L
            AlertPriority.LOW -> 120000L
        }
        restartTimer(alert, delay)
    }

    private fun restartTimer(alert: Alert, duration: Long) {
        autoActionJob?.cancel()
        autoActionJob = viewModelScope.launch {
            delay(duration)
            performDefaultAutoAction(alert)
        }
    }

    private fun stopAutoActionTimer() {
        autoActionJob?.cancel()
        autoActionJob = null
    }

    private suspend fun performDefaultAutoAction(alert: Alert) {
        val plan = assistantModelManager.getDefaultPlan(alert)
        val results = assistantOrchestrator.handle(plan, alert.id)
        val combinedMessage = StringBuilder()
        
        results.forEach { result ->
            when (result) {
                is AssistantActionExecutor.ExecutionResult.Success -> {
                    if (result.actionType == "CALL" || result.actionType == "SMS") {
                        combinedMessage.append("✅ ${result.message}\n")
                    }
                }
                else -> {}
            }
        }
        
        if (combinedMessage.isNotEmpty()) {
            addAiMessage("Fail-safe protocol completed:\n${combinedMessage.toString().trim()}", alert.id)
        }
    }

    private fun loadMessages() {
        val alertId = currentAlert?.id ?: GENERAL_CHAT_ID
        viewModelScope.launch {
            repository.getMessagesForAlert(alertId).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }
    
    private fun checkForInitialGreeting(alert: Alert) {
        viewModelScope.launch {
            val messages = repository.getMessagesForAlert(alert.id).first()
            if (messages.isEmpty()) {
                val alertMessage = ChatMessage(
                    alertId = alert.id,
                    message = "New Alert: ${alert.type.name}\n${alert.description}", 
                    isUser = false,
                    mediaUrl = alert.mediaUrl,
                    mediaType = alert.mediaType
                )
                repository.sendMessage(alertMessage)

                delay(500)
                val recommendation = generateRecommendation(alert.type.name)
                addAiMessage(recommendation, alert.id)
            }
        }
    }

    fun sendMessage(text: String, alert: Alert?) {
        val targetAlertId = alert?.id ?: currentAlert?.id ?: GENERAL_CHAT_ID
        val targetAlert = alert ?: currentAlert
        
        stopAutoActionTimer()
        
        val userMessage = ChatMessage(
            alertId = targetAlertId,
            message = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        
        viewModelScope.launch {
            repository.sendMessage(userMessage)
            
            if (text.contains("resolved", ignoreCase = true) || text.contains("mark as resolved", ignoreCase = true)) {
                targetAlert?.let { executeAction("RESOLVE", it, UUID.randomUUID().toString()) }
            } else {
                processAiResponse(text, targetAlert)
            }
        }
    }
    
    private suspend fun processAiResponse(prompt: String, alert: Alert?) {
        if (prompt.isBlank()) return
        val useChatbot = prefs.getAlertPreference(PreferenceManager.USE_CHATBOT)
        val useFallback = prefs.getAlertPreference(PreferenceManager.USE_PROGRAMMATIC_FALLBACK, defaultValue = true)
        val targetId = alert?.id ?: GENERAL_CHAT_ID

        // 1. Check if model is still loading
        if (useChatbot && _isModelInitializing.value) {
            addAiMessage("AI Chatbot is currently initializing (this may take a minute for 1GB models). Please wait...", targetId)
            return
        }

        // 2. Try LLM if enabled and ready
        if (useChatbot && _modelReady.value && chatSession != null) {
            generateLlmResponse(prompt, alert)
            return
        }

        // 3. Fallback logic: If Chatbot is on but failed/not ready
        if (useFallback) {
            handleProgrammaticFallback(prompt, alert)
        } else {
            // 4. Everything disabled or AI failed without fallback
            val msg = if (useChatbot && !_modelReady.value) {
                "AI Chatbot failed to initialize and Programmatic Fallback is disabled. Please check your model settings."
            } else {
                "Automated responses are currently disabled. Please enable either Chatbot or Programmatic Fallback in settings."
            }
            addAiMessage(msg, targetId)
        }
    }

    private fun handleProgrammaticFallback(prompt: String, alert: Alert?) {
        val targetId = alert?.id ?: GENERAL_CHAT_ID
        val query = prompt.uppercase(Locale.getDefault())
        
        viewModelScope.launch {
            delay(500) 
            val plan = when {
                query.contains("CALL") || query.contains("EMERGENCY") -> {
                    AssistantActionPlan(
                        reply = "Triggering emergency call protocol.",
                        actions = listOf(com.example.guardianeye.ai.ActionStep("CALL", mapOf("contact" to "emergency_contact")))
                    )
                }
                query.contains("SMS") || query.contains("MESSAGE") -> {
                    AssistantActionPlan(
                        reply = "Sending SMS notification.",
                        actions = listOf(com.example.guardianeye.ai.ActionStep("SMS", mapOf("contact" to "emergency_contact", "text" to "GuardianEye Alert: Action requested.")))
                    )
                }
                query.contains("RESOLVE") || query.contains("RESOLVED") -> {
                    AssistantActionPlan(
                        reply = "Resolving alert.",
                        actions = listOf(com.example.guardianeye.ai.ActionStep("RESOLVE"))
                    )
                }
                else -> AssistantActionPlan(reply = "I can help you Call, Message or Mark the alert as Resolved. What would you like to do?")
            }

            if (plan.actions.isNotEmpty()) {
                val results = assistantOrchestrator.handle(plan, alert?.id, ResolutionSource.USER)
                val combinedMessage = StringBuilder()
                plan.reply?.let { combinedMessage.append(it).append("\n\n") }
                
                results.forEach { result ->
                    when (result) {
                        is AssistantActionExecutor.ExecutionResult.Success -> {
                            if (result.actionType == "CALL" || result.actionType == "SMS" || result.actionType == "RESOLVE") {
                                combinedMessage.append("✅ ${result.message}\n")
                            }
                        }
                        is AssistantActionExecutor.ExecutionResult.Error -> {
                            combinedMessage.append("❌ ${result.error}\n")
                        }
                    }
                }
                
                val finalMsg = combinedMessage.toString().trim()
                if (finalMsg.isNotEmpty()) addAiMessage(finalMsg, targetId)
            } else {
                addAiMessage(plan.reply ?: "I'm ready to assist.", targetId)
            }
        }
    }

    private fun generateLlmResponse(prompt: String, alert: Alert?) {
        val session = chatSession ?: return
        val targetId = alert?.id ?: GENERAL_CHAT_ID

        viewModelScope.launch(Dispatchers.IO) {
            // Helper to execute prediction either with or without mutex
            val executePrediction = suspend {
                try {
                    var response = session.sendMessage(prompt)
                    var turns = 0
                    val maxTurns = 5

                    while (turns < maxTurns) {
                        // SAFETY CHECK: Ensure we have candidates and content parts
                        if (response.candidatesCount == 0) {
                            Log.w(TAG, "LLM returned no candidates.")
                            break
                        }
                        
                        val candidate = response.getCandidates(0)
                        if (candidate.content.partsCount == 0) {
                            Log.w(TAG, "LLM candidate has no parts.")
                            break
                        }
                        
                        val messagePart = candidate.content.getParts(0)
                        
                        when {
                            messagePart.hasFunctionCall() -> {
                                val fc = messagePart.functionCall
                                val argsMap = mutableMapOf<String, Any>()
                                fc.args.fieldsMap.forEach { (k, v) ->
                                    argsMap[k] = when {
                                        v.hasStringValue() -> v.stringValue
                                        v.hasBoolValue() -> v.boolValue
                                        v.hasNumberValue() -> v.numberValue
                                        else -> v.toString()
                                    }
                                }

                                val execResult = assistantActionExecutor.execute(fc.name, alert?.id, argsMap, ResolutionSource.ASSISTANT)
                                handleActionSideEffects(execResult, targetId)

                                val resultStr = when(execResult) {
                                    is AssistantActionExecutor.ExecutionResult.Success -> {
                                        execResult.data?.toString() ?: execResult.message
                                    }
                                    is AssistantActionExecutor.ExecutionResult.Error -> "Error: ${execResult.error}"
                                }

                                val functionResponse = FunctionResponse.newBuilder()
                                    .setName(fc.name)
                                    .setResponse(
                                        Struct.newBuilder()
                                            .putFields("result", Value.newBuilder().setStringValue(resultStr).build())
                                            .build()
                                    ).build()

                                val responseContent = Content.newBuilder()
                                    .setRole("user")
                                    .addParts(Part.newBuilder().setFunctionResponse(functionResponse).build())
                                    .build()

                                response = session.sendMessage(responseContent)
                                turns++
                            }
                            messagePart.hasText() -> {
                                if (messagePart.text.isNotBlank()) {
                                    addAiMessage(messagePart.text, targetId)
                                }
                                break
                            }
                            else -> break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "FC SDK Error", e)
                    val useFallback = prefs.getAlertPreference(PreferenceManager.USE_PROGRAMMATIC_FALLBACK, defaultValue = true)
                    if (useFallback) {
                        handleProgrammaticFallback(prompt, alert)
                    } else {
                        addAiMessage("An error occurred with the AI model: ${e.localizedMessage}. Fallback is disabled.", targetId)
                    }
                }
            }

            // Only use mutex if AI constraints are NOT active
            if (assistantModelManager.isConstraintsWorking) {
                executePrediction()
            } else {
                assistantModelManager.aiMutex.withLock {
                    executePrediction()
                }
            }
        }
    }

    private fun handleActionSideEffects(result: AssistantActionExecutor.ExecutionResult, targetId: String) {
        when (result) {
            is AssistantActionExecutor.ExecutionResult.Success -> {
                if (result.data is List<*> && result.data.all { it is ContactResult }) {
                    @Suppress("UNCHECKED_CAST")
                    val contacts = result.data as List<ContactResult>
                    addAiMessage("Please select a contact:", targetId, contacts)
                }
                if (result.actionType in listOf("CALL", "SMS", "RESOLVE")) {
                    addAiMessage("✅ ${result.message}", targetId)
                }
            }
            else -> {}
        }
    }

    private fun queueAction(action: String, alert: Alert?, text: String, args: JSONObject? = null) {
        val targetId = alert?.id ?: currentAlert?.id ?: GENERAL_CHAT_ID
        val messageId = UUID.randomUUID().toString()
        val contact = args?.optString("contact")
        val detail = args?.optString("text") ?: args?.optString("message") ?: args?.optString("query") ?: ""
        
        val extraArgsMap = mutableMapOf<String, Any>()
        args?.let { 
            it.keys().forEach { key -> extraArgsMap[key] = it.get(key) } 
        }
        
        val pendingMessage = ChatMessage(
            id = messageId,
            alertId = targetId,
            message = text,
            isUser = false,
            timestamp = System.currentTimeMillis(),
            pendingAction = PendingAction(intent = action, contact = contact, detail = detail, isAutoExecute = true)
        )
        
        viewModelScope.launch {
            repository.sendMessage(pendingMessage)
            val job = launch {
                delay(5000)
                executeAction(action, alert, messageId, contact, detail, extraArgsMap)
            }
            actionJobs[messageId] = job
        }
    }

    fun selectContactForAction(originalMessageId: String, contact: ContactResult, intent: String, detail: String?) {
         viewModelScope.launch {
            val original = _chatMessages.value.find { it.id == originalMessageId }
            original?.let { repository.sendMessage(it.copy(searchResults = null, message = "Selected: ${contact.name}")) }

            val args = JSONObject().apply {
                put("contact", contact.phoneNumber)
                if (detail != null) put("text", detail)
            }
            queueAction(intent, currentAlert, "${if(intent == "SMS") "Messaging" else "Calling"} ${contact.name}...", args)
        }
    }
    
    fun cancelAction(messageId: String) {
        actionJobs[messageId]?.cancel()
        actionJobs.remove(messageId)
        viewModelScope.launch {
            _chatMessages.value.find { it.id == messageId }?.let {
                repository.sendMessage(it.copy(message = "Action cancelled.", pendingAction = null, searchResults = null))
            }
        }
    }
    
    private suspend fun executeAction(
        action: String, 
        alert: Alert?, 
        messageId: String, 
        contact: String? = null, 
        detail: String? = null, 
        extraArgs: Map<String, Any>? = null
    ) {
        val mergedArgs = (extraArgs ?: emptyMap()).toMutableMap().apply {
            contact?.let { put("contact", it) }
            detail?.let { put("text", it) }
        }

        val resultActionResult = assistantActionExecutor.execute(action, alert?.id, mergedArgs, ResolutionSource.USER)
        val resultMessage = when(resultActionResult) {
            is AssistantActionExecutor.ExecutionResult.Success -> resultActionResult.message
            is AssistantActionExecutor.ExecutionResult.Error -> resultActionResult.error
        }

        _chatMessages.value.find { it.id == messageId }?.let { originalMessage ->
            val updatedMessage = originalMessage.copy(
                message = when (resultActionResult) {
                    is AssistantActionExecutor.ExecutionResult.Success -> {
                        when (resultActionResult.actionType) {
                            "CALL", "SMS", "RESOLVE" -> "${originalMessage.message}\n\n[System]: $resultMessage"
                            else -> originalMessage.message
                        }
                    }
                    is AssistantActionExecutor.ExecutionResult.Error -> {
                        "${originalMessage.message}\n\n[System]: $resultMessage"
                    }
                },
                pendingAction = null
            )
            repository.sendMessage(updatedMessage)
        }
        actionJobs.remove(messageId)
    }
    
    private fun addAiMessage(text: String, alertId: String, contactResults: List<ContactResult>? = null) {
        val message = ChatMessage(alertId = alertId, message = text, isUser = false, timestamp = System.currentTimeMillis(), searchResults = contactResults)
        viewModelScope.launch { repository.sendMessage(message) }
    }

    private fun generateRecommendation(type: String?): String {
        return try {
            when (type?.uppercase(Locale.getDefault())) {
                "KNOWN_FACE" -> "Known person detected. I can call or message someone if you need."
                "WEAPON" -> "CRITICAL: Weapon detected. I'm ready to call emergency services. What should I do?"
                "SCREAM" -> "I heard a scream. Should I call help?"
                else -> "New alert received. How can I help you? I can Call or SMS your contacts."
            }
        } catch (_: Exception) { "New alert received." }
    }
}
