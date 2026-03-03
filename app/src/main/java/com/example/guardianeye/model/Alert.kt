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
    val resolutionSource: ResolutionSource? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    
    // Family Support
    val familyId: String? = null,
    val detectedBy: String? = null, // Device ID or Member Name
    
    // Support for Composite Alerts
    val isComposite: Boolean = false,
    val childAlertIds: List<String>? = null,
    val compositeType: String? = null // e.g., "WEAPON_WITH_KNOWN_FACE"
)

enum class AlertType {
    UNKNOWN,
    KNOWN_FACE,
    FACE_RECOGNITION,
    MASK_DETECTION,
    UNKNOWN_FACE,
    WEAPON,
    SCREAM,
    COMPOSITE
}

enum class AlertPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ResolutionSource {
    USER,
    AUTO,
    ASSISTANT
}
