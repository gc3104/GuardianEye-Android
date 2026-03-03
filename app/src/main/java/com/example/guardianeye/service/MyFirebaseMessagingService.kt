package com.example.guardianeye.service

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.worker.FetchAlertWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            // The message contains status and alertId. We need alertId to fetch the details.
            val alertId = remoteMessage.data["alertId"] ?: remoteMessage.data["id"]
            
            if (!alertId.isNullOrEmpty()) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    scheduleAlertFetch(alertId, userId)
                } else {
                    Log.w(TAG, "User not logged in, cannot fetch alert details.")
                }
            } else {
                Log.w(TAG, "Message received without alertId.")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")
        // Use the centralized helper to update the token
        FirebaseManager.getInstance(this).updateFCMToken(token)
    }

    private fun scheduleAlertFetch(alertId: String, userId: String) {
        val data = workDataOf(
            "alertId" to alertId,
            "userId" to userId
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val fetchRequest = OneTimeWorkRequestBuilder<FetchAlertWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "fetch_alert_$alertId",
            ExistingWorkPolicy.REPLACE, 
            fetchRequest
        )
    }
}
