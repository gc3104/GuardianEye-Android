package com.example.guardianeye.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.navigation.NavDeepLinkBuilder
import com.example.guardianeye.MainActivity
import com.example.guardianeye.R
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import com.example.guardianeye.model.AlertPriority
import java.util.Locale

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val alertType = remoteMessage.data["type"] ?: "DEFAULT"
        val id = remoteMessage.data["id"] ?: ""
        val description = remoteMessage.data["description"] ?: ""
        val videoUrl = remoteMessage.data["videoUrl"] ?: ""
        val priorityString = remoteMessage.data["priority"] ?: "MEDIUM"
        
        remoteMessage.notification?.let {
            if (shouldShowNotification(alertType)) {
                sendNotification(it.title ?: "Alert", it.body ?: "New Alert Detected", alertType, id, description, videoUrl, priorityString)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w("MyFirebaseMsgService", "User not logged in, cannot save FCM token.")
            return
        }
        
        val db = FirebaseFirestore.getInstance()
        val tokenData = hashMapOf("device_token" to token)
        
        db.collection("tokens").document(userId)
            .set(tokenData)
            .addOnSuccessListener { Log.d("MyFirebaseMsgService", "FCM Token successfully written!") }
            .addOnFailureListener { e -> Log.w("MyFirebaseMsgService", "Error writing FCM token", e) }
    }

    private fun shouldShowNotification(type: String): Boolean {
        val prefs = PreferenceManager(this)
        return runBlocking {
            when (type.uppercase()) {
                "INTRUDER" -> prefs.getAlertPreference(PreferenceManager.ALERT_INTRUDER)
                "FACE_RECOGNITION" -> prefs.getAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION)
                "MASK_DETECTION" -> prefs.getAlertPreference(PreferenceManager.ALERT_MASK_DETECTION)
                "UNKNOWN_FACE" -> prefs.getAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE)
                "WEAPON" -> prefs.getAlertPreference(PreferenceManager.ALERT_WEAPON)
                "SCREAM" -> prefs.getAlertPreference(PreferenceManager.ALERT_SCREAM)
                else -> true 
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, alertType: String, id: String, description: String, videoUrl: String, priorityString: String) {
        val pendingIntent = NavDeepLinkBuilder(this)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.mobile_navigation)
            .setDestination(R.id.navigation_chat) // NAVIGATE TO CHAT
            .setArguments(Bundle().apply {
                putString("alertId", id)
                putString("alertType", alertType)
                putString("alertDesc", description)
                putString("videoUrl", videoUrl)
            })
            .createPendingIntent()

        var soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val prefs = PreferenceManager(this)
        
        val priority = try {
            AlertPriority.valueOf(priorityString.uppercase())
        } catch (e: Exception) {
            AlertPriority.MEDIUM
        }

        runBlocking {
            prefs.getNotificationSound(priority.name)?.let {
                if (it.isNotEmpty()) {
                    try {
                        soundUri = it.toUri()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val channelId = "guardian_eye_${priority.name.lowercase(Locale.getDefault())}"
        val channelName = "${priority.name.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Priority Alerts"
        
        val importance = when (priority) {
            AlertPriority.CRITICAL, AlertPriority.HIGH -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setPriority(if (importance == NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                importance
            )
            
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
                
            channel.setSound(soundUri, audioAttributes)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}