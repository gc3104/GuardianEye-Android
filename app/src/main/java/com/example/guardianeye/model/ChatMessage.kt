package com.example.guardianeye.model

data class ChatMessage(
    val id: String = "",
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null // If this message represents an action button
)