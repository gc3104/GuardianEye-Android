package com.example.guardianeye.data.repository

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.model.ResolutionSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirebaseManager(private val application: Application) {
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val messaging = FirebaseMessaging.getInstance()

    companion object {
        private const val TAG = "FirebaseManager"
        
        @Volatile
        private var INSTANCE: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseManager(context.applicationContext as Application)
                INSTANCE = instance
                instance
            }
        }
    }

    // --- Token Management ---

    fun updateFCMToken(token: String? = null, onComplete: ((Boolean) -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w(TAG, "User not logged in, cannot save FCM token.")
            onComplete?.invoke(false)
            return
        }

        if (token != null) {
            saveTokenToFirestore(userId, token, onComplete)
        } else {
            messaging.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val currentToken = task.result
                    if (currentToken != null) {
                        saveTokenToFirestore(userId, currentToken, onComplete)
                    } else {
                        onComplete?.invoke(false)
                    }
                } else {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private fun saveTokenToFirestore(userId: String, token: String, onComplete: ((Boolean) -> Unit)?) {
        val deviceId = getDeviceId()

        val tokenRef = db.collection("users").document(userId)
            .collection("device_tokens").document()

        val tokenData = hashMapOf(
            "device_token" to token,
            "device_id" to deviceId,
            "last_updated" to FieldValue.serverTimestamp(),
            "device_model" to Build.MODEL,
            "device_manufacturer" to Build.MANUFACTURER
        )
        
        tokenRef.set(tokenData)
            .addOnSuccessListener { 
                Log.d(TAG, "FCM Token successfully written!")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e -> 
                Log.w(TAG, "Error writing FCM token", e)
                onComplete?.invoke(false)
            }
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // --- Alert Management ---

    suspend fun fetchAlert(alertId: String): Alert? {
        val userId = auth.currentUser?.uid ?: return null
        
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("alerts").document(alertId).get().await()

            if (snapshot.exists()) {
                val data = snapshot.data ?: return null
                
                val timestampValue = data["timestamp"]
                val convertedTimestamp = try {
                    when (timestampValue) {
                        is Timestamp -> timestampValue
                        is Number -> {
                            val num = timestampValue.toLong()
                            // If greater than 200 billion, assume its milliseconds
                            if (num > 200_000_000_000L) Timestamp(Date(num)) else Timestamp(Date(num * 1000))
                        }
                        is Map<*, *> -> {
                            var seconds = (timestampValue["seconds"] as? Number)?.toLong() ?: 0L
                            val nanos = (timestampValue["nanoseconds"] as? Number)?.toInt() ?: 0
                            
                            // Handle cases where 'seconds' might actually be milliseconds
                            if (seconds > 200_000_000_000L) {
                                seconds /= 1000
                            }
                            
                            if (seconds > 253402300799L || seconds < -62135596800L) {
                                Timestamp.now()
                            } else {
                                Timestamp(seconds, nanos)
                            }
                        }
                        else -> Timestamp.now()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Timestamp parsing failed for $alertId", e)
                    Timestamp.now()
                }

                val typeStr = data["type"] as? String ?: "UNKNOWN"
                val priorityStr = data["priority"] as? String ?: "MEDIUM"
                val sourceStr = data["resolutionSource"] as? String
                
                val alertType = try { AlertType.valueOf(typeStr) } catch (e: Exception) { AlertType.UNKNOWN }
                val alertPriority = try { AlertPriority.valueOf(priorityStr) } catch (e: Exception) { AlertPriority.MEDIUM }
                val resSource = sourceStr?.let { try { ResolutionSource.valueOf(it) } catch (e: Exception) { null } }

                @Suppress("UNCHECKED_CAST")
                Alert(
                    id = alertId,
                    type = alertType,
                    priority = alertPriority,
                    timestamp = convertedTimestamp,
                    description = data["description"] as? String ?: "",
                    isActionTaken = data["isActionTaken"] as? Boolean ?: false,
                    actionTakenType = data["actionTakenType"] as? String,
                    resolutionSource = resSource,
                    mediaUrl = data["mediaUrl"] as? String,
                    mediaType = data["mediaType"] as? String,
                    isComposite = data["isComposite"] as? Boolean ?: false,
                    childAlertIds = data["childAlertIds"] as? List<String>,
                    compositeType = data["compositeType"] as? String
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching alert: $alertId", e)
            throw e
        }
    }

    suspend fun resolveAlert(alertId: String, actionType: String, source: ResolutionSource): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            db.collection("users").document(userId)
                .collection("alerts").document(alertId).update(
                    mapOf(
                        "isActionTaken" to true,
                        "actionTakenType" to actionType,
                        "resolutionSource" to source.name
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving alert: $alertId", e)
            false
        }
    }

    suspend fun deleteAlert(alertId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            db.collection("users").document(userId)
                .collection("alerts").document(alertId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting alert from Firebase: $alertId", e)
            false
        }
    }

    // --- Auth Helpers ---

    fun logout() {
        auth.signOut()
    }
}
