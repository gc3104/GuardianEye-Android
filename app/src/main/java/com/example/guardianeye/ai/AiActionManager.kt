package com.example.guardianeye.ai

import android.content.Context
import android.widget.Toast
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages actions triggered by the AI (LLM) or user input.
 * Maps intent strings (e.g. "CALL", "SMS") to actual Android actions.
 */
class AiActionManager(private val context: Context) {

    private val preferenceManager = PreferenceManager(context)

    suspend fun executeAction(action: String, alertType: String?, alertId: String?): String {
        return withContext(Dispatchers.Main) {
            when (action.uppercase()) {
                "CALL" -> {
                    val contact = preferenceManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) {
                        makeCall(context, contact)
                        "Calling emergency contact..."
                    } else {
                        Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                        "Failed: No emergency contact set."
                    }
                }
                "SMS" -> {
                    val contact = preferenceManager.getEmergencyContact()
                    if (!contact.isNullOrEmpty()) {
                        sendSms(context, contact, "Alert: ${alertType ?: "Security Event"} detected via GuardianEye.")
                        "Sending SMS..."
                    } else {
                        Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
                        "Failed: No emergency contact set."
                    }
                }
                "IGNORE", "RESOLVE" -> {
                    if (alertId != null) {
                        FirebaseFirestore.getInstance().collection("alerts").document(alertId)
                            .update("isActionTaken", true)
                        "Alert marked as resolved."
                    } else {
                        "No specific alert to resolve."
                    }
                }
                "OPEN_SETTINGS" -> {
                    // Example of another action
                    "Please navigate to settings manually." 
                }
                else -> {
                    "Unknown action."
                }
            }
        }
    }

    fun parseActionFromText(text: String): String {
        return when {
            text.contains("call", ignoreCase = true) -> "CALL"
            text.contains("sms", ignoreCase = true) || text.contains("text", ignoreCase = true) -> "SMS"
            text.contains("ignore", ignoreCase = true) || text.contains("dismiss", ignoreCase = true) || text.contains("resolve", ignoreCase = true) -> "RESOLVE"
            else -> "UNKNOWN"
        }
    }
}