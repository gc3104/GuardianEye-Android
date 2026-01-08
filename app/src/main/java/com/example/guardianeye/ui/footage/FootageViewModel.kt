package com.example.guardianeye.ui.footage

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.data.repository.FootageManager
import com.example.guardianeye.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FootageViewModel(application: Application) : AndroidViewModel(application) {
    private val footageManager = FootageManager(application)
    private val preferenceManager = PreferenceManager(application)

    private val _footageState = MutableStateFlow(FootageState())
    val footageState: StateFlow<FootageState> = _footageState.asStateFlow()

    private val _selectedDirectoryUri = MutableStateFlow<Uri?>(null)
    val selectedDirectoryUri: StateFlow<Uri?> = _selectedDirectoryUri.asStateFlow()

    init {
        viewModelScope.launch {
            val uriStr = preferenceManager.getFootageDirectory()
            if (uriStr != null) {
                _selectedDirectoryUri.value = uriStr.toUri()
                loadFootage()
            }
        }
    }

    fun loadFootage() {
        viewModelScope.launch {
            val uri = _selectedDirectoryUri.value ?: return@launch
            val (unarchived, archived) = footageManager.getFootageFiles(uri)
            _footageState.value = FootageState(unarchived, archived)
        }
    }

    fun renameFile(file: DocumentFile, newName: String) {
        viewModelScope.launch {
            if (file.uri != null) {
                footageManager.renameFile(file.uri, newName)
                loadFootage() // Refresh list
            }
        }
    }

    fun deleteFile(file: DocumentFile) {
        viewModelScope.launch {
            if (file.uri != null) {
                footageManager.deleteFile(file.uri)
                loadFootage() // Refresh list
            }
        }
    }
}

data class FootageState(
    val unarchived: List<DocumentFile> = emptyList(),
    val archived: List<DocumentFile> = emptyList()
)
