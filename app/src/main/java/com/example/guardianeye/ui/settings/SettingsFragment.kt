package com.example.guardianeye.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.guardianeye.R
import com.example.guardianeye.ai.AiModelManager
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var aiModelManager: AiModelManager
    
    // State to track which priority sound is being picked
    private var currentPickingPriority: String? = null
    
    // State for notification sound names
    private val criticalSoundName = mutableStateOf("Default")
    private val highSoundName = mutableStateOf("Default")
    private val mediumSoundName = mutableStateOf("Default")
    private val lowSoundName = mutableStateOf("Default")
    
    // State for Footage Directory
    private val footageDirectoryUri = mutableStateOf<Uri?>(null)

    private val pickRingtone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                lifecycleScope.launch {
                    preferenceManager.saveNotificationSound(priority, uri.toString(), name)
                    
                    when (priority) {
                        "CRITICAL" -> criticalSoundName.value = name
                        "HIGH" -> highSoundName.value = name
                        "MEDIUM" -> mediumSoundName.value = name
                        "LOW" -> lowSoundName.value = name
                    }
                    
                    Toast.makeText(context, "$priority Sound Saved", Toast.LENGTH_SHORT).show()
                }
            }
            currentPickingPriority = null
        }
    }
    
    private val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context?.contentResolver?.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            lifecycleScope.launch {
                preferenceManager.saveFootageDirectory(uri.toString())
                footageDirectoryUri.value = uri
                Toast.makeText(context, "Footage Directory Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        preferenceManager = PreferenceManager(context)
        aiModelManager = AiModelManager(context)
        
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GuardianEyeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsScreen() {
        val scrollState = rememberScrollState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // State
        var emergencyContact by remember { mutableStateOf("") }
        var streamUrl by remember { mutableStateOf("") }
        var aiModelUrl by remember { mutableStateOf("") }
        var aiModelFilename by remember { mutableStateOf("") }
        
        var intruderAlert by remember { mutableStateOf(true) }
        var faceRecognitionAlert by remember { mutableStateOf(true) }
        var maskDetectionAlert by remember { mutableStateOf(true) }
        var unknownFaceAlert by remember { mutableStateOf(true) }
        var weaponAlert by remember { mutableStateOf(true) }
        var screamAlert by remember { mutableStateOf(true) }

        val showChangePasswordDialog = remember { mutableStateOf(false) }

        // AI Model Download States
        var isModelDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var isModelDownloaded by remember { mutableStateOf(false) }

        // Load Settings & Check Model
        LaunchedEffect(Unit) {
            emergencyContact = preferenceManager.getEmergencyContact() ?: ""
            streamUrl = preferenceManager.getStreamUrl() ?: ""
            aiModelUrl = preferenceManager.getAiModelUrl() ?: ""
            aiModelFilename = aiModelManager.getModelName()
            val savedUriString = preferenceManager.getFootageDirectory()
            if (!savedUriString.isNullOrEmpty()) {
                footageDirectoryUri.value = savedUriString.toUri()
            }
            
            criticalSoundName.value = preferenceManager.getNotificationSoundName("CRITICAL") ?: "Default"
            highSoundName.value = preferenceManager.getNotificationSoundName("HIGH") ?: "Default"
            mediumSoundName.value = preferenceManager.getNotificationSoundName("MEDIUM") ?: "Default"
            lowSoundName.value = preferenceManager.getNotificationSoundName("LOW") ?: "Default"
            
            intruderAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_INTRUDER)
            faceRecognitionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION)
            maskDetectionAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_MASK_DETECTION)
            unknownFaceAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE)
            weaponAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_WEAPON)
            screamAlert = preferenceManager.getAlertPreference(PreferenceManager.ALERT_SCREAM)

            isModelDownloaded = aiModelManager.isModelDownloaded()
        }

        fun downloadModel() {
            if (aiModelUrl.isBlank()) {
                Toast.makeText(context, "Please enter a model URL first", Toast.LENGTH_SHORT).show()
                return
            }

            scope.launch {
                isModelDownloading = true
                downloadProgress = 0f
                
                aiModelManager.downloadModel(
                    url = aiModelUrl,
                    onProgress = { progress -> downloadProgress = progress },
                    onComplete = { success, error ->
                        isModelDownloading = false
                        if (success) {
                            isModelDownloaded = true
                            Toast.makeText(context, "AI Model Downloaded", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Download Failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        fun deleteModel() {
            scope.launch {
                val success = aiModelManager.deleteModel()
                if (success) {
                    isModelDownloaded = false
                    Toast.makeText(context, "AI Model Deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete model", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        fun saveModelName(name: String) {
            scope.launch {
                aiModelManager.setModelName(name)
                aiModelFilename = name
                // Re-check status for new filename
                isModelDownloaded = aiModelManager.isModelDownloaded()
                Toast.makeText(context, "Model Filename Saved", Toast.LENGTH_SHORT).show()
            }
        }

        if (showChangePasswordDialog.value && currentUser != null) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog.value = false },
                currentUser = currentUser
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            
            // Account Section
            SettingsSection(title = stringResource(R.string.section_account)) {
                Text(
                    text = "Email: ${currentUser?.email ?: "Not logged in"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (currentUser != null) {
                    Button(
                        onClick = { showChangePasswordDialog.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Password")
                    }
                }

                Button(
                    onClick = {
                        auth.signOut()
                        findNavController().navigate(R.id.action_global_loginFragment)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.logout_button_text))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Model Management
            SettingsSection(title = "AI Chat Model") {
                if (isModelDownloaded) {
                    Text("Model Status: Downloaded (Ready)", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { deleteModel() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete AI Model")
                    }
                } else {
                    Text("Model Status: Not Downloaded", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Model Filename Input
                    OutlinedTextField(
                        value = aiModelFilename,
                        onValueChange = { aiModelFilename = it },
                        label = { Text("Target Filename") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { saveModelName(aiModelFilename) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text("Set Filename")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // URL Input
                    OutlinedTextField(
                        value = aiModelUrl,
                        onValueChange = { aiModelUrl = it },
                        label = { Text("Model Download URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                preferenceManager.saveAiModelUrl(aiModelUrl)
                                Toast.makeText(context, "URL Saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Save URL")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isModelDownloading) {
                        Column {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Downloading...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Button(
                            onClick = { downloadModel() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = aiModelUrl.isNotBlank() && aiModelFilename.isNotBlank()
                        ) {
                            Text("Download AI Model")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // General Settings
            SettingsSection(title = stringResource(R.string.section_general)) {
                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text(stringResource(R.string.emergency_contact_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            preferenceManager.saveEmergencyContact(emergencyContact)
                            Toast.makeText(context, "Contact Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.save_contact_button))
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text(stringResource(R.string.stream_url_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            preferenceManager.saveStreamUrl(streamUrl)
                            Toast.makeText(context, "Stream URL Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.save_stream_button))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footage Directory Selection
                Text(
                    text = "CCTV Footage Directory",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val currentDirectory = footageDirectoryUri.value?.path ?: "Not selected"
                Text(
                    text = "Current: $currentDirectory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Button(
                    onClick = { pickDirectory.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Footage Directory")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notification Settings
            SettingsSection(title = stringResource(R.string.section_notifications)) {
                SwitchSetting(
                    title = stringResource(R.string.intruder_alert_title),
                    checked = intruderAlert,
                    onCheckedChange = { checked ->
                        intruderAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_INTRUDER, checked) }
                    }
                )

                SwitchSetting(
                    title = "Face Recognition Alert",
                    checked = faceRecognitionAlert,
                    onCheckedChange = { checked ->
                        faceRecognitionAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_FACE_RECOGNITION, checked) }
                    }
                )

                SwitchSetting(
                    title = "Mask Detection Alert",
                    checked = maskDetectionAlert,
                    onCheckedChange = { checked ->
                        maskDetectionAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_MASK_DETECTION, checked) }
                    }
                )
                
                SwitchSetting(
                    title = "Unknown Face Alert",
                    checked = unknownFaceAlert,
                    onCheckedChange = { checked ->
                        unknownFaceAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_UNKNOWN_FACE, checked) }
                    }
                )

                SwitchSetting(
                    title = "Weapon Detected Alert",
                    checked = weaponAlert,
                    onCheckedChange = { checked ->
                        weaponAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_WEAPON, checked) }
                    }
                )

                SwitchSetting(
                    title = "Scream Detected Alert",
                    checked = screamAlert,
                    onCheckedChange = { checked ->
                        screamAlert = checked
                        scope.launch { preferenceManager.saveAlertPreference(PreferenceManager.ALERT_SCREAM, checked) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Notification Sounds",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SoundSelectionRow("Critical Priority", criticalSoundName.value) {
                    pickSoundFor("CRITICAL")
                }
                SoundSelectionRow("High Priority", highSoundName.value) {
                    pickSoundFor("HIGH")
                }
                SoundSelectionRow("Medium Priority", mediumSoundName.value) {
                    pickSoundFor("MEDIUM")
                }
                SoundSelectionRow("Low Priority", lowSoundName.value) {
                    pickSoundFor("LOW")
                }
            }
        }
    }
    
    private fun pickSoundFor(priority: String) {
        currentPickingPriority = priority
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select $priority Sound")
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, null as Uri?)
        pickRingtone.launch(intent)
    }

    @Composable
    fun SoundSelectionRow(title: String, currentSound: String, onPick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                Text(text = currentSound, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onPick) {
                Text("Change")
            }
        }
    }

    @Composable
    fun ChangePasswordDialog(
        onDismiss: () -> Unit,
        currentUser: FirebaseUser
    ) {
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
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                            errorMessage = "All fields are required"
                            return@Button
                        }
                        if (currentPassword == newPassword) {
                            errorMessage = "New password cannot be same as current password"
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            errorMessage = "New passwords do not match"
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = null
                        
                        val email = currentUser.email
                        if (email == null) {
                            errorMessage = "User email not found"
                            isLoading = false
                            return@Button
                        }

                        val credential = EmailAuthProvider.getCredential(email, currentPassword)
                        currentUser.reauthenticate(credential)
                            .addOnCompleteListener { authTask ->
                                if (authTask.isSuccessful) {
                                    currentUser.updatePassword(newPassword)
                                        .addOnCompleteListener { updateTask ->
                                            isLoading = false
                                            if (updateTask.isSuccessful) {
                                                Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } else {
                                                errorMessage = "Update failed: ${updateTask.exception?.message}"
                                            }
                                        }
                                } else {
                                    isLoading = false
                                    errorMessage = "Re-authentication failed: ${authTask.exception?.message}"
                                }

                            }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), 
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Change")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Composable
    fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                content()
            }
        }
    }

    @Composable
    fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}