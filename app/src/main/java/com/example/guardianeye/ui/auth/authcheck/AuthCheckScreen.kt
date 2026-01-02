package com.example.guardianeye.ui.auth.authcheck

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AuthCheckScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthSuccess: () -> Unit,
    onNotLoggedIn: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // 1. Start the authentication check as soon as the screen is displayed.
    LaunchedEffect(Unit) {
        viewModel.checkAuthStatus()
    }

    // 2. React to auth state changes for navigation.
    LaunchedEffect(authState) {
        when (authState) {
            AuthState.AUTHENTICATED -> onAuthSuccess()
            AuthState.NOT_LOGGED_IN -> onNotLoggedIn()
            else -> { /* UI will handle other states */ }
        }
    }

    // 3. Set up the biometric prompt.
    val biometricPrompt = remember {
        if (activity == null) null
        else {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // This is crucial. It handles user cancellation (e.g., pressing "Use MPIN").
                    // We don't need to do anything here except let the prompt dismiss,
                    // revealing the MPIN entry screen that is already composed.
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
            BiometricPrompt(activity, executor, callback)
        }
    }

    fun triggerBiometric() {
        val biometricManager = BiometricManager.from(context)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("GuardianEye App Lock")
                .setSubtitle("Unlock to access your security system")
                .setNegativeButtonText("Use MPIN")
                .build()
            biometricPrompt?.authenticate(promptInfo)
        }
    }

    // 4. Display the correct UI based on the auth state.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (authState) {
            AuthState.CHECKING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            AuthState.MPIN_REQUIRED -> {
                // Attempt biometric auth automatically when MPIN is required.
                LaunchedEffect(Unit) { triggerBiometric() }
                MpinEntryScreen(
                    onMpinEntered = { mpin ->
                        if (!viewModel.verifyMpin(mpin)) {
                            Toast.makeText(context, "Incorrect MPIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBiometricRequest = { triggerBiometric() }
                )
            }

            AuthState.SET_MPIN_REQUIRED -> {
                SetMpinScreen(
                    onMpinSet = { mpin ->
                        viewModel.setMpin(mpin)
                    }
                )
            }

            // AUTHENTICATED and NOT_LOGGED_IN are handled by the LaunchedEffect, no UI needed.
            else -> {}
        }
    }
}

@Composable
private fun MpinEntryScreen(
    onMpinEntered: (String) -> Unit,
    onBiometricRequest: () -> Unit
) {
    var mpin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint, // Or App Logo
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter MPIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Please enter your 4-digit security PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // MPIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 32.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < mpin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        // Keypad
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("bio", "0", "back")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        KeyPadButton(
                            key = key,
                            onClick = {
                                when (key) {
                                    "back" -> if (mpin.isNotEmpty()) mpin = mpin.dropLast(1)
                                    "bio" -> onBiometricRequest()
                                    else -> if (mpin.length < 4) {
                                        mpin += key
                                        if (mpin.length == 4) {
                                            onMpinEntered(mpin)
                                            mpin = "" // Clear after attempt
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SetMpinScreen(
    onMpinSet: (String) -> Unit
) {
    var mpin by remember { mutableStateOf("") }
    var confirmMpin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 for enter, 2 for confirm
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Text(
                text = if (step == 1) "Set MPIN" else "Confirm MPIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (step == 1) "Create a 4-digit security PIN" else "Re-enter your PIN to confirm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // MPIN Dots
        val currentInput = if (step == 1) mpin else confirmMpin
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 32.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < currentInput.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        // Keypad
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "back")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        KeyPadButton(
                            key = key,
                            onClick = {
                                if (key == "back") {
                                    if (step == 1) {
                                        if (mpin.isNotEmpty()) mpin = mpin.dropLast(1)
                                    } else {
                                        if (confirmMpin.isNotEmpty()) confirmMpin = confirmMpin.dropLast(1)
                                    }
                                } else if (key.isNotEmpty()) {
                                    if (step == 1) {
                                        if (mpin.length < 4) {
                                            mpin += key
                                            if (mpin.length == 4) {
                                                step = 2 // Move to confirmation step
                                            }
                                        }
                                    } else { // step == 2
                                        if (confirmMpin.length < 4) {
                                            confirmMpin += key
                                            if (confirmMpin.length == 4) {
                                                if (mpin == confirmMpin) {
                                                    onMpinSet(mpin) // Success
                                                } else {
                                                    Toast.makeText(context, "PINs do not match. Try again.", Toast.LENGTH_SHORT).show()
                                                    // Reset process
                                                    mpin = ""
                                                    confirmMpin = ""
                                                    step = 1
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


@Composable
fun KeyPadButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable(enabled = key.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "back") {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.onSurface)
        } else if (key == "bio") {
            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", tint = MaterialTheme.colorScheme.primary)
        } else if (key.isNotEmpty()) {
            Text(
                text = key,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
