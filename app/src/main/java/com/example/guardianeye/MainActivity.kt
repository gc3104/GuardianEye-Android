package com.example.guardianeye

import android.Manifest
import android.content.Intent
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
import com.example.guardianeye.data.repository.FirebaseManager
import com.example.guardianeye.ui.GuardianEyeApp
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.example.guardianeye.utils.getEmergencyContactOrShowToast
import com.example.guardianeye.utils.makeCall
import com.example.guardianeye.utils.sendSms
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    
    // State for Compose Overlay Logic handled in App
    private var panicTimerSeconds = 5
    private var panicMessage = ""
    
    // State for tracking if we launched with a specific alert
    private var pendingAlertId: String? = null

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
             Toast.makeText(this, "Some permissions were denied. Certain features may not work.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
             Toast.makeText(this, "Permission denied.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferenceManager = PreferenceManager(this)

        // Check pending intent for notification data
        handleIntent(intent)

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
                        FirebaseManager.getInstance(this@MainActivity).logout()
                    },
                    openedAlertId = pendingAlertId
                )
            }
        }
        
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            multiplePermissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.hasExtra("alertId")) {
                pendingAlertId = it.getStringExtra("alertId")
            }
        }
    }

    private fun executePanicAction() {
        lifecycleScope.launch {
            val contact = getEmergencyContactOrShowToast(this@MainActivity, preferenceManager)
            if (contact != null) {
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
            }
        }
    }
}
