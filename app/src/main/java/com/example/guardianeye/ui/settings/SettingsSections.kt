package com.example.guardianeye.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
import com.example.guardianeye.utils.PreferenceManager
import kotlin.math.roundToInt

// Enum for MPIN dialog mode
enum class MpinMode {
    SET, CHANGE, REMOVE
}

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
    modifier: Modifier = Modifier.fillMaxWidth(),
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
fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    displayValue: (Float) -> String = { it.toInt().toString() }
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(displayValue(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
fun DropdownSetting(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable { expanded = true }
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
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
            onSuccess = { 
                showMpinDialog = false
                viewModel.refreshMpinState() 
            },
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

                var sliderValue by remember(state.mpinTimeoutStr) {
                    mutableFloatStateOf((state.mpinTimeoutStr.toFloatOrNull() ?: 60f))
                }

                SliderSetting(
                    title = "MPIN Timeout (Seconds)",
                    value = sliderValue,
                    valueRange = 10f..300f,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.updateMpinTimeoutStr(sliderValue.roundToInt().toString())
                        viewModel.saveMpinTimeout()
                    },
                    displayValue = { "${it.roundToInt()}s" }
                )

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
    val state by viewModel.settingsState.collectAsState()
    var alertRetention by remember { mutableFloatStateOf(90f) }
    var chatRetention by remember { mutableFloatStateOf(365f) }
    
    // Footage Lifecycle
    var compressionDelay by remember { mutableFloatStateOf(7f) }
    var archivePeriod by remember { mutableStateOf("Weekly") }
    var archiveRetention by remember { mutableFloatStateOf(180f) }
    val periodOptions = listOf("Daily", "Weekly", "Monthly")
    
    val context = LocalContext.current

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            viewModel.saveFootageDirectory(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SwitchSetting(
                title = "Save Alerts Locally",
                checked = state.saveAlertsLocally,
                onCheckedChange = { viewModel.updateAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY, it) }
            )
            SwitchSetting(
                title = "Save Chats Locally",
                checked = state.saveChatsLocally,
                onCheckedChange = { viewModel.updateAlertPreference(PreferenceManager.SAVE_CHATS_LOCALLY, it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // History Cleanup
            Text("Data Retention Policy", style = MaterialTheme.typography.titleMedium)
            
            SliderSetting(
                title = "Keep Alerts For (Days)",
                value = alertRetention,
                valueRange = 7f..365f,
                onValueChange = { alertRetention = it },
                onValueChangeFinished = {},
                displayValue = { "${it.roundToInt()} days" }
            )
            Row {
                OutlinedButton(onClick = { viewModel.deleteOldAlerts(alertRetention.roundToInt()) }) { Text("Apply Policy Now") }
                Spacer(modifier = Modifier.weight(1f))
                Button(colors=ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { viewModel.clearAlertHistory() }) { Text("Delete All") }
            }
            
            Spacer(Modifier.height(16.dp))
            
            SliderSetting(
                title = "Keep Chats For (Days)",
                value = chatRetention,
                valueRange = 30f..730f,
                onValueChange = { chatRetention = it },
                onValueChangeFinished = {},
                displayValue = { "${it.roundToInt()} days" }
            )
            Row {
                OutlinedButton(onClick = { viewModel.deleteOldChats(chatRetention.roundToInt()) }) { Text("Apply Policy Now") }
                Spacer(modifier = Modifier.weight(1f))
                Button(colors=ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { viewModel.clearChatHistory() }) { Text("Delete All") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Footage Management
            Text("Footage Lifecycle Policy", style = MaterialTheme.typography.titleMedium)
            
            // Directory Selection
            ActionButton(
                text = if (state.footageDirectoryUri != null) "Change Footage Folder" else "Select Footage Folder",
                modifier = Modifier.padding(vertical = 8.dp),
                onClick = { directoryPicker.launch(null) }
            )
            if (state.footageDirectoryUri != null) {
                Text(
                    text = "Current: ${state.footageDirectoryUri?.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(8.dp))

            SliderSetting(
                title = "Compress Footage After (Days)",
                value = compressionDelay,
                valueRange = 1f..30f,
                onValueChange = { compressionDelay = it },
                onValueChangeFinished = {},
                displayValue = { "${it.roundToInt()} days" }
            )
            
            DropdownSetting(
                title = "Archive Period",
                options = periodOptions,
                selectedOption = archivePeriod,
                onOptionSelected = { archivePeriod = it }
            )
            
             SliderSetting(
                title = "Delete Archives After (Days)",
                value = archiveRetention,
                valueRange = 30f..730f,
                onValueChange = { archiveRetention = it },
                onValueChangeFinished = {},
                displayValue = { "${it.roundToInt()} days" }
            )
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { viewModel.runSmartArchival(compressionDelay.roundToInt(), archivePeriod, archiveRetention.roundToInt()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Run Lifecycle Management Now") }
            
            Spacer(Modifier.height(8.dp))
            
             Button(
                onClick = { viewModel.deleteAllFootage() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete All Footage") }

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
            SwitchSetting(
                title = "Enable AI Chatbot",
                checked = state.useChatbot,
                onCheckedChange = { viewModel.updateAlertPreference(PreferenceManager.USE_CHATBOT, it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

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
                ActionButton("Save URL", modifier = Modifier.padding(top = 4.dp), onClick = { viewModel.saveAiModelUrl() })
                
                Spacer(Modifier.height(16.dp))
                if (isDownloading) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading... ${(progress * 100).toInt()}%", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    ActionButton("Download Model") { viewModel.downloadModel() }
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LabeledTextField(
                value = state.emergencyContact,
                label = "Emergency Contact",
                keyboardType = KeyboardType.Phone,
                onValueChange = { viewModel.updateEmergencyContact(it) }
            )
            ActionButton("Save Contact", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveEmergencyContact() })

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            LabeledTextField(
                value = state.streamUrl,
                label = "Camera Stream URL (ws://...)",
                keyboardType = KeyboardType.Uri,
                onValueChange = { viewModel.updateStreamUrl(it) }
            )
            ActionButton("Save Stream URL", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveStreamUrl() })
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
    var activePriority by remember { mutableStateOf<String?>(null) }

    val soundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && activePriority != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            // Get simple name from URI
            val name = android.provider.OpenableColumns.DISPLAY_NAME.let { col ->
                 context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                     if (cursor.moveToFirst()) {
                         val index = cursor.getColumnIndex(col)
                         if (index != -1) cursor.getString(index) else "Custom Sound"
                     } else "Custom Sound"
                 } ?: "Custom Sound"
            }
            
            viewModel.saveNotificationSound(activePriority!!, uri, name)
            activePriority = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Alert Toggles", style = MaterialTheme.typography.titleMedium)
            
            SwitchSetting("Intruder Alerts", state.intruderAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_INTRUDER, it) }
            SwitchSetting("Face Recognition", state.faceRecognitionAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION, it) }
            SwitchSetting("Mask Detection", state.maskDetectionAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_MASK_DETECTION, it) }
            SwitchSetting("Unknown Face", state.unknownFaceAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE, it) }
            SwitchSetting("Weapon Detection", state.weaponAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_WEAPON, it) }
            SwitchSetting("Scream Detection", state.screamAlert) { viewModel.updateAlertPreference(PreferenceManager.ALERT_SCREAM, it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text("Notification Sounds", style = MaterialTheme.typography.titleMedium)
            SoundSelectionRow("Critical Alerts (Weapon)", state.criticalSoundName) { activePriority = "CRITICAL"; soundPicker.launch("audio/*") }
            SoundSelectionRow("High Priority (Intruder)", state.highSoundName) { activePriority = "HIGH"; soundPicker.launch("audio/*") }
            SoundSelectionRow("Medium Priority (Unknown Face)", state.mediumSoundName) { activePriority = "MEDIUM"; soundPicker.launch("audio/*") }
            SoundSelectionRow("Low Priority (Others)", state.lowSoundName) { activePriority = "LOW"; soundPicker.launch("audio/*") }
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
    
    // Convert string to int for slider, with safety fallback
    var sliderValue by remember(state.panicTimerStr) { 
        mutableFloatStateOf((state.panicTimerStr.toIntOrNull() ?: 5).toFloat()) 
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Panic Button Configuration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
            
            SliderSetting(
                title = "Countdown Timer (Seconds)",
                value = sliderValue,
                valueRange = 0f..30f,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { viewModel.updatePanicTimerStr(sliderValue.roundToInt().toString()) },
                displayValue = { "${it.roundToInt()}s" }
            )

            Spacer(Modifier.height(16.dp))
            
            LabeledTextField(
                value = state.panicMessage,
                label = "Emergency Message",
                onValueChange = { viewModel.updatePanicMessage(it) }
            )
            
            Spacer(Modifier.height(24.dp))
            
            ActionButton("Save Panic Settings") { viewModel.savePanicSettings() }
        }
    }
}
