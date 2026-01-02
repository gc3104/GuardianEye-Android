package com.example.guardianeye.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/* ---------------------------------- */
/* Data Model (Scalable & Clean) */
/* ---------------------------------- */

private data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

/* ---------------------------------- */
/* Screen */
/* ---------------------------------- */

@Composable
fun SettingsScreen(
    onNavigateToAccount: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToPanic: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SettingsItem(
            icon = Icons.Default.AccountCircle,
            title = "Account",
            description = "Manage email and password",
            onClick = onNavigateToAccount
        ),
        SettingsItem(
            icon = Icons.Default.Security,
            title = "Security",
            description = "MPIN and security settings",
            onClick = onNavigateToSecurity
        ),
        SettingsItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "Alert preferences and sounds",
            onClick = onNavigateToNotifications
        ),
        SettingsItem(
            icon = Icons.Default.Warning,
            title = "Panic Button",
            description = "Timer and SMS settings",
            onClick = onNavigateToPanic
        ),
        SettingsItem(
            icon = Icons.Default.Info,
            title = "General",
            description = "Emergency contact, Stream URL, Storage",
            onClick = onNavigateToGeneral
        ),
        SettingsItem(
            icon = Icons.Default.DataUsage,
            title = "Data Management",
            description = "Clear history and manage footage",
            onClick = onNavigateToData
        ),
        SettingsItem(
            icon = Icons.Default.Psychology,
            title = "AI Model",
            description = "Manage AI chat model",
            onClick = onNavigateToAi
        )
    )

    SettingsScaffold(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {



            items(items) { item ->
                SettingsMenuItem(item)
            }
        }
    }
}

/* ---------------------------------- */
/* Scaffold Wrapper */
/* ---------------------------------- */

@Composable
private fun SettingsScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}

/* ---------------------------------- */
/* Row Item */
/* ---------------------------------- */

@Composable
private fun SettingsMenuItem(item: SettingsItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .semantics { contentDescription = item.title }
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingContent = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
        HorizontalDivider()
    }
}
