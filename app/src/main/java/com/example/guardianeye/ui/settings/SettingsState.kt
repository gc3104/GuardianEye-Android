package com.example.guardianeye.ui.settings

import android.net.Uri
import com.example.guardianeye.model.Family
import com.google.firebase.auth.FirebaseUser

data class SettingsState(
    val currentUser: FirebaseUser? = null,
    val family: Family? = null,
    val emergencyContact: String = "",
    val serverUrl: String = "",
    val streamUrl: String = "",
    val aiModelUrl: String = "",
    val aiModelFilename: String = "",
    val footageDirectoryUri: Uri? = null,
    
    val criticalSoundName: String = "Default",
    val highSoundName: String = "Default",
    val mediumSoundName: String = "Default",
    val lowSoundName: String = "Default",
    
    val knownFaceAlert: Boolean = true,
    val faceRecognitionAlert: Boolean = true,
    val maskDetectionAlert: Boolean = true,
    val unknownFaceAlert: Boolean = true,
    val weaponAlert: Boolean = true,
    val screamAlert: Boolean = true,
    
    val isModelDownloaded: Boolean = false,
    val downloadedModels: List<String> = emptyList(),
    val panicTimerStr: String = "5",
    val panicMessage: String = "",
    
    val isMpinSet: Boolean = false,
    val mpinTimeoutStr: String = "60",

    val saveAlertsLocally: Boolean = true,
    val saveChatsLocally: Boolean = true,
    val useChatbot: Boolean = true,
    val useProgrammaticFallback: Boolean = true,
    val useAiConstraints: Boolean = false,

    // LLM Config
    val aiMaxTokens: Int = 1024,
    val aiTopK: Int = 40,
    val aiTopP: Float = 0.95f,
    val aiTemperature: Float = 0.7f,
    val aiBackend: String = "CPU",
    
    // Formatter Settings
    val alertFormatTemplate: String = "[{priority}] {type} detected: {description}",
    val availableFormatters: List<String> = listOf("Default", "Concise", "Detailed", "SMS"),
    
    val archiveRetentionDays: Int = 365,
    val compressionDelayDays: Int = 7,
    val alertRetentionDays: Int = 30,
    val chatRetentionDays: Int = 30
)

/**
 * SettingsIntent was largely unused as ViewModel methods are called directly from the UI.
 * Keeping the sealed class for future pattern consistency if needed, but removing unused members.
 */
sealed class SettingsIntent
