package com.example.guardianeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.guardianeye.ui.GuardianEyeApp
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    
    // State for Compose Overlay Logic handled in App
    private var panicTimerSeconds = 5
    private var panicMessage = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
             Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
             Toast.makeText(this, "Permission denied.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferenceManager = PreferenceManager(this)

        setContent {
            GuardianEyeTheme {
                var showPanicOverlay by remember { mutableStateOf(false) }
                
                GuardianEyeApp(
                    onPanicTrigger = {
                        lifecycleScope.launch {
                            panicTimerSeconds = preferenceManager.getPanicTimer()
                            panicMessage = preferenceManager.getPanicMessage()
                            showPanicOverlay = true
                        }
                    },
                    showPanicOverlay = showPanicOverlay,
                    onPanicCancel = { showPanicOverlay = false },
                    panicTimerSeconds = panicTimerSeconds,
                    onPanicTimerFinished = {
                        executePanicAction()
                        showPanicOverlay = false
                    },
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                    }
                )
            }
        }
        
        askNotificationPermission()
    }

    private fun executePanicAction() {
        lifecycleScope.launch {
            val contact = preferenceManager.getEmergencyContact()
            if (!contact.isNullOrEmpty()) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                     makeCall(this@MainActivity, contact)
                } else {
                     requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
                
                if (panicMessage.isNotEmpty()) {
                     if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                          sendSms(this@MainActivity, contact, panicMessage)
                     } else {
                         requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                     }
                }
            } else {
                Toast.makeText(this@MainActivity, "No emergency contact set!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
