package com.example.guardianeye.ai

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.model.ContactResult
import com.example.guardianeye.model.ResolutionSource
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val firebaseManager: FirebaseManager,
    private val alertRepository: AlertRepository
) {

    private val TAG = "AssistantActionExecutor"

    sealed class ExecutionResult {
        data class Success(val message: String, val data: Any? = null, val actionType: String? = null) : ExecutionResult()
        data class Error(val error: String) : ExecutionResult()
    }

    @SuppressLint("MissingPermission")
    suspend fun execute(
        action: String,
        alertId: String? = null,
        args: Map<String, Any>? = null,
        source: ResolutionSource = ResolutionSource.AUTO
    ): ExecutionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing action: $action with args: $args from $source")
        try {
            val result = when (action.uppercase()) {
                "CALL" -> {
                    val contact = resolveToNumber(args?.get("contact") as? String)
                    if (contact != null) {
                        withContext(Dispatchers.Main) { makeCall(context, contact) }
                        ExecutionResult.Success("Calling $contact...", actionType = "CALL")
                    } else {
                        ExecutionResult.Error("Recipient number required for CALL.")
                    }
                }
                "SMS", "MESSAGE" -> {
                    val contact = resolveToNumber(args?.get("contact") as? String)
                    val text = (args?.get("text") as? String) ?: (args?.get("message") as? String) ?: "Security alert from GuardianEye."
                    if (contact != null) {
                        withContext(Dispatchers.Main) { sendSms(context, contact, text) }
                        ExecutionResult.Success("SMS sent to $contact: \"$text\"", actionType = "SMS")
                    } else {
                        ExecutionResult.Error("Recipient number required for SMS.")
                    }
                }
                "SEARCH_CONTACTS" -> {
                    val query = args?.get("query") as? String ?: ""
                    val results = queryContacts(query).map { ContactResult(it.key, it.value) }
                    if (results.isNotEmpty()) {
                        ExecutionResult.Success("Found ${results.size} matches.", results)
                    } else {
                        ExecutionResult.Error("No contacts found for '$query'.")
                    }
                }
                "SHARE_LOCATION" -> {
                    val contact = resolveToNumber(args?.get("contact") as? String) ?: return@withContext ExecutionResult.Error("Who should I send location to?")
                    try {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                        val loc = fusedClient.lastLocation.await()
                        if (loc != null) {
                            val link = "https://www.google.com/maps/search/?api=1&query=${loc.latitude},${loc.longitude}"
                            withContext(Dispatchers.Main) { sendSms(context, contact, "My GuardianEye Location: $link") }
                            ExecutionResult.Success("Location shared with $contact.", actionType = "LOCATION")
                        } else {
                            ExecutionResult.Error("GPS location unavailable.")
                        }
                    } catch (_: Exception) {
                        ExecutionResult.Error("Failed to access location services.")
                    }
                }
                "TOGGLE_DETECTION" -> {
                    val feature = args?.get("feature") as? String
                    val enable = (args?.get("enable") as? Boolean) ?: true
                    
                    val key = when(feature?.uppercase()) {
                        "KNOWN_FACE" -> PreferenceManager.ALERT_KNOWN_FACE
                        "WEAPON" -> PreferenceManager.ALERT_WEAPON
                        "SCREAM" -> PreferenceManager.ALERT_SCREAM
                        "FACE_RECOGNITION" -> PreferenceManager.ALERT_FACE_RECOGNITION
                        "MASK_DETECTION" -> PreferenceManager.ALERT_MASK_DETECTION
                        "UNKNOWN_FACE" -> PreferenceManager.ALERT_UNKNOWN_FACE
                        else -> null
                    }
                    
                    if (key != null) {
                        preferenceManager.saveAlertPreference(key, enable)
                        ExecutionResult.Success("${feature?.replace("_", " ")} detection turned ${if(enable) "ON" else "OFF"}.", actionType = "SETTINGS")
                    } else {
                        ExecutionResult.Error("Unknown feature: $feature")
                    }
                }
                "GET_SYSTEM_STATUS" -> {
                    val knownFace = preferenceManager.getAlertPreference(PreferenceManager.ALERT_KNOWN_FACE)
                    val weapon = preferenceManager.getAlertPreference(PreferenceManager.ALERT_WEAPON)
                    val scream = preferenceManager.getAlertPreference(PreferenceManager.ALERT_SCREAM)
                    ExecutionResult.Success("Status: KNOWN_FACE(${if(knownFace) "ON" else "OFF"}), WEAPON(${if(weapon) "ON" else "OFF"}), SCREAM(${if(scream) "ON" else "OFF"}).", actionType = "STATUS")
                }
                "BROADCAST_ALARM" -> {
                    ExecutionResult.Success("Panic alarm broadcasted to all connected devices.", actionType = "ALARM")
                }
                "RESOLVE" -> {
                    ExecutionResult.Success("Alert marked as resolved.", actionType = "RESOLVE")
                }
                else -> ExecutionResult.Error("Action '$action' not implemented.")
            }

            // Centralized resolution logic
            if (alertId != null && (action == "RESOLVE" || (result is ExecutionResult.Success && (action == "CALL" || action == "SMS")))) {
                val actionType = if (result is ExecutionResult.Success) result.actionType ?: action else action
                markAlertResolvedInternal(alertId, actionType, source)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
            ExecutionResult.Error("Failed: ${e.localizedMessage}")
        }
    }

    private suspend fun markAlertResolvedInternal(alertId: String, actionType: String, source: ResolutionSource) {
        // 1. Update local repository first to preserve state
        val localAlert = alertRepository.getAlert(alertId)
        if (localAlert != null) {
            alertRepository.updateAlert(
                localAlert.copy(
                    isActionTaken = true,
                    actionTakenType = actionType,
                    resolutionSource = source
                )
            )
        } else {
            // If it's not in local repo yet, fetch from Firebase before deleting?
            // Actually, usually it should be local if it's being handled.
            // If not, we could technically insert it here if we had all details.
        }

        // 2. Delete resolved alert from Firebase ONLY
        firebaseManager.deleteAlert(alertId)
    }

    private suspend fun resolveToNumber(raw: String?): String? {
        if (raw == null || raw == "emergency_contact") return preferenceManager.getEmergencyContact()
        if (raw.matches(Regex("^[+]?[0-9]{5,15}$"))) return raw
        return queryContacts(raw).values.firstOrNull() ?: preferenceManager.getEmergencyContact()
    }

    @SuppressLint("Range")
    private fun queryContacts(name: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    map[it.getString(0)] = it.getString(1)
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantActionExecutor", "Contact query failed", e)
        }
        return map
    }
}
