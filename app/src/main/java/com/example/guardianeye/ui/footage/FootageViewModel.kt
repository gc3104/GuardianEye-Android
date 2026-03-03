package com.example.guardianeye.ui.footage

import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.data.repository.FootageManager
import com.example.guardianeye.utils.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FootageViewModel @Inject constructor(
    private val footageManager: FootageManager,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _footageState = MutableStateFlow(FootageState())
    val footageState: StateFlow<FootageState> = _footageState.asStateFlow()

    private val _selectedDirectoryUri = MutableStateFlow<Uri?>(null)
    val selectedDirectoryUri: StateFlow<Uri?> = _selectedDirectoryUri.asStateFlow()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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
            _footageState.update { it.copy(unarchived = unarchived, archived = archived) }
        }
    }

    fun syncFootageFromServer(serverFileUrls: List<String>) {
        val destUri = _selectedDirectoryUri.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            serverFileUrls.forEach { url ->
                val fileName = url.substringAfterLast("/")
                if (fileName.isNotBlank()) {
                    footageManager.downloadAndSaveFootage(url, fileName, destUri)
                }
            }
            loadFootage()
            _isSyncing.value = false
        }
    }

    fun renameFile(file: DocumentFile, newName: String) {
        viewModelScope.launch {
            footageManager.renameFile(file.uri, newName)
            loadFootage() // Refresh list
        }
    }

    fun deleteFile(file: DocumentFile) {
        viewModelScope.launch {
            footageManager.deleteFile(file.uri)
            loadFootage() // Refresh list
        }
    }
}

data class FootageState(
    val unarchived: List<DocumentFile> = emptyList(),
    val archived: List<DocumentFile> = emptyList()
)
