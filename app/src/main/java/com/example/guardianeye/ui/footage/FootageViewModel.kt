package com.example.guardianeye.ui.footage

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.model.Footage
import com.example.guardianeye.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class FootageViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)
    
    private val _footageList = MutableStateFlow<List<Footage>>(emptyList())
    val footageList: StateFlow<List<Footage>> = _footageList.asStateFlow()
    
    private val _selectedDirectoryUri = MutableStateFlow<Uri?>(null)
    val selectedDirectoryUri: StateFlow<Uri?> = _selectedDirectoryUri.asStateFlow()

    init {
        loadFootage()
    }

    fun loadFootage() {
        viewModelScope.launch {
            val savedUriString = preferenceManager.getFootageDirectory()
            if (!savedUriString.isNullOrEmpty()) {
                try {
                    val uri = savedUriString.toUri()
                    val context = getApplication<Application>()
                    val persistedUriPermissions = context.contentResolver.persistedUriPermissions
                    val hasPermission = persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

                    if (hasPermission) {
                        _selectedDirectoryUri.value = uri
                        val loadedFootage = withContext(Dispatchers.IO) {
                            val list = mutableListOf<Footage>()
                            val docFile = DocumentFile.fromTreeUri(context, uri)
                            docFile?.listFiles()?.forEach { file ->
                                if (file.isFile && (file.type?.startsWith("video/") == true || file.name?.endsWith(".mp4") == true)) {
                                    list.add(
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
                            list
                        }
                        _footageList.value = loadedFootage
                    } else {
                        _selectedDirectoryUri.value = null
                    }
                } catch (e: Exception) {
                    _selectedDirectoryUri.value = null
                }
            } else {
                _selectedDirectoryUri.value = null
            }
        }
    }
    
    private fun formatSize(size: Long): String {
        val kb = size / 1024
        val mb = kb / 1024
        return if (mb > 0) "$mb MB" else "$kb KB"
    }
}