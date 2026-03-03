package com.example.guardianeye.data.repository

import com.example.guardianeye.data.local.AlertDao
import com.example.guardianeye.model.Alert
import com.example.guardianeye.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AlertRepository(private val alertDao: AlertDao, private val preferenceManager: PreferenceManager) {
    val allAlerts: Flow<List<Alert>> = alertDao.getAllAlerts()

    suspend fun insert(alert: Alert) {
        if (preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY)) {
            withContext(Dispatchers.IO) {
                alertDao.insertAlert(alert)
            }
        }
    }

    suspend fun updateAlert(alert: Alert) {
        if (preferenceManager.getAlertPreference(PreferenceManager.SAVE_ALERTS_LOCALLY)) {
            withContext(Dispatchers.IO) {
                alertDao.updateAlert(alert)
            }
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
    
    suspend fun deleteOldAlerts(retentionPeriodDays: Int) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (retentionPeriodDays * 24 * 60 * 60 * 1000L)
            alertDao.deleteAlertsOlderThan(cutoff)
        }
    }

    suspend fun deleteAlert(alert: Alert) {
        withContext(Dispatchers.IO) {
            alertDao.deleteAlert(alert)
        }
    }
}
