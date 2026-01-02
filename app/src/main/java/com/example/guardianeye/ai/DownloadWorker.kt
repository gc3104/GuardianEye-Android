package com.example.guardianeye.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.guardianeye.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create Notification Channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading AI Model")
            .setContentText("Please wait...")
            .setSmallIcon(R.drawable.ic_home_black_24dp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, false)

        val notificationId = 1
        
        if (Build.VERSION.SDK_INT >= 34) {
            setForeground(
                ForegroundInfo(
                    notificationId, 
                    notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )
        } else {
             setForeground(ForegroundInfo(notificationId, notificationBuilder.build()))
        }

        val finalFile = File(applicationContext.filesDir, filename)
        val tempFile = File(applicationContext.filesDir, "$filename.tmp")
        
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: ${response.code} ${response.message}")

                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength <= 0) {
                     throw IOException("Invalid content length - download cannot proceed.")
                }
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    if (totalBytesRead % (1024 * 512) == 0L || totalBytesRead == contentLength) { 
                         notificationManager.notify(
                            notificationId, 
                            notificationBuilder.setProgress(100, progress, false)
                                .setContentText("Downloading: $progress%")
                                .build()
                        )
                        setProgress(workDataOf("progress" to progress))
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (totalBytesRead != contentLength) {
                    throw IOException("Download incomplete: expected $contentLength bytes but got $totalBytesRead")
                }
            }
            
            if (finalFile.exists()) {
                finalFile.delete()
            }
            
            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Failed to rename temp file to ${finalFile.name}")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error during download", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure()
        } finally {
             notificationManager.cancel(notificationId)
        }
    }

    companion object {
        const val KEY_URL = "key_url"
        const val KEY_FILENAME = "key_filename"
        const val CHANNEL_ID = "download_channel"
    }
}
