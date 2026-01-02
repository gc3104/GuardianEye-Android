package com.example.guardianeye.ui.footage

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guardianeye.model.Footage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FootageScreen(
    viewModel: FootageViewModel = viewModel()
) {
    val footageList by viewModel.footageList.collectAsState()
    val selectedDirectoryUri by viewModel.selectedDirectoryUri.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Reload footage on screen entry
    LaunchedEffect(Unit) {
        viewModel.loadFootage()
    }

    val filteredList = remember(footageList, searchQuery, sortOption) {
        var list = footageList.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }

        list = when (sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> list.sortedBy { it.timestamp }
            SortOption.NAME_ASC -> list.sortedBy { it.name }
            SortOption.SIZE_DESC -> list.sortedByDescending { parseSize(it.size) }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search & Sort
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search footage") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Date (Newest First)") },
                        onClick = { sortOption = SortOption.DATE_DESC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest First)") },
                        onClick = { sortOption = SortOption.DATE_ASC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (A-Z)") },
                        onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest First)") },
                        onClick = { sortOption = SortOption.SIZE_DESC; showSortMenu = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedDirectoryUri == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No footage directory set.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please select a folder in Settings to manage CCTV recordings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No footage found matching criteria.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(filteredList) { footage ->
                    FootageItem(footage) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(footage.path.toUri(), "video/*")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No app found to play video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FootageItem(footage: Footage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            // Thumbnail placeholder or load if possible
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = footage.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = "Size: ${footage.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDate(footage.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDate(date: Date): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
}

private fun parseSize(sizeStr: String): Long {
    return try {
        val parts = sizeStr.split(" ")
        val value = parts[0].toLong()
        when (parts[1]) {
            "MB" -> value * 1024 * 1024
            "KB" -> value * 1024
            else -> value
        }
    } catch (e: Exception) { 0L }
}

enum class SortOption {
    DATE_DESC, DATE_ASC, NAME_ASC, SIZE_DESC
}