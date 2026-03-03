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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.guardianeye.ui.theme.GuardianEyeTheme

@Composable
fun AuthCheckScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: () -> Unit,
    onNotLoggedIn: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(Unit) {
        viewModel.checkAuthStatus()
    }

    LaunchedEffect(authState) {
        when (authState) {
            AuthState.AUTHENTICATED -> onAuthSuccess()
            AuthState.NOT_LOGGED_IN -> onNotLoggedIn()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    val biometricPrompt = remember {
        if (activity == null) null
        else {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (authState) {
            AuthState.CHECKING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AuthState.MPIN_REQUIRED -> {
                LaunchedEffect(Unit) { triggerBiometric() }
                MpinEntryScreen(
                    onMpinEntered = { mpin ->
                        viewModel.verifyMpin(mpin)
                    },
                    onBiometricRequest = { triggerBiometric() }
                )
            }
            AuthState.SET_MPIN_REQUIRED -> {
                SetMpinScreen(onMpinSet = { mpin -> viewModel.setMpin(mpin) })
            }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
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
                                            mpin = ""
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
    var step by remember { mutableIntStateOf(1) }
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
                                            if (mpin.length == 4) step = 2
                                        }
                                    } else {
                                        if (confirmMpin.length < 4) {
                                            confirmMpin += key
                                            if (confirmMpin.length == 4) {
                                                if (mpin == confirmMpin) {
                                                    onMpinSet(mpin)
                                                } else {
                                                    Toast.makeText(context, "PINs do not match. Try again.", Toast.LENGTH_SHORT).show()
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

@Preview(showBackground = true)
@Composable
fun MpinEntryScreenPreview() {
    GuardianEyeTheme {
        MpinEntryScreen(onMpinEntered = {}, onBiometricRequest = {})
    }
}

@Preview(showBackground = true)
@Composable
fun SetMpinScreenPreview() {
    GuardianEyeTheme {
        SetMpinScreen(onMpinSet = {})
    }
}
