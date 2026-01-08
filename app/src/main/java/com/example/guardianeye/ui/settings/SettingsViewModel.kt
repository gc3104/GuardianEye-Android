package com.example.guardianeye.ui.settings

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.guardianeye.ai.AiModelManager
import com.example.guardianeye.data.local.AppDatabase
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.ChatRepository
import com.example.guardianeye.data.repository.FootageManager
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)
    private val aiModelManager = AiModelManager(application)
    private val mpinStorage = MpinStorage(application)
    private val auth = FirebaseAuth.getInstance()
    private val workManager = WorkManager.getInstance(application)

    private val database = AppDatabase.getDatabase(application)
    private val chatRepository: ChatRepository
    private val alertRepository: AlertRepository
    private val footageManager: FootageManager = FootageManager(application)

    // State
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _isModelDownloading = MutableStateFlow(false)
    val isModelDownloading: StateFlow<Boolean> = _isModelDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Store current download work ID
    private var currentDownloadId: UUID? = null

    init {
        chatRepository = ChatRepository(database.chatDao())
        alertRepository = AlertRepository(database.alertDao(), preferenceManager)
        loadSettings()
    }
    
    private fun observeWork(workId: UUID) {
        currentDownloadId = workId
        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(workId).asFlow().collect { workInfo ->
                if (workInfo == null) return@collect
                
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        _isModelDownloading.value = true
                        val progress = workInfo.progress.getInt("progress", 0)
                        _downloadProgress.value = progress / 100f
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _isModelDownloading.value = false
                        _downloadProgress.value = 1f
                        _settingsState.update { it.copy(isModelDownloaded = true) }
                        showToast("AI Model Downloaded")
                    }
                    WorkInfo.State.FAILED -> {
                        _isModelDownloading.value = false
                        showToast("Download Failed")
                    }
                    WorkInfo.State.CANCELLED -> {
                        _isModelDownloading.value = false
                    }
                    else -> { /* ENQUEUED, BLOCKED */ }
                }
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            // Load MPIN state on IO dispatcher to avoid blocking main thread
            val isMpinSet = withContext(Dispatchers.IO) { mpinStorage.hasMpin() }
            
            _settingsState.update { 
                it.copy(
                    currentUser = auth.currentUser,
                    emergencyContact = preferenceManager.getEmergencyContact() ?: "",
                    // Default to 10.0.2.2 for Android Emulator access to localhost
                    streamUrl = preferenceManager.getStreamUrl() ?: "ws://10.0.2.2:8000/ws",
                    aiModelUrl = preferenceManager.getAiModelUrl() ?: "",
                    aiModelFilename = aiModelManager.getModelName(),
                    footageDirectoryUri = preferenceManager.getFootageDirectory()?.toUri(),
                    
                    criticalSoundName = preferenceManager.getNotificationSoundName("CRITICAL") ?: "Default",
                    highSoundName = preferenceManager.getNotificationSoundName("HIGH") ?: "Default",
                    mediumSoundName = preferenceManager.getNotificationSoundName("MEDIUM") ?: "Default",
                    lowSoundName = preferenceManager.getNotificationSoundName("LOW") ?: "Default",
                    
                    intruderAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_INTRUDER),
                    faceRecognitionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION),
                    maskDetectionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_MASK_DETECTION),
                    unknownFaceAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE),
                    weaponAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_WEAPON),
                    screamAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_SCREAM),
                    
                    isModelDownloaded = aiModelManager.isModelDownloaded(),
                    panicTimerStr = preferenceManager.getPanicTimer().toString(),
                    panicMessage = preferenceManager.getPanicMessage(),
                    
                    isMpinSet = isMpinSet,
                    mpinTimeoutStr = preferenceManager.getMpinTimeout().toString(),

                    saveAlertsLocally = preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY),
                    saveChatsLocally = preferenceManager.getAlertPreference(PreferenceManager.SAVE_CHATS_LOCALLY),
                    useChatbot = preferenceManager.getAlertPreference(PreferenceManager.USE_CHATBOT)
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

    fun updateStreamUrl(url: String) {
        _settingsState.update { it.copy(streamUrl = url) }
    }
    
    fun saveStreamUrl() {
        viewModelScope.launch {
            preferenceManager.saveStreamUrl(_settingsState.value.streamUrl)
            showToast("Stream URL Saved")
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
            val name = _settingsState.value.aiModelFilename
            aiModelManager.setModelName(name)
            // Re-check status for new filename
            val isDownloaded = aiModelManager.isModelDownloaded()
             _settingsState.update { it.copy(isModelDownloaded = isDownloaded) }
            showToast("Model Filename Saved")
        }
    }

    fun downloadModel() {
        val url = _settingsState.value.aiModelUrl
        val filename = _settingsState.value.aiModelFilename
        if (url.isBlank()) {
            showToast("Please enter a model URL first")
            return
        }
        if (filename.isBlank()) {
            showToast("Please enter a model filename first")
            return
        }

        // Trigger WorkManager via AiModelManager and observe only THIS request
        val workId = aiModelManager.downloadModel(url, filename)
        observeWork(workId)
        
        // Optimistic UI update
        _isModelDownloading.value = true
        _downloadProgress.value = 0f
        showToast("Download started in background...")
    }

    fun deleteModel() {
        viewModelScope.launch {
            val success = aiModelManager.deleteModel()
            if (success) {
                _settingsState.update { it.copy(isModelDownloaded = false) }
                showToast("AI Model Deleted")
            } else {
                showToast("Failed to delete model")
            }
        }
    }

    fun updateAlertPreference(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.saveAlertPreference(key, enabled)
            // Update local state immediately for UI responsiveness
             _settingsState.update { 
                 when(key) {
                     PreferenceManager.ALERT_INTRUDER -> it.copy(intruderAlert = enabled)
                     PreferenceManager.ALERT_FACE_RECOGNITION -> it.copy(faceRecognitionAlert = enabled)
                     PreferenceManager.ALERT_MASK_DETECTION -> it.copy(maskDetectionAlert = enabled)
                     PreferenceManager.ALERT_UNKNOWN_FACE -> it.copy(unknownFaceAlert = enabled)
                     PreferenceManager.ALERT_WEAPON -> it.copy(weaponAlert = enabled)
                     PreferenceManager.ALERT_SCREAM -> it.copy(screamAlert = enabled)
                     PreferenceManager.SAVE_ALERTS_LOCALLY -> it.copy(saveAlertsLocally = enabled)
                     PreferenceManager.SAVE_CHATS_LOCALLY -> it.copy(saveChatsLocally = enabled)
                     PreferenceManager.USE_CHATBOT -> it.copy(useChatbot = enabled)
                     else -> it
                 }
             }
        }
    }
    
    fun updatePanicTimerStr(value: String) {
         _settingsState.update { it.copy(panicTimerStr = value) }
    }
    
    fun updatePanicMessage(value: String) {
         _settingsState.update { it.copy(panicMessage = value) }
    }
    
    fun savePanicSettings() {
        viewModelScope.launch {
            val seconds = _settingsState.value.panicTimerStr.toIntOrNull() ?: 5
            preferenceManager.savePanicTimer(seconds)
            preferenceManager.savePanicMessage(_settingsState.value.panicMessage)
            showToast("Panic Settings Saved")
        }
    }
    
    fun saveFootageDirectory(uri: Uri) {
         viewModelScope.launch {
            preferenceManager.saveFootageDirectory(uri.toString())
            _settingsState.update { it.copy(footageDirectoryUri = uri) }
            showToast("Footage Directory Saved")
        }
    }

    fun saveNotificationSound(priority: String, uri: Uri, name: String) {
        viewModelScope.launch {
             preferenceManager.saveNotificationSound(priority, uri.toString(), name)
             _settingsState.update { 
                 when (priority) {
                        "CRITICAL" -> it.copy(criticalSoundName = name)
                        "HIGH" -> it.copy(highSoundName = name)
                        "MEDIUM" -> it.copy(mediumSoundName = name)
                        "LOW" -> it.copy(lowSoundName = name)
                        else -> it
                 }
             }
             showToast("$priority Sound Saved")
        }
    }
    
    fun refreshMpinState() {
        viewModelScope.launch {
            val isMpinSet = withContext(Dispatchers.IO) { mpinStorage.hasMpin() }
            _settingsState.update { it.copy(isMpinSet = isMpinSet) }
        }
    }
    
    fun updateMpinTimeoutStr(value: String) {
        _settingsState.update { it.copy(mpinTimeoutStr = value) }
    }
    
    fun saveMpinTimeout() {
        viewModelScope.launch {
             val seconds = _settingsState.value.mpinTimeoutStr.toIntOrNull() ?: 60
             preferenceManager.saveMpinTimeout(seconds)
             showToast("Timeout Settings Saved")
        }
    }

    // Data Management Functions
    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
            showToast("Chat history cleared")
        }
    }
    
    fun deleteOldChats(days: Int) {
         viewModelScope.launch {
             chatRepository.deleteOldMessages(days)
             showToast("Deleted chats older than $days days")
         }
    }

    fun clearAlertHistory() {
        viewModelScope.launch {
            alertRepository.deleteAllAlerts()
            showToast("Alert history cleared")
        }
    }
    
    fun deleteOldAlerts(days: Int) {
         viewModelScope.launch {
             alertRepository.deleteOldAlerts(days)
             showToast("Deleted alerts older than $days days")
         }
    }

    fun deleteOldFootage(retentionDays: Int) {
        viewModelScope.launch {
            val directoryUri = _settingsState.value.footageDirectoryUri
            if (directoryUri == null) {
                showToast("No footage directory selected")
                return@launch
            }

            // Using the new FootageManager for deleting raw files logic if needed, 
            // but for simple retention deletion, we can reuse similar logic or add to FootageManager.
            // For simplicity/consistency, let's keep the simple logic here or delegate.
            // Let's delegate part of it or keep it simple as it was for now, 
            // but the "Run Lifecycle" button is the main feature.
            
            // Re-implementing using FootageManager call would be cleaner but let's stick to the existing method 
            // unless we want to move everything to FootageManager.
            // Actually, let's keep the "Delete Now" button logic simple here for raw files.
            
            val context = getApplication<Application>()
            try {
                val docFile = DocumentFile.fromTreeUri(context, directoryUri)
                if (docFile != null && docFile.isDirectory) {
                    val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
                    var deletedCount = 0
                    
                    withContext(Dispatchers.IO) {
                        docFile.listFiles().forEach { file ->
                             if (file.isFile && file.lastModified() < cutoffTime) {
                                 if (file.delete()) {
                                     deletedCount++
                                 }
                             }
                        }
                    }
                    showToast("Deleted $deletedCount old files")
                } else {
                    showToast("Cannot access footage directory")
                }
            } catch (e: Exception) {
                showToast("Error cleaning footage: ${e.message}")
            }
        }
    }
    
    fun runSmartArchival(compressionDelay: Int, archivePeriod: String, archiveRetention: Int) {
        viewModelScope.launch {
            val directoryUri = _settingsState.value.footageDirectoryUri
            if (directoryUri == null) {
                showToast("No footage directory selected")
                return@launch
            }
            
            showToast("Starting lifecycle management...")
            try {
                footageManager.runLifecycle(directoryUri, compressionDelay, archivePeriod, archiveRetention)
                showToast("Lifecycle management completed")
            } catch (e: Exception) {
                showToast("Error during lifecycle management: ${e.message}")
            }
        }
    }
    
    fun deleteAllFootage() {
        viewModelScope.launch {
            val directoryUri = _settingsState.value.footageDirectoryUri
            if (directoryUri == null) {
                showToast("No footage directory selected")
                return@launch
            }
            
            showToast("Deleting ALL footage...")
            try {
                footageManager.deleteAllFootage(directoryUri)
                showToast("All footage deleted")
            } catch (e: Exception) {
                showToast("Error deleting footage: ${e.message}")
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
    }
}

data class SettingsState(
    val currentUser: com.google.firebase.auth.FirebaseUser? = null,
    val emergencyContact: String = "",
    val streamUrl: String = "",
    val aiModelUrl: String = "",
    val aiModelFilename: String = "",
    val footageDirectoryUri: Uri? = null,
    
    val criticalSoundName: String = "Default",
    val highSoundName: String = "Default",
    val mediumSoundName: String = "Default",
    val lowSoundName: String = "Default",
    
    val intruderAlert: Boolean = true,
    val faceRecognitionAlert: Boolean = true,
    val maskDetectionAlert: Boolean = true,
    val unknownFaceAlert: Boolean = true,
    val weaponAlert: Boolean = true,
    val screamAlert: Boolean = true,
    
    val isModelDownloaded: Boolean = false,
    
    val panicTimerStr: String = "5",
    val panicMessage: String = "",
    
    val isMpinSet: Boolean = false,
    val mpinTimeoutStr: String = "60",

    val saveAlertsLocally: Boolean = true,
    val saveChatsLocally: Boolean = true,
    val useChatbot: Boolean = true
)
