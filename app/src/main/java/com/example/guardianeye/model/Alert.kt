package com.example.guardianeye.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp

@Entity
data class Alert(
    @PrimaryKey
    val id: String = "",
    val type: AlertType = AlertType.UNKNOWN,
    val priority: AlertPriority = AlertPriority.MEDIUM,
    val timestamp: Timestamp = Timestamp.now(),
    val description: String = "",
    val isActionTaken: Boolean = false,
    val actionTakenType: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

enum class AlertType {
    UNKNOWN,
    INTRUDER,
    FACE_RECOGNITION,
    MASK_DETECTION,
    UNKNOWN_FACE,
    WEAPON,
    SCREAM
}

enum class AlertPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
