package com.example.guardianeye.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.guardianeye.R
import com.example.guardianeye.ui.alerts.AlertDetailScreen
import com.example.guardianeye.ui.alerts.AlertsScreen
import com.example.guardianeye.ui.auth.authcheck.AuthCheckScreen
import com.example.guardianeye.ui.auth.login.LoginScreen
import com.example.guardianeye.ui.auth.register.RegisterScreen
import com.example.guardianeye.ui.chat.ChatScreen
import com.example.guardianeye.ui.components.PanicOverlay
import com.example.guardianeye.ui.footage.FootageScreen
import com.example.guardianeye.ui.home.HomeScreen
import com.example.guardianeye.ui.home.HomeViewModel
import com.example.guardianeye.ui.navigation.GuardianEyeDestinations
import com.example.guardianeye.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.example.guardianeye.ui.settings.AccountSettingsScreen
import com.example.guardianeye.ui.settings.AiSettingsScreen
import com.example.guardianeye.ui.settings.DataSettingsScreen
import com.example.guardianeye.ui.settings.GeneralSettingsScreen
import com.example.guardianeye.ui.settings.NotificationSettingsScreen
import com.example.guardianeye.ui.settings.PanicSettingsScreen
import com.example.guardianeye.ui.settings.SecuritySettingsScreen
import com.example.guardianeye.ui.settings.SettingsScreen
import com.example.guardianeye.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianEyeApp(
    onPanicTrigger: () -> Unit,
    showPanicOverlay: Boolean,
    onPanicCancel: () -> Unit,
    panicTimerSeconds: Int,
    onPanicTimerFinished: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if current screen is top-level (drawer + top bar)
    val isTopLevel = currentRoute in listOf(
        GuardianEyeDestinations.HOME_ROUTE,
        GuardianEyeDestinations.ALERTS_ROUTE,
        GuardianEyeDestinations.FOOTAGE_ROUTE,
        GuardianEyeDestinations.CHAT_ROUTE,
        GuardianEyeDestinations.SETTINGS_ROUTE
    )

    if (isTopLevel) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    TOP_LEVEL_DESTINATIONS.forEach { destination ->
                        val isSelected = currentRoute.let { route ->
                            if (destination.route == GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE) {
                                route == GuardianEyeDestinations.SETTINGS_ROUTE
                            } else if (route?.contains("?") == true) {
                                route.substringBefore("?") == destination.route
                            } else {
                                route == destination.route
                            }
                        }

                        NavigationDrawerItem(
                            label = { Text(destination.iconTextId) },
                            icon = { Icon(destination.selectedIcon, contentDescription = null) },
                            selected = isSelected,
                            onClick = {
                                val targetRoute = if (destination.route == GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE) {
                                    GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE
                                } else destination.route

                                navController.navigate(targetRoute) {
                                    popUpTo(GuardianEyeDestinations.HOME_ROUTE) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = onPanicTrigger) {
                                Icon(Icons.Default.Warning, contentDescription = "Panic", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    GuardianEyeNavHost(navController = navController, onLogout = onLogout)
                }
            }
        }
    } else {
        // Sub-screens: no drawer, just show NavHost
        Box(modifier = Modifier.fillMaxSize()) {
            GuardianEyeNavHost(navController = navController, onLogout = onLogout)
        }
    }

    PanicOverlay(
        showOverlay = showPanicOverlay,
        onCancelClick = onPanicCancel,
        timerSeconds = panicTimerSeconds,
        onTimerFinished = onPanicTimerFinished
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}

@Composable
fun GuardianEyeNavHost(
    navController: NavHostController,
    onLogout: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = GuardianEyeDestinations.AUTH_CHECK_ROUTE
    ) {
        // Auth
        composable(GuardianEyeDestinations.AUTH_CHECK_ROUTE) {
            AuthCheckScreen(
                onAuthSuccess = {
                    navController.navigate(GuardianEyeDestinations.HOME_ROUTE) {
                        popUpTo(GuardianEyeDestinations.AUTH_CHECK_ROUTE) { inclusive = true }
                    }
                },
                onNotLoggedIn = {
                    navController.navigate(GuardianEyeDestinations.LOGIN_ROUTE) {
                        popUpTo(GuardianEyeDestinations.AUTH_CHECK_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        composable(GuardianEyeDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(GuardianEyeDestinations.AUTH_CHECK_ROUTE) {
                        popUpTo(GuardianEyeDestinations.LOGIN_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(GuardianEyeDestinations.REGISTER_ROUTE)
                }
            )
        }

        composable(GuardianEyeDestinations.REGISTER_ROUTE) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(GuardianEyeDestinations.AUTH_CHECK_ROUTE) {
                        popUpTo(GuardianEyeDestinations.REGISTER_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(GuardianEyeDestinations.LOGIN_ROUTE) {
                        popUpTo(GuardianEyeDestinations.REGISTER_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // Home
        composable(GuardianEyeDestinations.HOME_ROUTE) {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(viewModel = viewModel, onNavigateToFootage = {
                navController.navigate(GuardianEyeDestinations.FOOTAGE_ROUTE)
            })
        }

        // Alerts
        composable(GuardianEyeDestinations.ALERTS_ROUTE) {
            AlertsScreen(
                onNavigateToChat = { alert ->
                    val route = "${GuardianEyeDestinations.CHAT_ROUTE}?alertId=${alert.id}&alertType=${alert.type}&alertDesc=${Uri.encode(alert.description)}&mediaUrl=${Uri.encode(alert.mediaUrl ?: "")}&mediaType=${alert.mediaType ?: ""}"
                    navController.navigate(route)
                },
                onNavigateToDetail = { alert ->
                    val route = "${GuardianEyeDestinations.ALERT_DETAIL_ROUTE}/${alert.id}/${alert.type}/${Uri.encode(alert.description)}/${Uri.encode(alert.mediaUrl ?: "")}/${alert.mediaType ?: "NONE"}"
                    navController.navigate(route)
                }
            )
        }

        // Footage
        composable(GuardianEyeDestinations.FOOTAGE_ROUTE) {
            FootageScreen()
        }

        // Chat
        composable(
            route = "${GuardianEyeDestinations.CHAT_ROUTE}?alertId={alertId}&alertType={alertType}&alertDesc={alertDesc}&mediaUrl={mediaUrl}&mediaType={mediaType}",
            arguments = listOf(
                navArgument("alertId") { nullable = true; defaultValue = null },
                navArgument("alertType") { nullable = true; defaultValue = null },
                navArgument("alertDesc") { nullable = true; defaultValue = null },
                navArgument("mediaUrl") { nullable = true; defaultValue = null },
                navArgument("mediaType") { nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            ChatScreen(
                alertId = backStackEntry.arguments?.getString("alertId"),
                alertType = backStackEntry.arguments?.getString("alertType"),
                alertDesc = backStackEntry.arguments?.getString("alertDesc"),
                mediaUrl = backStackEntry.arguments?.getString("mediaUrl"),
                mediaType = backStackEntry.arguments?.getString("mediaType")
            )
        }

        // Settings Graph
        navigation(
            startDestination = GuardianEyeDestinations.SETTINGS_ROUTE,
            route = GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE
        ) {
            composable(GuardianEyeDestinations.SETTINGS_ROUTE) {
                SettingsScreen(
                    onNavigateToAccount = { navController.navigate(GuardianEyeDestinations.SETTINGS_ACCOUNT_ROUTE) },
                    onNavigateToSecurity = { navController.navigate(GuardianEyeDestinations.SETTINGS_SECURITY_ROUTE) },
                    onNavigateToData = { navController.navigate(GuardianEyeDestinations.SETTINGS_DATA_ROUTE) },
                    onNavigateToAi = { navController.navigate(GuardianEyeDestinations.SETTINGS_AI_ROUTE) },
                    onNavigateToGeneral = { navController.navigate(GuardianEyeDestinations.SETTINGS_GENERAL_ROUTE) },
                    onNavigateToPanic = { navController.navigate(GuardianEyeDestinations.SETTINGS_PANIC_ROUTE) },
                    onNavigateToNotifications = { navController.navigate(GuardianEyeDestinations.SETTINGS_NOTIFICATIONS_ROUTE) }
                )
            }

            fun settingsSubScreen(
                route: String,
                title: String,
                content: @Composable (SettingsViewModel) -> Unit
            ) {
                composable(route) { entry ->
                    val parentEntry = remember(entry) {
                        navController.getBackStackEntry(GuardianEyeDestinations.SETTINGS_GRAPH_ROUTE)
                    }
                    val viewModel: SettingsViewModel = viewModel(parentEntry)
                    SettingsSubScreen(title = title, onBack = { navController.popBackStack() }) {
                        content(viewModel)
                    }
                }
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_ACCOUNT_ROUTE, "Account") {
                AccountSettingsScreen(viewModel = it, onLogout = {
                    onLogout()
                    navController.navigate(GuardianEyeDestinations.LOGIN_ROUTE) {
                        popUpTo(0)
                    }
                })
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_SECURITY_ROUTE, "Security") {
                SecuritySettingsScreen(viewModel = it)
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_DATA_ROUTE, "Data Management") {
                DataSettingsScreen(viewModel = it)
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_AI_ROUTE, "AI Model") {
                AiSettingsScreen(viewModel = it)
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_GENERAL_ROUTE, "General") {
                GeneralSettingsScreen(viewModel = it)
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_PANIC_ROUTE, "Panic Button") {
                PanicSettingsScreen(viewModel = it)
            }

            settingsSubScreen(GuardianEyeDestinations.SETTINGS_NOTIFICATIONS_ROUTE, "Notifications") {
                NotificationSettingsScreen(viewModel = it)
            }
        }

        // Alert Detail
        composable(
            route = "${GuardianEyeDestinations.ALERT_DETAIL_ROUTE}/{alertId}/{alertType}/{alertDesc}/{mediaUrl}/{mediaType}",
            arguments = listOf(
                navArgument("alertId") { type = NavType.StringType },
                navArgument("alertType") { type = NavType.StringType },
                navArgument("alertDesc") { type = NavType.StringType },
                navArgument("mediaUrl") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            AlertDetailScreen(
                alertType = backStackEntry.arguments?.getString("alertType"),
                alertDesc = backStackEntry.arguments?.getString("alertDesc"),
                mediaUrl = backStackEntry.arguments?.getString("mediaUrl"),
                mediaType = backStackEntry.arguments?.getString("mediaType")
            )
        }
    }
}
