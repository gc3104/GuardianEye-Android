package com.example.guardianeye.model

data class Family(
    val id: String = "",
    val name: String = "",
    val adminId: String = "",
    val members: List<FamilyMember> = emptyList(),
    val settings: FamilySettings = FamilySettings()
)

data class FamilyMember(
    val userId: String = "",
    val displayName: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    val profileImageUrl: String? = null
)

enum class MemberRole {
    ADMIN,
    MEMBER,
    GUEST
}

data class FamilySettings(
    val allowMemberManagement: Boolean = false,
    val shareAlertsAutomatically: Boolean = true,
    val alertFormat: String = "DEFAULT" // Example for the "formatter based on model"
)
