package com.example.guardianeye.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.guardianeye.MainActivity
import com.example.guardianeye.R
import com.example.guardianeye.data.local.AppDatabase
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.utils.PreferenceManager
import java.util.Locale

class FetchAlertWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val alertId = inputData.getString("alertId") ?: return Result.failure()
        val firebaseManager = FirebaseManager.getInstance(applicationContext)

        return try {
            val alert = firebaseManager.fetchAlert(alertId)

            if (alert != null) {
                saveAlertLocally(alert)
                
                if (shouldShowNotification(alert.type.name)) {
                    sendNotification(alert)
                }
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun saveAlertLocally(alert: Alert) {
        val prefs = PreferenceManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AlertRepository(database.alertDao(), prefs)
        repository.insert(alert)
    }

    private suspend fun shouldShowNotification(type: String): Boolean {
        val prefs = PreferenceManager(applicationContext)
        return when (type.uppercase()) {
            "KNOWN_FACE" -> prefs.getAlertPreference(PreferenceManager.ALERT_KNOWN_FACE)
            "FACE_RECOGNITION" -> prefs.getAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION)
            "MASK_DETECTION" -> prefs.getAlertPreference(PreferenceManager.ALERT_MASK_DETECTION)
            "UNKNOWN_FACE" -> prefs.getAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE)
            "WEAPON" -> prefs.getAlertPreference(PreferenceManager.ALERT_WEAPON)
            "SCREAM" -> prefs.getAlertPreference(PreferenceManager.ALERT_SCREAM)
            else -> true
        }
    }

    private suspend fun sendNotification(alert: Alert) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("alertId", alert.id)
            putExtra("alertType", alert.type.name)
            putExtra("alertDesc", alert.description)
            putExtra("mediaUrl", alert.mediaUrl)
            putExtra("mediaType", alert.mediaType)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, alert.id.hashCode(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        var soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val prefs = PreferenceManager(applicationContext)

        val priority = alert.priority

        // Using preference names as strings to match sound storage logic in PreferenceManager
        val soundUriStr = when(priority) {
            AlertPriority.CRITICAL -> prefs.getNotificationSound("CRITICAL")
            AlertPriority.HIGH -> prefs.getNotificationSound("HIGH")
            AlertPriority.MEDIUM -> prefs.getNotificationSound("MEDIUM")
            AlertPriority.LOW -> prefs.getNotificationSound("LOW")
        }

        soundUriStr?.let { soundPath ->
            if (soundPath.isNotEmpty()) {
                try {
                    soundUri = soundPath.toUri()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val channelId = "guardian_eye_${priority.name.lowercase(Locale.getDefault())}"
        val channelName = "${priority.name.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} Priority Alerts"

        val importance = when (priority) {
            AlertPriority.CRITICAL, AlertPriority.HIGH -> NotificationManager.IMPORTANCE_HIGH
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }

        val title = "Alert: ${alert.type.name.replace("_", " ")}"
        val messageBody = alert.description.ifEmpty { "New Alert Detected" }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setPriority(if (importance == NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        notificationManager.notify(alert.id.hashCode(), notificationBuilder.build())
    }
}
