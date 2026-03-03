package com.example.guardianeye.utils

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun sendSms(context: Context, phoneNumber: String, message: String) {
    try {
        val smsManager = ContextCompat.getSystemService(context, SmsManager::class.java)
        smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
        Toast.makeText(context, "SMS sent successfully.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to send SMS.", Toast.LENGTH_SHORT).show()
    }
}

fun makeCall(context: Context, phoneNumber: String) {
    try {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = "tel:$phoneNumber".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
    } catch (e: SecurityException) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to place call. Permission denied.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to place call.", Toast.LENGTH_SHORT).show()
    }
}

suspend fun getEmergencyContactOrShowToast(
    context: Context,
    preferenceManager: PreferenceManager
): String? {
    val contact = preferenceManager.getEmergencyContact()
    if (contact.isNullOrEmpty()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "No emergency contact set!", Toast.LENGTH_SHORT).show()
        }
        return null
    }
    return contact
}
