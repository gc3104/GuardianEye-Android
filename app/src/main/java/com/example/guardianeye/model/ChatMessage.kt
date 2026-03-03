package com.example.guardianeye.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val alertId: String? = null,
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    @Embedded(prefix = "pending_")
    val pendingAction: PendingAction? = null,
    val searchResults: List<ContactResult>? = null
)

data class PendingAction(
    val intent: String,
    val countdownSeconds: Int = 5,
    val startTime: Long = System.currentTimeMillis(),
    val contact: String? = null,
    val detail: String? = null,
    val isAutoExecute: Boolean = true,
    val followUpAction: String? = null,
    val followUpText: String? = null
)

data class ContactResult(
    val name: String,
    val phoneNumber: String
)
