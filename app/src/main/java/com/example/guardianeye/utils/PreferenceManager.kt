package com.example.guardianeye.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val EMERGENCY_CONTACT = stringPreferencesKey("emergency_contact")
        val STREAM_URL = stringPreferencesKey("stream_url")
        val FOOTAGE_DIRECTORY_URI = stringPreferencesKey("footage_directory_uri")
        val AI_MODEL_URL = stringPreferencesKey("ai_model_url")
        val AI_MODEL_FILENAME = stringPreferencesKey("ai_model_filename")
        
        // Alert Preferences
        val ALERT_INTRUDER = booleanPreferencesKey("alert_intruder")
        val ALERT_FACE_RECOGNITION = booleanPreferencesKey("alert_face_recognition")
        val ALERT_MASK_DETECTION = booleanPreferencesKey("alert_mask_detection")
        val ALERT_UNKNOWN_FACE = booleanPreferencesKey("alert_unknown_face")
        val ALERT_WEAPON = booleanPreferencesKey("alert_weapon")
        val ALERT_SCREAM = booleanPreferencesKey("alert_scream")
        
        // Notification Sound Preferences
        val SOUND_CRITICAL = stringPreferencesKey("sound_critical")
        val SOUND_NAME_CRITICAL = stringPreferencesKey("sound_name_critical")
        val SOUND_HIGH = stringPreferencesKey("sound_high")
        val SOUND_NAME_HIGH = stringPreferencesKey("sound_name_high")
        val SOUND_MEDIUM = stringPreferencesKey("sound_medium")
        val SOUND_NAME_MEDIUM = stringPreferencesKey("sound_name_medium")
        val SOUND_LOW = stringPreferencesKey("sound_low")
        val SOUND_NAME_LOW = stringPreferencesKey("sound_name_low")

        // Panic Button Preferences
        val PANIC_TIMER_SECONDS = intPreferencesKey("panic_timer_seconds")
        val PANIC_MESSAGE = stringPreferencesKey("panic_message")

        // Security Preferences
        val MPIN_TIMEOUT_SECONDS = intPreferencesKey("mpin_timeout_seconds")
        
        // Data Saving Preferences
        val SAVE_ALERTS_LOCALLY = booleanPreferencesKey("save_alerts_locally")
        val SAVE_CHATS_LOCALLY = booleanPreferencesKey("save_chats_locally")

        // AI Preferences
        val USE_CHATBOT = booleanPreferencesKey("use_chatbot")
    }

    suspend fun saveEmergencyContact(contact: String) {
        context.dataStore.edit { preferences ->
            preferences[EMERGENCY_CONTACT] = contact
        }
    }

    suspend fun getEmergencyContact(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[EMERGENCY_CONTACT]
        }.first()
    }

    suspend fun saveNotificationSound(priority: String, uri: String, name: String) {
        val (uriKey, nameKey) = when(priority) {
            "CRITICAL" -> Pair(SOUND_CRITICAL, SOUND_NAME_CRITICAL)
            "HIGH" -> Pair(SOUND_HIGH, SOUND_NAME_HIGH)
            "MEDIUM" -> Pair(SOUND_MEDIUM, SOUND_NAME_MEDIUM)
            "LOW" -> Pair(SOUND_LOW, SOUND_NAME_LOW)
            else -> return
        }
        
        context.dataStore.edit { preferences ->
            preferences[uriKey] = uri
            preferences[nameKey] = name
        }
    }

    suspend fun getNotificationSound(priority: String): String? {
        val key = when(priority) {
            "CRITICAL" -> SOUND_CRITICAL
            "HIGH" -> SOUND_HIGH
            "MEDIUM" -> SOUND_MEDIUM
            "LOW" -> SOUND_LOW
            else -> return null
        }
        return context.dataStore.data.map { preferences ->
            preferences[key]
        }.first()
    }

    suspend fun getNotificationSoundName(priority: String): String? {
        val key = when(priority) {
            "CRITICAL" -> SOUND_NAME_CRITICAL
            "HIGH" -> SOUND_NAME_HIGH
            "MEDIUM" -> SOUND_NAME_MEDIUM
            "LOW" -> SOUND_NAME_LOW
            else -> return null
        }
        return context.dataStore.data.map { preferences ->
            preferences[key]
        }.first()
    }

    suspend fun saveStreamUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[STREAM_URL] = url
        }
    }

    suspend fun getStreamUrl(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[STREAM_URL]
        }.first()
    }

    suspend fun saveAiModelUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODEL_URL] = url
        }
    }

    suspend fun getAiModelUrl(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[AI_MODEL_URL]
        }.first()
    }

    suspend fun saveAiModelFilename(filename: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODEL_FILENAME] = filename
        }
    }

    suspend fun getAiModelFilename(): String {
        return context.dataStore.data.map { preferences ->
            preferences[AI_MODEL_FILENAME] ?: "gemma-3n-E4B-it-int4.task"
        }.first()
    }

    suspend fun saveFootageDirectory(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[FOOTAGE_DIRECTORY_URI] = uri
        }
    }

    suspend fun getFootageDirectory(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[FOOTAGE_DIRECTORY_URI]
        }.first()
    }
    
    // Alert Preference Functions
    suspend fun saveAlertPreference(key: Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }

    suspend fun getAlertPreference(key: Preferences.Key<Boolean>, defaultValue: Boolean = true): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }.first()
    }

    // Panic Button Preference Functions
    suspend fun savePanicTimer(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PANIC_TIMER_SECONDS] = seconds
        }
    }

    suspend fun getPanicTimer(): Int {
        return context.dataStore.data.map { preferences ->
            preferences[PANIC_TIMER_SECONDS] ?: 5 // Default 5 seconds
        }.first()
    }

    suspend fun savePanicMessage(message: String) {
        context.dataStore.edit { preferences ->
            preferences[PANIC_MESSAGE] = message
        }
    }

    suspend fun getPanicMessage(): String {
        return context.dataStore.data.map { preferences ->
            preferences[PANIC_MESSAGE] ?: "Emergency! Please help!"
        }.first()
    }

    // MPIN Functions
    suspend fun saveMpinTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[MPIN_TIMEOUT_SECONDS] = seconds
        }
    }

    suspend fun getMpinTimeout(): Int {
        return context.dataStore.data.map { preferences ->
            preferences[MPIN_TIMEOUT_SECONDS] ?: 60 // Default 60 seconds
        }.first()
    }
}