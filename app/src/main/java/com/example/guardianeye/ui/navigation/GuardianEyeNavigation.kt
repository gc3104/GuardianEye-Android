package com.example.guardianeye.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector

object GuardianEyeDestinations {
    const val AUTH_CHECK_ROUTE = "auth_check"
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val HOME_ROUTE = "home"
    const val ALERTS_ROUTE = "alerts"
    const val FOOTAGE_ROUTE = "footage"
    const val CHAT_ROUTE = "chat"
    const val FAMILY_ROUTE = "family"

    // Settings Routes
    const val SETTINGS_GRAPH_ROUTE = "settings_graph"
    const val SETTINGS_ROUTE = "settings" // Main list
    const val SETTINGS_ACCOUNT_ROUTE = "settings/account"
    const val SETTINGS_SECURITY_ROUTE = "settings/security"
    const val SETTINGS_DATA_ROUTE = "settings/data"
    const val SETTINGS_AI_ROUTE = "settings/ai"
    const val SETTINGS_GENERAL_ROUTE = "settings/general"
    const val SETTINGS_PANIC_ROUTE = "settings/panic"
    const val SETTINGS_NOTIFICATIONS_ROUTE = "settings/notifications"

    const val ALERT_DETAIL_ROUTE = "alert_detail"
}

data class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconTextId: String
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(
        route = GuardianEyeDestinations.HOME_ROUTE,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Filled.Home,
        iconTextId = "Home"
    ),
    TopLevelDestination(
        route = GuardianEyeDestinations.ALERTS_ROUTE,
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Filled.Notifications,
        iconTextId = "Alerts"
    ),
    TopLevelDestination(
        route = GuardianEyeDestinations.FAMILY_ROUTE,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Filled.People,
        iconTextId = "Family"
    ),
    TopLevelDestination(
        route = GuardianEyeDestinations.CHAT_ROUTE,
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Filled.Chat,
        iconTextId = "Chat"
    ),
    TopLevelDestination(
        route = GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Filled.Settings,
        iconTextId = "Settings"
    )
)
