package com.example.guardianeye.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import java.util.Locale

/* ------------------------- */
/* Generic Helper Components */
/* ------------------------- */

@Composable
fun SettingsToastObserver(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val message by viewModel.toastMessage.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }
}

@Composable
fun LabeledTextField(
    value: String,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = color
    ) { Text(text) }
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SoundSelectionRow(title: String, currentSound: String, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(currentSound, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onPick) { Text("Change") }
    }
}

/* ------------------------- */
/* Account Settings Screen */
/* ------------------------- */

@Composable
fun AccountSettingsScreen(viewModel: SettingsViewModel, onLogout: () -> Unit) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    if (showChangePasswordDialog && state.currentUser != null) {
        ChangePasswordDialog(onDismiss = { showChangePasswordDialog = false }, currentUser = state.currentUser!!)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Email: ${state.currentUser?.email ?: "Not logged in"}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp))

            state.currentUser?.let {
                ActionButton("Change Password") { showChangePasswordDialog = true }
                Spacer(Modifier.height(16.dp))
            }

            ActionButton(
                text = "Logout",
                color = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = onLogout
            )
        }
    }
}

/* ------------------------- */
/* Security Settings Screen */
/* ------------------------- */

@Composable
fun SecuritySettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    val context = LocalContext.current
    var showMpinDialog by remember { mutableStateOf(false) }
    var mpinDialogMode by remember { mutableStateOf(MpinMode.SET) }

    if (showMpinDialog) {
        MpinManagementDialog(
            mode = mpinDialogMode,
            onDismiss = { showMpinDialog = false },
            onSuccess = { showMpinDialog = false; viewModel.refreshMpinState() },
            mpinStorage = MpinStorage(context)
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (state.isMpinSet) {
                ActionButton("Change MPIN") { mpinDialogMode = MpinMode.CHANGE; showMpinDialog = true }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = "Remove MPIN",
                    color = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { mpinDialogMode = MpinMode.REMOVE; showMpinDialog = true }
                )
                Spacer(Modifier.height(16.dp))

                LabeledTextField(
                    value = state.mpinTimeoutStr,
                    label = "MPIN Timeout (seconds)",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { viewModel.updateMpinTimeoutStr(it) }
                )
                ActionButton("Save Timeout", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveMpinTimeout() })
            } else {
                Text("Secure your app with a 4-digit MPIN.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                ActionButton("Set MPIN") { mpinDialogMode = MpinMode.SET; showMpinDialog = true }
            }
        }
    }
}

/* ------------------------- */
/* Data Settings Screen */
/* ------------------------- */

@Composable
fun DataSettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    var showDataManagementDialog by remember { mutableStateOf(false) }

    if (showDataManagementDialog) {
        DataManagementDialog(
            onDismiss = { showDataManagementDialog = false },
            onClearChat = { viewModel.clearChatHistory() },
            onClearAlerts = { viewModel.clearAlertHistory() },
            onDeleteFootage = { days -> viewModel.deleteOldFootage(days) },
            onCompressFootage = { period -> viewModel.compressFootage(period) }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Manage storage for chat history, alerts, and footage.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            ActionButton("Manage Data") { showDataManagementDialog = true }
        }
    }
}

/* ------------------------- */
/* AI Settings Screen */
/* ------------------------- */

@Composable
fun AiSettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    val isDownloading by viewModel.isModelDownloading.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (state.isModelDownloaded) {
                Text("Model Status: Downloaded", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = "Delete AI Model",
                    color = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { viewModel.deleteModel() }
                )
            } else {
                LabeledTextField(state.aiModelFilename, "Target Filename", onValueChange = { viewModel.updateAiModelFilename(it) })
                ActionButton("Set Filename", modifier = Modifier.padding(top = 4.dp), onClick = { viewModel.saveModelName() })
                Spacer(Modifier.height(8.dp))
                LabeledTextField(state.aiModelUrl, "Model Download URL", keyboardType = KeyboardType.Uri, onValueChange = { viewModel.updateAiModelUrl(it) })
                ActionButton("Save URL", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveAiModelUrl() })
                Spacer(Modifier.height(8.dp))

                if (isDownloading) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    Text("Downloading: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                } else {
                    ActionButton(
                        text = "Download AI Model",
                        enabled = state.aiModelUrl.isNotBlank() && state.aiModelFilename.isNotBlank(),
                        onClick = { viewModel.downloadModel() }
                    )
                }
            }
        }
    }
}

/* ------------------------- */
/* General Settings Screen */
/* ------------------------- */

@Composable
fun GeneralSettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    val context = LocalContext.current

    val pickDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.saveFootageDirectory(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LabeledTextField(state.emergencyContact, "Emergency Contact", keyboardType = KeyboardType.Phone, onValueChange = { viewModel.updateEmergencyContact(it) })
            ActionButton("Save Contact", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveEmergencyContact() })
            Spacer(Modifier.height(16.dp))

            LabeledTextField(state.streamUrl, "Stream URL", keyboardType = KeyboardType.Uri, onValueChange = { viewModel.updateStreamUrl(it) })
            ActionButton("Save Stream URL", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveStreamUrl() })
            Spacer(Modifier.height(16.dp))

            Text("CCTV Footage Directory", style = MaterialTheme.typography.bodyMedium)
            Text("Current: ${state.footageDirectoryUri?.path ?: "Not selected"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton("Select Footage Directory") { pickDirectory.launch(null) }
        }
    }
}

/* ------------------------- */
/* Panic Settings Screen */
/* ------------------------- */

@Composable
fun PanicSettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            LabeledTextField(state.panicTimerStr, "Countdown Timer (seconds)", keyboardType = KeyboardType.Number, onValueChange = { viewModel.updatePanicTimerStr(it) })
            Spacer(Modifier.height(8.dp))
            LabeledTextField(state.panicMessage, "Panic SMS Message", onValueChange = { viewModel.updatePanicMessage(it) })
            ActionButton("Save Panic Settings", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.savePanicSettings() })
        }
    }
}

/* ------------------------- */
/* Notification Settings Screen */
/* ------------------------- */

@Composable
fun NotificationSettingsScreen(viewModel: SettingsViewModel) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    val context = LocalContext.current
    var currentPickingPriority by remember { mutableStateOf<String?>(null) }

    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val priority = currentPickingPriority
            if (uri != null && priority != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val name = ringtone.getTitle(context)
                viewModel.saveNotificationSound(priority, uri, name)
            }
            currentPickingPriority = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SwitchSetting("Intruder Alert", state.intruderAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_INTRUDER, it) }
            SwitchSetting("Face Recognition Alert", state.faceRecognitionAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION, it) }
            SwitchSetting("Mask Detection Alert", state.maskDetectionAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_MASK_DETECTION, it) }
            SwitchSetting("Unknown Face Alert", state.unknownFaceAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE, it) }
            SwitchSetting("Weapon Detected Alert", state.weaponAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_WEAPON, it) }
            SwitchSetting("Scream Detected Alert", state.screamAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_SCREAM, it) }

            Spacer(Modifier.height(16.dp))
            Text("Notification Sounds", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

            listOf(
                "CRITICAL" to state.criticalSoundName,
                "HIGH" to state.highSoundName,
                "MEDIUM" to state.mediumSoundName,
                "LOW" to state.lowSoundName
            ).forEach { (priority, name) ->
                SoundSelectionRow(
                    title = priority.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    currentSound = name
                ) {
                    currentPickingPriority = priority
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select $priority Sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, null as Uri?)
                    }
                    pickRingtone.launch(intent)
                }
            }
        }
    }
}

/* ------------------------- */
/* Helper Dialogs */
/* ------------------------- */

@Composable
fun DataManagementDialog(
    onDismiss: () -> Unit,
    onClearChat: () -> Unit,
    onClearAlerts: () -> Unit,
    onDeleteFootage: (Int) -> Unit,
    onCompressFootage: (String) -> Unit
) {
    var selectedRetention by remember { mutableStateOf("30") }
    var selectedCompression by remember { mutableStateOf("Daily") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data Management") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("History Cleanup", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onClearChat, modifier = Modifier.weight(1f)) { Text("Clear Chat") }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = onClearAlerts, modifier = Modifier.weight(1f)) { Text("Clear Alerts") }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text("Footage Cleanup", style = MaterialTheme.typography.titleSmall)
                Text("Delete footage older than:", style = MaterialTheme.typography.bodySmall)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = selectedRetention,
                        onValueChange = { if (it.all { c -> c.isDigit() }) selectedRetention = it },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Days") }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(onClick = { selectedRetention.toIntOrNull()?.let { onDeleteFootage(it) } }) { Text("Delete") }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text("Archival & Compression", style = MaterialTheme.typography.titleSmall)
                Text("Compress old footage to save space.", style = MaterialTheme.typography.bodySmall)
                
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Frequency: ")
                        Button(
                            onClick = { selectedCompression = "Daily" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCompression == "Daily") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedCompression == "Daily") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Daily") }
                        Spacer(modifier = Modifier.size(4.dp))
                        Button(
                             onClick = { selectedCompression = "Weekly" },
                             colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCompression == "Weekly") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedCompression == "Weekly") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                             )
                        ) { Text("Weekly") }
                    }
                    Button(
                        onClick = { onCompressFootage(selectedCompression) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Compress & Archive Now") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun MpinManagementDialog(
    mode: MpinMode,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    mpinStorage: MpinStorage
) {
    var step by remember { mutableIntStateOf(1) }
    var pinInput by remember { mutableStateOf("") }
    var tempPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val title = when (mode) {
        MpinMode.SET -> "Set MPIN"
        MpinMode.CHANGE -> "Change MPIN"
        MpinMode.REMOVE -> "Remove MPIN"
    }

    val instruction = when (mode) {
        MpinMode.SET -> if (step == 1) "Enter new 4-digit MPIN" else "Confirm MPIN"
        MpinMode.CHANGE -> when (step) {
            1 -> "Enter current MPIN"
            2 -> "Enter new 4-digit MPIN"
            else -> "Confirm new MPIN"
        }
        MpinMode.REMOVE -> "Enter MPIN to confirm removal"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(instruction, modifier = Modifier.padding(bottom = 8.dp))
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) { pinInput = it; errorMessage = null } },
                    label = { Text("MPIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length != 4) { errorMessage = "MPIN must be 4 digits"; return@Button }
                    when (mode) {
                        MpinMode.SET -> {
                            if (step == 1) { tempPin = pinInput; pinInput = ""; step = 2 }
                            else {
                                if (pinInput == tempPin) { mpinStorage.saveMpin(pinInput); Toast.makeText(context, "MPIN Set Successfully", Toast.LENGTH_SHORT).show(); onSuccess() }
                                else { errorMessage = "MPINs do not match" }
                            }
                        }
                        MpinMode.CHANGE -> {
                            if (step == 1) {
                                if (mpinStorage.verifyMpin(pinInput)) { pinInput = ""; step = 2 } else { errorMessage = "Incorrect MPIN" }
                            } else if (step == 2) { tempPin = pinInput; pinInput = ""; step = 3 }
                            else {
                                if (pinInput == tempPin) { mpinStorage.saveMpin(pinInput); Toast.makeText(context, "MPIN Changed Successfully", Toast.LENGTH_SHORT).show(); onSuccess() }
                                else { errorMessage = "MPINs do not match" }
                            }
                        }
                        MpinMode.REMOVE -> {
                            if (mpinStorage.verifyMpin(pinInput)) { mpinStorage.deleteMpin(); Toast.makeText(context, "MPIN Removed", Toast.LENGTH_SHORT).show(); onSuccess() }
                            else { errorMessage = "Incorrect MPIN" }
                        }
                    }
                }
            ) { Text(if (mode == MpinMode.REMOVE) "Remove" else if (mode == MpinMode.SET && step == 2) "Confirm" else if (mode == MpinMode.CHANGE && step == 3) "Confirm" else "Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, currentUser: FirebaseUser) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(value = currentPassword, onValueChange = { currentPassword = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) { errorMessage = "All fields are required"; return@Button }
                    if (currentPassword == newPassword) { errorMessage = "New password cannot be same as current password"; return@Button }
                    if (newPassword != confirmPassword) { errorMessage = "New passwords do not match"; return@Button }
                    isLoading = true; errorMessage = null
                    val email = currentUser.email
                    if (email == null) { errorMessage = "User email not found"; isLoading = false; return@Button }
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    currentUser.reauthenticate(credential).addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            currentUser.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                isLoading = false
                                if (updateTask.isSuccessful) { Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show(); onDismiss() }
                                else { errorMessage = "Update failed: ${updateTask.exception?.message}" }
                            }
                        } else { isLoading = false; errorMessage = "Re-authentication failed: ${authTask.exception?.message}" }
                    }
                },
                enabled = !isLoading
            ) { if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

enum class MpinMode { SET, CHANGE, REMOVE }