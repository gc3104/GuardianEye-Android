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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.guardianeye.ui.auth.authcheck.MpinStorage
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
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
fun SwitchSetting(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
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
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    displayValue: (Float) -> String = { it.toInt().toString() }
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
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
    modifier: Modifier = Modifier,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(vertical = 8.dp)) {
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
fun SoundSelectionRow(
    title: String,
    currentSound: String,
    modifier: Modifier = Modifier,
    onPick: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
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
fun AccountSettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    if (showChangePasswordDialog && state.currentUser != null) {
        ChangePasswordDialog(onDismiss = { showChangePasswordDialog = false }, currentUser = state.currentUser!!)
    }

    Surface(modifier = modifier.fillMaxSize()) {
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
                onClick = {
                    viewModel.logout()
                    onLogout()
                }
            )
        }
    }
}

/* ------------------------- */
/* Security Settings Screen */
/* ------------------------- */

@Composable
fun SecuritySettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
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

    Surface(modifier = modifier.fillMaxSize()) {
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
fun DataSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    
    // Alert Retention
    var alertRetention by remember(state.alertRetentionDays) { mutableFloatStateOf(state.alertRetentionDays.toFloat()) }
    // Chat Retention
    var chatRetention by remember(state.chatRetentionDays) { mutableFloatStateOf(state.chatRetentionDays.toFloat()) }
    
    // Footage Lifecycle
    var compressionDelay by remember(state.compressionDelayDays) { mutableFloatStateOf(state.compressionDelayDays.toFloat()) }
    var archiveRetention by remember(state.archiveRetentionDays) { mutableFloatStateOf(state.archiveRetentionDays.toFloat()) }
    
    var archivePeriod by remember { mutableStateOf("Weekly") }
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SwitchSetting(
                title = "Save Alerts Locally",
                checked = state.saveAlertsLocally,
                onCheckedChange = { viewModel.updateAlertPreference("SAVE_ALERTS_LOCALLY", it) }
            )
            SwitchSetting(
                title = "Save Chats Locally",
                checked = state.saveChatsLocally,
                onCheckedChange = { viewModel.updateAlertPreference("SAVE_CHATS_LOCALLY", it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // History Cleanup
            Text("Data Retention Policy", style = MaterialTheme.typography.titleMedium)
            
            SliderSetting(
                title = "Keep Alerts For (Days)",
                value = alertRetention,
                valueRange = 7f..365f,
                onValueChange = { alertRetention = it },
                onValueChangeFinished = { viewModel.saveAlertRetentionDays(alertRetention.roundToInt()) },
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
                onValueChangeFinished = { viewModel.saveChatRetentionDays(chatRetention.roundToInt()) },
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
                onValueChangeFinished = { viewModel.saveCompressionDelayDays(compressionDelay.roundToInt()) },
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
                onValueChangeFinished = { viewModel.saveArchiveRetentionDays(archiveRetention.roundToInt()) },
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
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    val isDownloading by viewModel.isModelDownloading.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    val modelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModel(uri, state.aiModelFilename.ifBlank { "imported_model.task" })
        }
    }

    if (showImportDialog) {
        ImportConfigDialog(
            onDismiss = { showImportDialog = false },
            onImport = { json ->
                viewModel.importModelSettings(json)
                showImportDialog = false
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("General AI Settings", style = MaterialTheme.typography.titleMedium)
            SwitchSetting(
                title = "Enable AI Chatbot",
                checked = state.useChatbot,
                onCheckedChange = { viewModel.updateAlertPreference("USE_CHATBOT", it) }
            )
            
            SwitchSetting(
                title = "Enable Programmatic Fallback",
                checked = state.useProgrammaticFallback,
                onCheckedChange = { viewModel.updateAlertPreference("USE_PROGRAMMATIC_FALLBACK", it) }
            )

            SwitchSetting(
                title = "Use AI Constraints (Experimental)",
                checked = state.useAiConstraints,
                onCheckedChange = { viewModel.updateAlertPreference("USE_AI_CONSTRAINTS", it) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // LLM Configuration
            Text("LLM Hyperparameters", style = MaterialTheme.typography.titleMedium)
            
            var tokensValue by remember(state.aiMaxTokens) { mutableFloatStateOf(state.aiMaxTokens.toFloat()) }
            SliderSetting(
                title = "Max Tokens",
                value = tokensValue,
                valueRange = 512f..8192f,
                onValueChange = { tokensValue = it },
                onValueChangeFinished = { viewModel.updateAiMaxTokens(tokensValue.roundToInt()) },
                displayValue = { "${it.roundToInt()}" }
            )

            var topKValue by remember(state.aiTopK) { mutableFloatStateOf(state.aiTopK.toFloat()) }
            SliderSetting(
                title = "Top K",
                value = topKValue,
                valueRange = 1f..100f,
                onValueChange = { topKValue = it },
                onValueChangeFinished = { viewModel.updateAiTopK(topKValue.roundToInt()) }
            )

            var topPValue by remember(state.aiTopP) { mutableFloatStateOf(state.aiTopP) }
            SliderSetting(
                title = "Top P",
                value = topPValue,
                valueRange = 0f..1f,
                onValueChange = { topPValue = it },
                onValueChangeFinished = { viewModel.updateAiTopP(topPValue) },
                displayValue = { "%.2f".format(it) }
            )

            var tempValue by remember(state.aiTemperature) { mutableFloatStateOf(state.aiTemperature) }
            SliderSetting(
                title = "Temperature",
                value = tempValue,
                valueRange = 0f..2f,
                onValueChange = { tempValue = it },
                onValueChangeFinished = { viewModel.updateAiTemperature(tempValue) },
                displayValue = { "%.2f".format(it) }
            )

            DropdownSetting(
                title = "Inference Backend",
                options = listOf("CPU", "GPU"),
                selectedOption = state.aiBackend,
                onOptionSelected = { viewModel.updateAiBackend(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Downloaded Models", style = MaterialTheme.typography.titleMedium)
            if (state.downloadedModels.isEmpty()) {
                Text("No models downloaded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.downloadedModels.forEach { modelName ->
                    ModelRow(
                        name = modelName,
                        isActive = modelName == state.aiModelFilename,
                        onActivate = { viewModel.switchActiveModel(modelName) },
                        onDelete = { viewModel.deleteModel(modelName) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Add/Import Model", style = MaterialTheme.typography.titleMedium)
            LabeledTextField(state.aiModelFilename, "Model Filename (.task / .litertlm)", onValueChange = { viewModel.updateAiModelFilename(it) })
            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { modelFilePicker.launch(arrayOf("*/*")) }
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import .task")
                }
                
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showImportDialog = true }
                ) {
                    Text("Import JSON Config")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Remote Download", style = MaterialTheme.typography.titleMedium)
            LabeledTextField(state.aiModelUrl, "Download URL", keyboardType = KeyboardType.Uri, onValueChange = { viewModel.updateAiModelUrl(it) })
            
            Spacer(Modifier.height(16.dp))
            if (isDownloading) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Downloading... ${(progress * 100).toInt()}%", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { viewModel.saveAiModelUrl(); viewModel.saveModelName() }) { Text("Save Config") }
                    Button(modifier = Modifier.weight(1f), onClick = { viewModel.downloadModel() }) { Text("Download") }
                }
            }
        }
    }
}

@Composable
fun ImportConfigDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var jsonText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import AI Config") },
        text = {
            Column {
                Text("Paste model JSON configuration here.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("{\"model_url\": \"...\", \"model_name\": \"...\"}") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(jsonText) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ModelRow(
    name: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = { if (isActive) Text("Active", color = MaterialTheme.colorScheme.primary) },
        leadingContent = { 
            RadioButton(selected = isActive, onClick = onActivate)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
        modifier = modifier.clickable { onActivate() }
    )
}

/* ------------------------- */
/* General Settings Screen */
/* ------------------------- */

@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
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
                value = state.serverUrl,
                label = "Server URL (http/https)",
                keyboardType = KeyboardType.Uri,
                onValueChange = { viewModel.updateServerUrl(it) }
            )
            
            if (state.streamUrl.isNotBlank()) {
                Text(
                    text = "Extracted Stream URL: ${state.streamUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            
            ActionButton("Save Server URL", modifier = Modifier.padding(top = 8.dp), onClick = { viewModel.saveServerUrl() })
        }
    }
}

/* ------------------------- */
/* Notification Settings Screen */
/* ------------------------- */

@Composable
fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
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

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Alert Toggles", style = MaterialTheme.typography.titleMedium)
            
            SwitchSetting("Known Face Alerts", state.knownFaceAlert) { viewModel.updateAlertPreference("ALERT_KNOWN_FACE", it) }
            SwitchSetting("Face Recognition", state.faceRecognitionAlert) { viewModel.updateAlertPreference("ALERT_FACE_RECOGNITION", it) }
            SwitchSetting("Mask Detection", state.maskDetectionAlert) { viewModel.updateAlertPreference("ALERT_MASK_DETECTION", it) }
            SwitchSetting("Unknown Face", state.unknownFaceAlert) { viewModel.updateAlertPreference("ALERT_UNKNOWN_FACE", it) }
            SwitchSetting("Weapon Detection", state.weaponAlert) { viewModel.updateAlertPreference("ALERT_WEAPON", it) }
            SwitchSetting("Scream Detection", state.screamAlert) { viewModel.updateAlertPreference("ALERT_SCREAM", it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text("Notification Sounds", style = MaterialTheme.typography.titleMedium)
            SoundSelectionRow("Critical Alerts (Weapon)", state.criticalSoundName) { activePriority = "CRITICAL"; soundPicker.launch("audio/*") }
            SoundSelectionRow("High Priority (Recognized)", state.highSoundName) { activePriority = "HIGH"; soundPicker.launch("audio/*") }
            SoundSelectionRow("Medium Priority (Unknown Face)", state.mediumSoundName) { activePriority = "MEDIUM"; soundPicker.launch("audio/*") }
            SoundSelectionRow("Low Priority (Others)", state.lowSoundName) { activePriority = "LOW"; soundPicker.launch("audio/*") }
        }
    }
}

/* ------------------------- */
/* Panic Settings Screen */
/* ------------------------- */

@Composable
fun PanicSettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    SettingsToastObserver(viewModel)
    val state by viewModel.settingsState.collectAsState()
    
    // Convert string to int for slider, with safety fallback
    var sliderValue by remember(state.panicTimerStr) { 
        mutableFloatStateOf((state.panicTimerStr.toIntOrNull() ?: 5).toFloat()) 
    }

    Surface(modifier = modifier.fillMaxSize()) {
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
