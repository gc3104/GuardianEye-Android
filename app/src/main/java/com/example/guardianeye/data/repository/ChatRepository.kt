package com.example.guardianeye.data.repository

import com.example.guardianeye.data.local.ChatDao
import com.example.guardianeye.model.ChatMessage
import com.example.guardianeye.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao, private val preferenceManager: PreferenceManager) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getMessagesForAlert(alertId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForAlert(alertId)
    }

    suspend fun sendMessage(message: ChatMessage) {
        if (preferenceManager.getAlertPreference(PreferenceManager.SAVE_CHATS_LOCALLY)) {
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(message)
            }
        }
    }
    
    suspend fun deleteAllMessages() {
        withContext(Dispatchers.IO) {
            chatDao.deleteAllMessages()
        }
    }

    suspend fun deleteOldMessages(retentionPeriodDays: Int) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (retentionPeriodDays * 24 * 60 * 60 * 1000L)
            chatDao.deleteMessagesOlderThan(cutoff)
        }
    }
}
