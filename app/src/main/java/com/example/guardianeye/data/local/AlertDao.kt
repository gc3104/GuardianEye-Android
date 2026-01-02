package com.example.guardianeye.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.guardianeye.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alert WHERE id = :id")
    suspend fun getAlertById(id: String): Alert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert)

    @Update
    suspend fun updateAlert(alert: Alert)

    @Query("DELETE FROM alert")
    suspend fun deleteAllAlerts()
}
