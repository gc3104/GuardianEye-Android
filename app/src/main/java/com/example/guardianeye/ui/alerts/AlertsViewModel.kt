package com.example.guardianeye.ui.alerts

import androidx.lifecycle.ViewModel
import com.example.guardianeye.model.Alert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        listenToAlerts()
    }

    private fun listenToAlerts() {
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                _isLoading.value = false
                if (e != null) {
                    // Handle error
                    return@addSnapshotListener
                }
                val alertsList = snapshots?.toObjects(Alert::class.java) ?: emptyList()
                _alerts.value = alertsList
            }
    }
}