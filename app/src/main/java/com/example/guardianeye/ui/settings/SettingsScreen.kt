package com.example.guardianeye.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianeye.ui.theme.GuardianEyeTheme

private data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAccount: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToPanic: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToFamily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.settingsState.collectAsState()

    SettingsContent(
        state = state,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToSecurity = onNavigateToSecurity,
        onNavigateToData = onNavigateToData,
        onNavigateToAi = onNavigateToAi,
        onNavigateToGeneral = onNavigateToGeneral,
        onNavigateToPanic = onNavigateToPanic,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToFamily = onNavigateToFamily,
        modifier = modifier
    )
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    onNavigateToAccount: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToPanic: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToFamily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SettingsItem(Icons.Default.AccountCircle, "Account", "Manage email and password", onNavigateToAccount),
        SettingsItem(Icons.Default.Group, "Family Settings", "Manage family members and roles", onNavigateToFamily),
        SettingsItem(Icons.Default.Security, "Security", "MPIN and security settings", onNavigateToSecurity),
        SettingsItem(Icons.Default.Notifications, "Notifications", "Alert preferences and sounds", onNavigateToNotifications),
        SettingsItem(Icons.Default.Warning, "Panic Button", "Timer and SMS settings", onNavigateToPanic),
        SettingsItem(Icons.Default.Info, "General", "Emergency contact, Stream URL, Storage", onNavigateToGeneral),
        SettingsItem(Icons.Default.DataUsage, "Data Management", "Clear history and manage footage", onNavigateToData),
        SettingsItem(Icons.Default.Psychology, "AI Model & Formatting", "Manage AI model and alert formats", onNavigateToAi)
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(items) { item ->
            SettingsMenuItem(item)
        }
    }
}

@Composable
private fun SettingsMenuItem(item: SettingsItem) {
    Surface(
        onClick = item.onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        ListItem(
            headlineContent = { Text(item.title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(item.description, style = MaterialTheme.typography.bodyMedium) },
            leadingContent = { Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    GuardianEyeTheme {
        SettingsContent(
            state = SettingsState(),
            onNavigateToAccount = {},
            onNavigateToSecurity = {},
            onNavigateToData = {},
            onNavigateToAi = {},
            onNavigateToGeneral = {},
            onNavigateToPanic = {},
            onNavigateToNotifications = {},
            onNavigateToFamily = {}
        )
    }
}
