package com.example.guardianeye.ui.home

import android.app.Application
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    private val _recentFootage = MutableStateFlow<List<Footage>>(emptyList())
    val recentFootage: StateFlow<List<Footage>> = _recentFootage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _streamUrl.value = preferenceManager.getStreamUrl()
            loadRecentFootage()
        }
    }

    private suspend fun loadRecentFootage() {
        val savedUriString = preferenceManager.getFootageDirectory()
        if (!savedUriString.isNullOrEmpty()) {
            try {
                val uri = savedUriString.toUri()
                val context = getApplication<Application>()
                val persistedUriPermissions = context.contentResolver.persistedUriPermissions
                val hasPermission = persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

                if (hasPermission) {
                    val allFootage = withContext(Dispatchers.IO) {
                         val footageList = mutableListOf<Footage>()
                         val docFile = DocumentFile.fromTreeUri(context, uri)
                         docFile?.listFiles()?.forEach { file ->
                            if (file.isFile && (file.type?.startsWith("video/") == true || file.name?.endsWith(".mp4") == true)) {
                                footageList.add(
                                    Footage(
                                        id = file.uri.toString(),
                                        name = file.name ?: "Unknown",
                                        path = file.uri.toString(),
                                        timestamp = Date(file.lastModified()),
                                        size = "", // Simplification
                                        duration = ""
                                    )
                                )
                            }
                         }
                         footageList
                    }
                    _recentFootage.value = allFootage.sortedByDescending { it.timestamp }.take(5)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}