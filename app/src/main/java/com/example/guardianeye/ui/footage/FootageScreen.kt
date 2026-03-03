package com.example.guardianeye.ui.footage

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import java.text.SimpleDateFormat
import java.util.*

sealed class FootageIntent {
    data class Play(val file: DocumentFile) : FootageIntent()
    data class Delete(val file: DocumentFile) : FootageIntent()
    data class Rename(val file: DocumentFile, val newName: String) : FootageIntent()
    object Refresh : FootageIntent()
}

@Composable
fun FootageScreen(viewModel: FootageViewModel) {
    val state by viewModel.footageState.collectAsState()
    val context = LocalContext.current

    FootageContent(
        unarchived = state.unarchived,
        archived = state.archived,
        onIntent = { intent ->
            when (intent) {
                is FootageIntent.Play -> {
                    val playIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(intent.file.uri, context.contentResolver.getType(intent.file.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(playIntent)
                }
                is FootageIntent.Delete -> viewModel.deleteFile(intent.file)
                is FootageIntent.Rename -> viewModel.renameFile(intent.file, intent.newName)
                FootageIntent.Refresh -> viewModel.loadFootage()
            }
        }
    )
}

@Composable
private fun FootageContent(
    unarchived: List<DocumentFile>,
    archived: List<DocumentFile>,
    onIntent: (FootageIntent) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Recent") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Archived") })
        }

        val files = if (selectedTab == 0) unarchived else archived
        
        if (files.isEmpty()) {
            EmptyFootageState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    FootageCard(file, onIntent)
                }
            }
        }
    }
}

@Composable
private fun FootageCard(file: DocumentFile, onIntent: (FootageIntent) -> Unit) {
    Surface(
        onClick = { onIntent(FootageIntent.Play(file)) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                Text("${file.length() / 1024} KB • ${formatDate(file.lastModified())}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { /* Menu Logic */ }) {
                Icon(Icons.Default.MoreVert, null)
            }
        }
    }
}

@Composable
private fun EmptyFootageState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text("No footage found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun formatDate(timestamp: Long): String = 
    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))

@Preview(showBackground = true)
@Composable
fun FootageScreenPreview() {
    GuardianEyeTheme {
        FootageContent(unarchived = emptyList(), archived = emptyList(), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyFootageStatePreview() {
    GuardianEyeTheme {
        EmptyFootageState()
    }
}
