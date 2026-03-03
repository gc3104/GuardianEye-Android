package com.example.guardianeye.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.guardianeye.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chatmessage ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chatmessage WHERE alertId = :alertId ORDER BY timestamp ASC")
    fun getMessagesForAlert(alertId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chatmessage")
    suspend fun deleteAllMessages(): Int

    @Query("DELETE FROM chatmessage WHERE timestamp < :timestamp")
    suspend fun deleteMessagesOlderThan(timestamp: Long): Int
}
