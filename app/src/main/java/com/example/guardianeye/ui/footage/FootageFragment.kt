package com.example.guardianeye.ui.footage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.example.guardianeye.model.Footage
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.example.guardianeye.utils.PreferenceManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

class FootageFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager
    private var selectedDirectoryUri: Uri? = null

    @OptIn(DelicateCoroutinesApi::class)
    private val openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            // Persist permission
            val contentResolver = requireContext().contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags)
            
            selectedDirectoryUri = it
            
            // Save to preferences
            kotlinx.coroutines.GlobalScope.launch {
                preferenceManager.saveFootageDirectory(it.toString())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        preferenceManager = PreferenceManager(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GuardianEyeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        FootageScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun FootageScreen() {
        var footageList by remember { mutableStateOf<List<Footage>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
        var showSortMenu by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        // Load initial directory from preferences
        LaunchedEffect(Unit) {
            val savedUriString = preferenceManager.getFootageDirectory()
            if (!savedUriString.isNullOrEmpty()) {
                try {
                    val uri = savedUriString.toUri()
                    // Check if we still have permission
                    val persistedUriPermissions = context.contentResolver.persistedUriPermissions
                    val hasPermission = persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                    
                    if (hasPermission) {
                        selectedDirectoryUri = uri
                        footageList = loadFootageFromDirectory(uri)
                    }
                } catch (e: Exception) {
                    // Handle invalid URI
                }
            }
        }

        // Reload if selectedDirectoryUri changes (from button click)
        LaunchedEffect(selectedDirectoryUri) {
            if (selectedDirectoryUri != null) {
                footageList = loadFootageFromDirectory(selectedDirectoryUri!!)
            }
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
            // Header & Directory Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CCTV Footage",
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(onClick = { openDirectoryLauncher.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Folder")
                }
            }
            
            // Current Directory Display
            if (selectedDirectoryUri != null) {
                Text(
                    text = "Source: ${getDirectoryName(selectedDirectoryUri!!)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
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
                            text = "Please select a folder to manage CCTV recordings.",
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    // Use AsyncImage if available or Icon placeholder
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

    private suspend fun loadFootageFromDirectory(uri: Uri): List<Footage> = withContext(Dispatchers.IO) {
        val footageList = mutableListOf<Footage>()
        val context = context ?: return@withContext emptyList()
        
        val docFile = DocumentFile.fromTreeUri(context, uri) ?: return@withContext emptyList()
        
        docFile.listFiles().forEach { file ->
            if (file.isFile && (file.type?.startsWith("video/") == true || file.name?.endsWith(".mp4") == true)) {
                footageList.add(
                    Footage(
                        id = file.uri.toString(),
                        name = file.name ?: "Unknown",
                        path = file.uri.toString(),
                        timestamp = Date(file.lastModified()),
                        size = formatSize(file.length()),
                        duration = ""
                    )
                )
            }
        }
        footageList
    }
    
    private fun getDirectoryName(uri: Uri): String {
        return try {
            val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
            docFile?.name ?: uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun formatDate(date: Date): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024
        val mb = kb / 1024
        return if (mb > 0) "$mb MB" else "$kb KB"
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
}