package com.example.guardianeye.ui.footage

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FootageScreen(viewModel: FootageViewModel = viewModel()) {
    val footageState by viewModel.footageState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFootage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()

        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(text = { Text("Unarchived") },
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } })
            Tab(text = { Text("Archived") },
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } })
        }

        HorizontalPager(state = pagerState) {
            when (it) {
                0 -> FootageList(files = footageState.unarchived, viewModel)
                1 -> FootageList(files = footageState.archived, viewModel)
            }
        }
    }
}

@Composable
private fun FootageList(files: List<DocumentFile>, viewModel: FootageViewModel) {
    val context = LocalContext.current
    var fileToRename by remember { mutableStateOf<DocumentFile?>(null) }

    // Search and Sort states
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredFiles = remember(files, searchQuery, sortOption) {
        var list = files.filter { 
            it.name?.contains(searchQuery, ignoreCase = true) == true 
        }
        
        list = when (sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.lastModified() }
            SortOption.DATE_ASC -> list.sortedBy { it.lastModified() }
            SortOption.NAME_ASC -> list.sortedBy { it.name ?: "" }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.length() }
        }
        list
    }

    if (fileToRename != null) {
        RenameFileDialog(
            file = fileToRename!!,
            onDismiss = { fileToRename = null },
            onRename = { newName ->
                viewModel.renameFile(fileToRename!!, newName)
                fileToRename = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Sort Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search") },
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
                        text = { Text("Date (Newest)") },
                        onClick = { sortOption = SortOption.DATE_DESC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest)") },
                        onClick = { sortOption = SortOption.DATE_ASC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (A-Z)") },
                        onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest)") },
                        onClick = { sortOption = SortOption.SIZE_DESC; showSortMenu = false }
                    )
                }
            }
        }

        if (filteredFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searchQuery.isNotEmpty()) "No matches found." else "No footage found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFiles) { file ->
                    FootageItem(
                        file = file,
                        onPlay = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(file.uri, context.contentResolver.getType(file.uri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot play file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRename = { fileToRename = file },
                        onDelete = { viewModel.deleteFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FootageItem(
    file: DocumentFile,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { onPlay() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = if (file.name?.endsWith(".zip") == true) Icons.Default.Archive else Icons.Default.PlayCircleOutline
            Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name ?: "", style = MaterialTheme.typography.bodyLarge)
                Text("Size: ${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                Text(formatDate(file.lastModified()), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        onRename()
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        onDelete()
                        showMenu = false
                    })
                }
            }
        }
    }
}

@Composable
fun RenameFileDialog(file: DocumentFile, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(file.name ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onRename(newName) }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private enum class SortOption {
    DATE_DESC, DATE_ASC, NAME_ASC, SIZE_DESC
}
