package com.example.guardianeye.ui.alerts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.ui.components.VideoPlayer
import com.example.guardianeye.ui.theme.GuardianEyeTheme

sealed class AlertDetailIntent {
    object NavigateToChat : AlertDetailIntent()
    object ShareAlert : AlertDetailIntent()
}

@Composable
fun AlertDetailScreen(
    alert: Alert,
    onNavigateToChat: () -> Unit
) {
    AlertDetailContent(
        alert = alert,
        onIntent = { intent ->
            when (intent) {
                AlertDetailIntent.NavigateToChat -> onNavigateToChat()
                AlertDetailIntent.ShareAlert -> { /* Share Logic */ }
            }
        }
    )
}

@Composable
private fun AlertDetailContent(
    alert: Alert,
    onIntent: (AlertDetailIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        AlertHeader(alert)

        Spacer(modifier = Modifier.height(24.dp))

        if (!alert.mediaUrl.isNullOrEmpty() && alert.mediaUrl != "NONE") {
            MediaSection(alert)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        ActionButtons(onIntent)
    }
}

@Composable
private fun AlertHeader(alert: Alert) {
    Column {
        Text(
            text = alert.type.name.replace("_", " "),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Priority: ${alert.priority}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = alert.description.ifEmpty { "No detailed description available." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaSection(alert: Alert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        when (alert.mediaType) {
            "IMAGE" -> {
                Image(
                    painter = rememberAsyncImagePainter(alert.mediaUrl),
                    contentDescription = "Alert Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
            "VIDEO", "AUDIO" -> {
                VideoPlayer(
                    url = alert.mediaUrl!!, 
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f), 
                    isAudio = alert.mediaType == "AUDIO"
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(onIntent: (AlertDetailIntent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = { onIntent(AlertDetailIntent.ShareAlert) },
            modifier = Modifier.weight(1f).height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
        Button(
            onClick = { onIntent(AlertDetailIntent.NavigateToChat) },
            modifier = Modifier.weight(1f).height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null)
            Spacer(Modifier.width(8.dp))
            Text("Chat")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AlertDetailScreenPreview() {
    GuardianEyeTheme {
        AlertDetailContent(
            alert = Alert(
                type = AlertType.WEAPON,
                priority = AlertPriority.CRITICAL,
                description = "Automated detection of a firearm in the living room area."
            ),
            onIntent = {}
        )
    }
}
