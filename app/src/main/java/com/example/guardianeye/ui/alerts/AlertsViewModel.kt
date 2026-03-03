package com.example.guardianeye.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.guardianeye.data.repository.AlertRepository
import com.example.guardianeye.model.Alert
import com.example.guardianeye.utils.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAlerts()
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            if (preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY)) {
                // Load from local DB
                alertRepository.allAlerts.collectLatest { localAlerts ->
                    _alerts.value = localAlerts
                    _isLoading.value = false
                }
            } else {
                // Load from Firebase (Original Logic)
                listenToFirebaseAlerts()
            }
        }
    }

    private fun listenToFirebaseAlerts() {
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                _isLoading.value = false
                if (e != null) {
                    return@addSnapshotListener
                }
                val alertsList = snapshots?.toObjects(Alert::class.java) ?: emptyList()
                _alerts.value = alertsList
            }
    }

    fun deleteAlert(alert: Alert) {
        viewModelScope.launch {
            if (preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY)) {
                alertRepository.deleteAlert(alert)
            } else {
                // If not saving locally, maybe delete from Firebase? 
                // For now, assuming local deletion is the requested feature.
                // Firebase deletion typically requires more checks.
            }
        }
    }
}
