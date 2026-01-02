package com.example.guardianeye.data.repository

import com.example.guardianeye.data.local.AlertDao
import com.example.guardianeye.model.Alert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AlertRepository(private val alertDao: AlertDao) {
    val allAlerts: Flow<List<Alert>> = alertDao.getAllAlerts()

    suspend fun insertAlert(alert: Alert) {
        withContext(Dispatchers.IO) {
            alertDao.insertAlert(alert)
        }
    }

    suspend fun updateAlert(alert: Alert) {
        withContext(Dispatchers.IO) {
            alertDao.updateAlert(alert)
        }
    }

    suspend fun getAlert(id: String): Alert? {
        return withContext(Dispatchers.IO) {
            alertDao.getAlertById(id)
        }
    }

    suspend fun deleteAllAlerts() {
        withContext(Dispatchers.IO) {
            alertDao.deleteAllAlerts()
        }
    }
}
