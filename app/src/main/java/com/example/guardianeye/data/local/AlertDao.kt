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

    // Assuming timestamp in Alert is stored as a comparable format (e.g. Long seconds/millis in Room type converter)
    // If it's a complex object, make sure TypeConverters are set up. 
    // Usually Timestamp is converted to Long.
    @Query("DELETE FROM alert WHERE timestamp < :timestamp")
    suspend fun deleteAlertsOlderThan(timestamp: Long)

    @Query("DELETE FROM alert WHERE id = :id")
    suspend fun deleteAlert(id: String)
}
