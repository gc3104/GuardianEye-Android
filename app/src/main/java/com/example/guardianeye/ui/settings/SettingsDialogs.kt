package com.example.guardianeye.ui.settings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, currentUser: FirebaseUser) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword != confirmPassword) {
                        Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (newPassword.length < 6) {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Re-authenticate
                    val credential = EmailAuthProvider.getCredential(currentUser.email!!, currentPassword)
                    currentUser.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                        if (reauthTask.isSuccessful) {
                            currentUser.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Update failed: ${updateTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Re-authentication failed: ${reauthTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MpinManagementDialog(
    mode: MpinMode,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    mpinStorage: MpinStorage
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(if (mode == MpinMode.CHANGE || mode == MpinMode.REMOVE) 0 else 1) }
    
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var mpinInput by remember { mutableStateOf("") }
    var tempNewMpin by remember { mutableStateOf("") }

    when (mode) {
        MpinMode.SET -> {
            if (step == 1) { title = "Set MPIN"; subtitle = "Enter a 4-digit PIN" }
            else { title = "Confirm MPIN"; subtitle = "Re-enter to confirm" }
        }
        MpinMode.CHANGE -> {
            when (step) {
                0 -> { title = "Verify Old MPIN"; subtitle = "Enter current PIN" }
                1 -> { title = "New MPIN"; subtitle = "Enter new 4-digit PIN" }
                else -> { title = "Confirm New MPIN"; subtitle = "Re-enter to confirm" }
            }
        }
        MpinMode.REMOVE -> {
            title = "Remove MPIN"
            subtitle = "Enter current PIN to remove"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 24.dp)) {
                    repeat(4) { index ->
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(
                            if (index < mpinInput.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Keypad(
                    onDigitClick = { digit ->
                        if (mpinInput.length < 4) {
                            mpinInput += digit
                            if (mpinInput.length == 4) {
                                scope.launch {
                                    when (mode) {
                                        MpinMode.SET -> {
                                            if (step == 1) {
                                                tempNewMpin = mpinInput
                                                mpinInput = ""
                                                step = 2
                                            } else {
                                                if (mpinInput == tempNewMpin) {
                                                    mpinStorage.saveMpin(mpinInput)
                                                    Toast.makeText(context, "MPIN Set Successfully", Toast.LENGTH_SHORT).show()
                                                    onSuccess()
                                                } else {
                                                    Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                                                    mpinInput = ""
                                                    tempNewMpin = ""
                                                    step = 1
                                                }
                                            }
                                        }
                                        MpinMode.CHANGE -> {
                                            if (step == 0) {
                                                if (mpinStorage.verifyMpin(mpinInput)) {
                                                    mpinInput = ""
                                                    step = 1
                                                } else {
                                                    Toast.makeText(context, "Incorrect Old MPIN", Toast.LENGTH_SHORT).show()
                                                    mpinInput = ""
                                                }
                                            } else if (step == 1) {
                                                tempNewMpin = mpinInput
                                                mpinInput = ""
                                                step = 2
                                            } else {
                                                if (mpinInput == tempNewMpin) {
                                                    mpinStorage.saveMpin(mpinInput)
                                                    Toast.makeText(context, "MPIN Changed Successfully", Toast.LENGTH_SHORT).show()
                                                    onSuccess()
                                                } else {
                                                    Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                                                    mpinInput = ""
                                                    tempNewMpin = ""
                                                    step = 1
                                                }
                                            }
                                        }
                                        MpinMode.REMOVE -> {
                                            if (mpinStorage.verifyMpin(mpinInput)) {
                                                mpinStorage.deleteMpin()
                                                Toast.makeText(context, "MPIN Removed", Toast.LENGTH_SHORT).show()
                                                onSuccess()
                                            } else {
                                                Toast.makeText(context, "Incorrect MPIN", Toast.LENGTH_SHORT).show()
                                                mpinInput = ""
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onBackspaceClick = { if (mpinInput.isNotEmpty()) mpinInput = mpinInput.dropLast(1) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun Keypad(onDigitClick: (String) -> Unit, onBackspaceClick: () -> Unit) {
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
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "back") onBackspaceClick()
                                else if (key.isNotEmpty()) onDigitClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "back") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                        } else if (key.isNotEmpty()) {
                            Text(text = key, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
