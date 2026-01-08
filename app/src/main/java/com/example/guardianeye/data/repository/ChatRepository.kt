package com.example.guardianeye.data.repository

import com.example.guardianeye.data.local.ChatDao
import com.example.guardianeye.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    suspend fun insertMessage(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            chatDao.insertMessage(message)
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
