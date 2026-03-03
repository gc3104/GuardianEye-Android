package com.example.guardianeye.ui.settings

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.guardianeye.ai.AssistantModelManager
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.ChatRepository
import com.example.guardianeye.data.repository.FootageManager
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val assistantModelManager: AssistantModelManager,
    private val mpinStorage: MpinStorage,
    private val chatRepository: ChatRepository,
    private val alertRepository: AlertRepository,
    private val footageManager: FootageManager,
    private val workManager: WorkManager
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _isModelDownloading = MutableStateFlow(false)
    val isModelDownloading: StateFlow<Boolean> = _isModelDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val isMpinSet = withContext(Dispatchers.IO) { mpinStorage.hasMpin() }
            val mpinTimeout = preferenceManager.getMpinTimeout()
            val emergencyContact = preferenceManager.getEmergencyContact() ?: ""
            val serverUrl = preferenceManager.getServerUrl() ?: "http://10.0.2.2:8000"
            val streamUrl = preferenceManager.getStreamUrl() ?: "ws://10.0.2.2:8000/ws"
            val aiModelUrl = preferenceManager.getAiModelUrl()
            val aiModelFilename = assistantModelManager.getModelName()
            val footageDirectoryUri = preferenceManager.getFootageDirectory()?.toUri()
            
            val alertFormat = preferenceManager.getAlertFormatTemplate() ?: "[{priority}] {type} detected: {description}"

            _settingsState.update { state ->
                state.copy(
                    currentUser = auth.currentUser,
                    emergencyContact = emergencyContact,
                    serverUrl = serverUrl,
                    streamUrl = streamUrl,
                    aiModelUrl = aiModelUrl,
                    aiModelFilename = aiModelFilename,
                    footageDirectoryUri = footageDirectoryUri,
                    alertFormatTemplate = alertFormat,
                    isMpinSet = isMpinSet,
                    mpinTimeoutStr = mpinTimeout.toString(),
                    
                    knownFaceAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_KNOWN_FACE),
                    faceRecognitionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION),
                    maskDetectionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_MASK_DETECTION),
                    unknownFaceAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE),
                    weaponAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_WEAPON),
                    screamAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_SCREAM),
                    
                    criticalSoundName = preferenceManager.getNotificationSoundName("CRITICAL") ?: "Default",
                    highSoundName = preferenceManager.getNotificationSoundName("HIGH") ?: "Default",
                    mediumSoundName = preferenceManager.getNotificationSoundName("MEDIUM") ?: "Default",
                    lowSoundName = preferenceManager.getNotificationSoundName("LOW") ?: "Default",
                    
                    panicTimerStr = preferenceManager.getPanicTimer().toString(),
                    panicMessage = preferenceManager.getPanicMessage(),
                    
                    saveAlertsLocally = preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY),
                    saveChatsLocally = preferenceManager.getAlertPreference(PreferenceManager.SAVE_CHATS_LOCALLY),
                    useChatbot = preferenceManager.getAlertPreference(PreferenceManager.USE_CHATBOT),
                    useProgrammaticFallback = preferenceManager.getAlertPreference(PreferenceManager.USE_PROGRAMMATIC_FALLBACK),
                    useAiConstraints = preferenceManager.getAlertPreference(PreferenceManager.USE_AI_CONSTRAINTS),
                    
                    aiMaxTokens = preferenceManager.getAiMaxTokens(),
                    aiTopK = preferenceManager.getAiTopK(),
                    aiTopP = preferenceManager.getAiTopP(),
                    aiTemperature = preferenceManager.getAiTemperature(),
                    aiBackend = preferenceManager.getAiBackend(),
                    
                    alertRetentionDays = preferenceManager.getAlertRetentionDays(),
                    chatRetentionDays = preferenceManager.getChatRetentionDays(),
                    compressionDelayDays = preferenceManager.getCompressionDelayDays(),
                    archiveRetentionDays = preferenceManager.getArchiveRetentionDays(),
                    downloadedModels = assistantModelManager.getDownloadedModels().map { it.name }
                )
            }
        }
    }

    fun updateEmergencyContact(contact: String) {
        _settingsState.update { it.copy(emergencyContact = contact) }
    }

    fun saveEmergencyContact() {
        viewModelScope.launch {
            preferenceManager.saveEmergencyContact(_settingsState.value.emergencyContact)
            showToast("Contact Saved")
        }
    }

    fun updateServerUrl(url: String) {
        val streamUrl = extractStreamUrl(url)
        _settingsState.update { it.copy(serverUrl = url, streamUrl = streamUrl) }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            preferenceManager.saveServerUrl(_settingsState.value.serverUrl)
            preferenceManager.saveStreamUrl(_settingsState.value.streamUrl)
            showToast("Server & Stream URL Saved")
        }
    }

    fun updateAiModelUrl(url: String) {
        _settingsState.update { it.copy(aiModelUrl = url) }
    }

    fun saveAiModelUrl() {
        viewModelScope.launch {
            preferenceManager.saveAiModelUrl(_settingsState.value.aiModelUrl)
            showToast("URL Saved")
        }
    }

    fun updateAiModelFilename(filename: String) {
        _settingsState.update { it.copy(aiModelFilename = filename) }
    }

    fun saveModelName() {
        viewModelScope.launch {
            preferenceManager.saveAiModelFilename(_settingsState.value.aiModelFilename)
            showToast("Model Filename Saved")
        }
    }

    fun downloadModel() {
        val url = _settingsState.value.aiModelUrl
        val filename = _settingsState.value.aiModelFilename
        if (url.isBlank() || filename.isBlank()) {
            showToast("Please enter model URL and filename")
            return
        }
        val workId = assistantModelManager.downloadModel(url, filename)
        observeWork(workId)
        _isModelDownloading.value = true
        _downloadProgress.value = 0f
    }

    private fun observeWork(workId: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(workId).asFlow().collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt("progress", 0)
                        _downloadProgress.value = progress / 100f
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _isModelDownloading.value = false
                        refreshDownloadedModels()
                        showToast("AI Model Downloaded")
                    }
                    WorkInfo.State.FAILED -> {
                        _isModelDownloading.value = false
                        showToast("Download Failed")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun refreshDownloadedModels() {
        viewModelScope.launch {
            val models = assistantModelManager.getDownloadedModels().map { it.name }
            _settingsState.update { it.copy(downloadedModels = models) }
        }
    }

    fun importModel(uri: Uri, filename: String) {
        viewModelScope.launch {
            if (assistantModelManager.importModel(uri, filename)) {
                refreshDownloadedModels()
                showToast("Model Imported")
            } else {
                showToast("Import Failed")
            }
        }
    }

    fun deleteModel(filename: String?) {
        viewModelScope.launch {
            if (assistantModelManager.deleteModel(filename)) {
                refreshDownloadedModels()
                showToast("Model Deleted")
            }
        }
    }

    fun switchActiveModel(filename: String) {
        viewModelScope.launch {
            preferenceManager.saveAiModelFilename(filename)
            _settingsState.update { it.copy(aiModelFilename = filename) }
            showToast("Switched to $filename")
        }
    }

    fun updateAlertPreference(key: String, enabled: Boolean) {
        viewModelScope.launch {
            val prefKey = when (key) {
                "ALERT_KNOWN_FACE" -> PreferenceManager.ALERT_KNOWN_FACE
                "ALERT_FACE_RECOGNITION" -> PreferenceManager.ALERT_FACE_RECOGNITION
                "ALERT_MASK_DETECTION" -> PreferenceManager.ALERT_MASK_DETECTION
                "ALERT_UNKNOWN_FACE" -> PreferenceManager.ALERT_UNKNOWN_FACE
                "ALERT_WEAPON" -> PreferenceManager.ALERT_WEAPON
                "ALERT_SCREAM" -> PreferenceManager.ALERT_SCREAM
                "SAVE_ALERTS_LOCALLY" -> PreferenceManager.SAVE_ALERTS_LOCALLY
                "SAVE_CHATS_LOCALLY" -> PreferenceManager.SAVE_CHATS_LOCALLY
                "USE_CHATBOT" -> PreferenceManager.USE_CHATBOT
                "USE_PROGRAMMATIC_FALLBACK" -> PreferenceManager.USE_PROGRAMMATIC_FALLBACK
                "USE_AI_CONSTRAINTS" -> PreferenceManager.USE_AI_CONSTRAINTS
                else -> null
            }

            if (prefKey != null) {
                preferenceManager.saveAlertPreference(prefKey, enabled)
                _settingsState.update { state ->
                    when (key) {
                        "ALERT_KNOWN_FACE" -> state.copy(knownFaceAlert = enabled)
                        "ALERT_FACE_RECOGNITION" -> state.copy(faceRecognitionAlert = enabled)
                        "ALERT_MASK_DETECTION" -> state.copy(maskDetectionAlert = enabled)
                        "ALERT_UNKNOWN_FACE" -> state.copy(unknownFaceAlert = enabled)
                        "ALERT_WEAPON" -> state.copy(weaponAlert = enabled)
                        "ALERT_SCREAM" -> state.copy(screamAlert = enabled)
                        "SAVE_ALERTS_LOCALLY" -> state.copy(saveAlertsLocally = enabled)
                        "SAVE_CHATS_LOCALLY" -> state.copy(saveChatsLocally = enabled)
                        "USE_CHATBOT" -> state.copy(useChatbot = enabled)
                        "USE_PROGRAMMATIC_FALLBACK" -> state.copy(useProgrammaticFallback = enabled)
                        "USE_AI_CONSTRAINTS" -> state.copy(useAiConstraints = enabled)
                        else -> state
                    }
                }
            }
        }
    }

    fun saveAlertFormat(template: String) {
        viewModelScope.launch {
            preferenceManager.saveAlertFormatTemplate(template)
            _settingsState.update { it.copy(alertFormatTemplate = template) }
            showToast("Format Updated")
        }
    }

    fun updateAiMaxTokens(tokens: Int) {
        viewModelScope.launch {
            preferenceManager.saveAiMaxTokens(tokens)
            _settingsState.update { it.copy(aiMaxTokens = tokens) }
        }
    }

    fun updateAiTopK(topK: Int) {
        viewModelScope.launch {
            preferenceManager.saveAiTopK(topK)
            _settingsState.update { it.copy(aiTopK = topK) }
        }
    }

    fun updateAiTopP(topP: Float) {
        viewModelScope.launch {
            preferenceManager.saveAiTopP(topP)
            _settingsState.update { it.copy(aiTopP = topP) }
        }
    }

    fun updateAiTemperature(temp: Float) {
        viewModelScope.launch {
            preferenceManager.saveAiTemperature(temp)
            _settingsState.update { it.copy(aiTemperature = temp) }
        }
    }

    fun updateAiBackend(backend: String) {
        viewModelScope.launch {
            preferenceManager.saveAiBackend(backend)
            _settingsState.update { it.copy(aiBackend = backend) }
        }
    }

    fun saveAlertRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveAlertRetentionDays(days)
            _settingsState.update { it.copy(alertRetentionDays = days) }
        }
    }

    fun saveChatRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveChatRetentionDays(days)
            _settingsState.update { it.copy(chatRetentionDays = days) }
        }
    }

    fun saveCompressionDelayDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveCompressionDelayDays(days)
            _settingsState.update { it.copy(compressionDelayDays = days) }
        }
    }

    fun saveArchiveRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.saveArchiveRetentionDays(days)
            _settingsState.update { it.copy(archiveRetentionDays = days) }
        }
    }

    fun saveFootageDirectory(uri: Uri) {
        viewModelScope.launch {
            preferenceManager.saveFootageDirectory(uri.toString())
            _settingsState.update { it.copy(footageDirectoryUri = uri) }
            showToast("Folder Saved")
        }
    }

    fun deleteOldAlerts(days: Int) {
        viewModelScope.launch {
            alertRepository.deleteOldAlerts(days)
            showToast("Old alerts deleted")
        }
    }

    fun deleteOldChats(days: Int) {
        viewModelScope.launch {
            chatRepository.deleteOldMessages(days)
            showToast("Old chats deleted")
        }
    }

    fun clearChatHistory() = viewModelScope.launch {
        chatRepository.deleteAllMessages()
        showToast("Chat history cleared")
    }

    fun clearAlertHistory() = viewModelScope.launch {
        alertRepository.deleteAllAlerts()
        showToast("Alert history cleared")
    }

    fun deleteAllFootage() {
        viewModelScope.launch {
            if (footageManager.deleteAllFootage()) showToast("All footage deleted")
        }
    }

    fun runSmartArchival(compressionDays: Int, period: String, archiveRetention: Int) {
        viewModelScope.launch {
            footageManager.runManualLifecycle(compressionDays, period, archiveRetention)
            showToast("Lifecycle task started")
        }
    }

    fun updatePanicTimerStr(timer: String) {
        _settingsState.update { it.copy(panicTimerStr = timer) }
    }

    fun updatePanicMessage(message: String) {
        _settingsState.update { it.copy(panicMessage = message) }
    }

    fun savePanicSettings() {
        viewModelScope.launch {
            val timer = _settingsState.value.panicTimerStr.toIntOrNull() ?: 5
            preferenceManager.savePanicTimer(timer)
            preferenceManager.savePanicMessage(_settingsState.value.panicMessage)
            showToast("Panic Settings Saved")
        }
    }

    fun updateMpinTimeoutStr(timeout: String) {
        _settingsState.update { it.copy(mpinTimeoutStr = timeout) }
    }

    fun saveMpinTimeout() {
        viewModelScope.launch {
            val timeout = _settingsState.value.mpinTimeoutStr.toIntOrNull() ?: 60
            preferenceManager.saveMpinTimeout(timeout)
            showToast("Timeout Saved")
        }
    }

    fun refreshMpinState() {
        viewModelScope.launch {
            val isMpinSet = withContext(Dispatchers.IO) { mpinStorage.hasMpin() }
            _settingsState.update { it.copy(isMpinSet = isMpinSet) }
        }
    }

    fun saveNotificationSound(priority: String, uri: Uri, name: String) {
        viewModelScope.launch {
            preferenceManager.saveNotificationSound(priority, uri.toString(), name)
            when (priority) {
                "CRITICAL" -> _settingsState.update { it.copy(criticalSoundName = name) }
                "HIGH" -> _settingsState.update { it.copy(highSoundName = name) }
                "MEDIUM" -> _settingsState.update { it.copy(mediumSoundName = name) }
                "LOW" -> _settingsState.update { it.copy(lowSoundName = name) }
            }
            showToast("Sound Updated")
        }
    }

    fun importModelSettings(json: String) {
        try {
            val obj = JSONObject(json)
            val modelUrl = obj.optString("model_url")
            val modelName = obj.optString("model_name")
            val template = obj.optString("format_template")
            
            if (modelUrl.isNotEmpty()) updateAiModelUrl(modelUrl)
            if (modelName.isNotEmpty()) updateAiModelFilename(modelName)
            if (template.isNotEmpty()) saveAlertFormat(template)
            
            showToast("Settings Imported")
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Import failed", e)
            showToast("Invalid JSON")
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun extractStreamUrl(serverUrl: String): String {
        return try {
            val uri = serverUrl.toUri()
            val wsScheme = if (uri.scheme == "https") "wss" else "ws"
            "$wsScheme://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}/ws"
        } catch (e: Exception) { 
            Log.e("SettingsViewModel", "Stream URL extraction failed", e)
            "" 
        }
    }

    private fun showToast(message: String) { _toastMessage.value = message }
    fun clearToast() { _toastMessage.value = null }
}
