package com.example.guardianeye.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val EMERGENCY_CONTACT = stringPreferencesKey("emergency_contact")
        val SERVER_URL = stringPreferencesKey("server_url")
        val STREAM_URL = stringPreferencesKey("stream_url")
        val FOOTAGE_DIRECTORY_URI = stringPreferencesKey("footage_directory_uri")
        val AI_MODEL_URL = stringPreferencesKey("ai_model_url")
        val AI_MODEL_FILENAME = stringPreferencesKey("ai_model_filename")
        val ALERT_FORMAT_TEMPLATE = stringPreferencesKey("alert_format_template")
        
        val ALERT_KNOWN_FACE = booleanPreferencesKey("alert_known_face")
        val ALERT_FACE_RECOGNITION = booleanPreferencesKey("alert_face_recognition")
        val ALERT_MASK_DETECTION = booleanPreferencesKey("alert_mask_detection")
        val ALERT_UNKNOWN_FACE = booleanPreferencesKey("alert_unknown_face")
        val ALERT_WEAPON = booleanPreferencesKey("alert_weapon")
        val ALERT_SCREAM = booleanPreferencesKey("alert_scream")
        
        val SOUND_CRITICAL = stringPreferencesKey("sound_critical")
        val SOUND_NAME_CRITICAL = stringPreferencesKey("sound_name_critical")
        val SOUND_HIGH = stringPreferencesKey("sound_high")
        val SOUND_NAME_HIGH = stringPreferencesKey("sound_name_high")
        val SOUND_MEDIUM = stringPreferencesKey("sound_medium")
        val SOUND_NAME_MEDIUM = stringPreferencesKey("sound_name_medium")
        val SOUND_LOW = stringPreferencesKey("sound_low")
        val SOUND_NAME_LOW = stringPreferencesKey("sound_name_low")

        val PANIC_TIMER_SECONDS = intPreferencesKey("panic_timer_seconds")
        val PANIC_MESSAGE = stringPreferencesKey("panic_message")

        val MPIN_TIMEOUT_SECONDS = intPreferencesKey("mpin_timeout_seconds")
        
        val SAVE_ALERTS_LOCALLY = booleanPreferencesKey("save_alerts_locally")
        val SAVE_CHATS_LOCALLY = booleanPreferencesKey("save_chats_locally")

        val USE_CHATBOT = booleanPreferencesKey("use_chatbot")
        val USE_PROGRAMMATIC_FALLBACK = booleanPreferencesKey("use_programmatic_fallback")
        val USE_AI_CONSTRAINTS = booleanPreferencesKey("use_ai_constraints")
        
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_TOP_K = intPreferencesKey("ai_top_k")
        val AI_TOP_P = floatPreferencesKey("ai_top_p")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val AI_BACKEND = stringPreferencesKey("ai_backend")

        val ARCHIVE_RETENTION_DAYS = intPreferencesKey("archive_retention_days")
        val COMPRESSION_DELAY_DAYS = intPreferencesKey("compression_delay_days")
        val ALERT_RETENTION_DAYS = intPreferencesKey("alert_retention_days")
        val CHAT_RETENTION_DAYS = intPreferencesKey("chat_retention_days")
    }

    suspend fun saveEmergencyContact(contact: String) {
        context.dataStore.edit { preferences -> preferences[EMERGENCY_CONTACT] = contact }
    }

    suspend fun getEmergencyContact(): String? {
        return context.dataStore.data.map { it[EMERGENCY_CONTACT] }.first()
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
        return context.dataStore.data.map { it[key] }.first()
    }

    suspend fun getNotificationSoundName(priority: String): String? {
        val key = when(priority) {
            "CRITICAL" -> SOUND_NAME_CRITICAL
            "HIGH" -> SOUND_NAME_HIGH
            "MEDIUM" -> SOUND_NAME_MEDIUM
            "LOW" -> SOUND_NAME_LOW
            else -> return null
        }
        return context.dataStore.data.map { it[key] }.first()
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url }
    }

    suspend fun getServerUrl(): String? {
        return context.dataStore.data.map { it[SERVER_URL] }.first()
    }

    suspend fun saveStreamUrl(url: String) {
        context.dataStore.edit { it[STREAM_URL] = url }
    }

    suspend fun getStreamUrl(): String? {
        return context.dataStore.data.map { it[STREAM_URL] }.first()
    }

    suspend fun saveAiModelUrl(url: String) {
        context.dataStore.edit { it[AI_MODEL_URL] = url }
    }

    suspend fun getAiModelUrl(): String {
        return context.dataStore.data.map { preferences ->
            val url = preferences[AI_MODEL_URL]
            if (url.isNullOrBlank()) "https://github.com/gc3104/GuardianEye-Android/releases/download/v1.0.0/gemma3-1b-it-int4.task" else url
        }.first()
    }

    suspend fun saveAiModelFilename(filename: String) {
        context.dataStore.edit { it[AI_MODEL_FILENAME] = filename }
    }

    suspend fun getAiModelFilename(): String {
        return context.dataStore.data.map { preferences ->
            val filename = preferences[AI_MODEL_FILENAME]
            if (filename.isNullOrBlank()) "gemma3-1b-it-int4.task" else filename
        }.first()
    }

    suspend fun saveAlertFormatTemplate(template: String) {
        context.dataStore.edit { it[ALERT_FORMAT_TEMPLATE] = template }
    }

    suspend fun getAlertFormatTemplate(): String? {
        return context.dataStore.data.map { it[ALERT_FORMAT_TEMPLATE] }.first()
    }

    suspend fun saveFootageDirectory(uri: String) {
        context.dataStore.edit { it[FOOTAGE_DIRECTORY_URI] = uri }
    }

    suspend fun getFootageDirectory(): String? {
        return context.dataStore.data.map { it[FOOTAGE_DIRECTORY_URI] }.first()
    }
    
    suspend fun saveAlertPreference(key: Preferences.Key<Boolean>, enabled: Boolean) {
        context.dataStore.edit { it[key] = enabled }
    }

    suspend fun getAlertPreference(key: Preferences.Key<Boolean>, defaultValue: Boolean = true): Boolean {
        return context.dataStore.data.map { it[key] ?: defaultValue }.first()
    }

    suspend fun savePanicTimer(seconds: Int) {
        context.dataStore.edit { it[PANIC_TIMER_SECONDS] = seconds }
    }

    suspend fun getPanicTimer(): Int {
        return context.dataStore.data.map { it[PANIC_TIMER_SECONDS] ?: 5 }.first()
    }

    suspend fun savePanicMessage(message: String) {
        context.dataStore.edit { it[PANIC_MESSAGE] = message }
    }

    suspend fun getPanicMessage(): String {
        return context.dataStore.data.map { it[PANIC_MESSAGE] ?: "Emergency! Please help!" }.first()
    }

    suspend fun saveMpinTimeout(seconds: Int) {
        context.dataStore.edit { it[MPIN_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun getMpinTimeout(): Int {
        return context.dataStore.data.map { it[MPIN_TIMEOUT_SECONDS] ?: 60 }.first()
    }

    suspend fun saveAiMaxTokens(value: Int) {
        context.dataStore.edit { it[AI_MAX_TOKENS] = value }
    }
    suspend fun getAiMaxTokens(): Int {
        return context.dataStore.data.map { it[AI_MAX_TOKENS] ?: 1024 }.first()
    }

    suspend fun saveAiTopK(value: Int) {
        context.dataStore.edit { it[AI_TOP_K] = value }
    }
    suspend fun getAiTopK(): Int {
        return context.dataStore.data.map { it[AI_TOP_K] ?: 40 }.first()
    }

    suspend fun saveAiTopP(value: Float) {
        context.dataStore.edit { it[AI_TOP_P] = value }
    }
    suspend fun getAiTopP(): Float {
        return context.dataStore.data.map { it[AI_TOP_P] ?: 0.95f }.first()
    }

    suspend fun saveAiTemperature(value: Float) {
        context.dataStore.edit { it[AI_TEMPERATURE] = value }
    }
    suspend fun getAiTemperature(): Float {
        return context.dataStore.data.map { it[AI_TEMPERATURE] ?: 0.7f }.first()
    }

    suspend fun saveAiBackend(value: String) {
        context.dataStore.edit { it[AI_BACKEND] = value }
    }
    suspend fun getAiBackend(): String {
        return context.dataStore.data.map { it[AI_BACKEND] ?: "CPU" }.first()
    }

    suspend fun saveIntPreference(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getIntPreference(key: Preferences.Key<Int>, defaultValue: Int): Int {
        return context.dataStore.data.map { it[key] ?: defaultValue }.first()
    }

    suspend fun getArchiveRetentionDays() = getIntPreference(ARCHIVE_RETENTION_DAYS, 30)
    suspend fun saveArchiveRetentionDays(days: Int) = saveIntPreference(ARCHIVE_RETENTION_DAYS, days)

    suspend fun getCompressionDelayDays() = getIntPreference(COMPRESSION_DELAY_DAYS, 1)
    suspend fun saveCompressionDelayDays(days: Int) = saveIntPreference(COMPRESSION_DELAY_DAYS, days)

    suspend fun getAlertRetentionDays() = getIntPreference(ALERT_RETENTION_DAYS, 30)
    suspend fun saveAlertRetentionDays(days: Int) = saveIntPreference(ALERT_RETENTION_DAYS, days)

    suspend fun getChatRetentionDays() = getIntPreference(CHAT_RETENTION_DAYS, 30)
    suspend fun saveChatRetentionDays(days: Int) = saveIntPreference(CHAT_RETENTION_DAYS, days)
}
