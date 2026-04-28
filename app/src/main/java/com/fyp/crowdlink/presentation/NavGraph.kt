package com.fyp.crowdlink.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fyp.crowdlink.domain.model.SosAlertData
import com.fyp.crowdlink.presentation.chat.ChatScreen
import com.fyp.crowdlink.presentation.compass.CompassScreen
import com.fyp.crowdlink.presentation.discovery.DiscoveryScreen
import com.fyp.crowdlink.presentation.friends.FriendsScreen
import com.fyp.crowdlink.presentation.map.MapScreen
import com.fyp.crowdlink.presentation.pairing.PairingScreen
import com.fyp.crowdlink.presentation.pairing.PairingViewModel
import com.fyp.crowdlink.presentation.pairing.QRScannerScreen
import com.fyp.crowdlink.presentation.relay.RelayDiscoveryScreen
import com.fyp.crowdlink.presentation.settings.ProfileScreen
import com.fyp.crowdlink.presentation.settings.SettingsScreen
import com.fyp.crowdlink.presentation.sos.SosAlertScreen

/**
 * Destination
 *
 * Defines the four top-level bottom-nav destinations. Routes for sub-screens
 * (chat, compass, pairing, etc.) are registered directly in [AppNavHost].
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    DISCOVERY("discovery", "Discovery", Icons.Default.Radar, "Discovery"),
    FRIENDS("friends", "Friends", Icons.Default.Groups, "Friends"),
    MAP("map", "Map", Icons.Default.Map, "Map"),
    SETTINGS("settings", "Settings", Icons.Default.Settings, "Settings")
}

/**
 * AppNavHost
 *
 * Registers all navigation destinations in the app. Top-level tabs are in [Destination].
 * Sub-screens (chat, compass, map with args, pairing, scanner, SOS alert, relay, profile)
 * are registered here with their route arguments. QR scan results are passed back to the
 * pairing screen via [savedStateHandle] to keep the scanner decoupled from [PairingViewModel].
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destination.DISCOVERY.route,
        modifier = modifier
    ) {
        composable(Destination.DISCOVERY.route) {
            DiscoveryScreen(
                onNavigateToCompass = { id, name -> navController.navigate("compass/$id/$name") },
                onNavigateToChat = { id, name -> navController.navigate("chat/$id/$name") },
                onNavigateToMap = { id, name -> navController.navigate("map?friendId=$id&friendName=$name") },
                onNavigateToRelay = { navController.navigate("relay_discovery") }
            )
        }
        composable(Destination.FRIENDS.route) {
            FriendsScreen(
                onNavigateToPairing = { navController.navigate("pairing") },
                onNavigateToSettings = { navController.navigate(Destination.SETTINGS.route) },
                onNavigateToChat = { id, name -> navController.navigate("chat/$id/$name") },
                onNavigateToCompass = { id, name -> navController.navigate("compass/$id/$name") }
            )
        }
        composable(
            route = "map?friendId={friendId}&friendName={friendName}",
            arguments = listOf(
                navArgument("friendId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("friendName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId")
            MapScreen(
                initialFriendId = friendId,
                onNavigateToCompass = { id, name -> navController.navigate("compass/$id/$name") },
                onNavigateToChat = { id, name -> navController.navigate("chat/$id/$name") }
            )
        }
        composable(
            route = "chat/{friendId}/{friendName}",
            arguments = listOf(
                navArgument("friendId") { type = NavType.StringType },
                navArgument("friendName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: ""
            val friendName = backStackEntry.arguments?.getString("friendName") ?: ""
            ChatScreen(
                friendId = friendId,
                friendName = friendName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "compass/{friendId}/{friendName}",
            arguments = listOf(
                navArgument("friendId") { type = NavType.StringType },
                navArgument("friendName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: ""
            val friendName = backStackEntry.arguments?.getString("friendName") ?: ""
            CompassScreen(
                friendId = friendId,
                friendName = friendName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate("profile") }
            )
        }
        composable("profile") {
            ProfileScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("relay_discovery") {
            RelayDiscoveryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable("pairing") { backStackEntry ->
            val viewModel: PairingViewModel = hiltViewModel()

            // QR scan result is delivered via savedStateHandle to keep the scanner screen
            // decoupled from PairingViewModel - consumed once and cleared
            val scannedQr = backStackEntry.savedStateHandle.get<String>("scanned_qr")
            if (scannedQr != null) {
                backStackEntry.savedStateHandle.remove<String>("scanned_qr")
                viewModel.onQRScanned(scannedQr, "Friend")
            }

            PairingScreen(
                viewModel = viewModel,
                onPairingSuccess = { navController.popBackStack() },
                onScanClick = { navController.navigate("scanner") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("scanner") {
            QRScannerScreen(
                onScanned = { scannedData ->
                    // write result to the pairing screen's savedStateHandle before popping
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("scanned_qr", scannedData)
                    navController.popBackStack()
                },
                onCancelled = { navController.popBackStack() }
            )
        }
        composable(
            route = "sos_alert/{friendId}/{senderName}/{latitude}/{longitude}/{receivedAt}",
            arguments = listOf(
                navArgument("friendId") { type = NavType.StringType },
                navArgument("senderName") { type = NavType.StringType },
                navArgument("latitude") { type = NavType.FloatType },
                navArgument("longitude") { type = NavType.FloatType },
                navArgument("receivedAt") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: ""
            val senderName = backStackEntry.arguments?.getString("senderName") ?: "Unknown"
            // 0.0 is used as a sentinel for missing coordinates - convert back to null
            val latitude = backStackEntry.arguments?.getFloat("latitude")?.toDouble()?.takeIf { it != 0.0 }
            val longitude = backStackEntry.arguments?.getFloat("longitude")?.toDouble()?.takeIf { it != 0.0 }
            val receivedAt = backStackEntry.arguments?.getLong("receivedAt") ?: 0L

            SosAlertScreen(
                friendId = friendId,
                senderName = senderName,
                latitude = latitude,
                longitude = longitude,
                receivedAt = receivedAt,
                onNavigateToChat = {
                    navController.navigate("chat/$friendId/$senderName") {
                        popUpTo("sos_alert/$friendId/$senderName/${latitude ?: 0.0}/${longitude ?: 0.0}/$receivedAt") {
                            inclusive = true
                        }
                    }
                },
                onNavigateToMap = {
                    navController.navigate("map?friendId=$friendId&friendName=$senderName") {
                        popUpTo("sos_alert/$friendId/$senderName/${latitude ?: 0.0}/${longitude ?: 0.0}/$receivedAt") {
                            inclusive = true
                        }
                    }
                },
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}

/**
 * MainScreen
 *
 * Root composable shown after onboarding completes. Hosts the bottom navigation bar and
 * [AppNavHost]. Deep links from notification taps are received as [pendingChatFriendId]
 * and [pendingSosAlert] and consumed via [LaunchedEffect] to navigate imperatively once.
 * The bottom bar is hidden on sub-screens so it only appears on top-level tab destinations.
 */
@Composable
fun MainScreen(
    pendingChatFriendId: String? = null,
    onChatNavigated: () -> Unit = {},
    pendingSosAlert: SosAlertData? = null,
    onSosAlertNavigated: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // consume chat deep link from notification tap
    LaunchedEffect(pendingChatFriendId) {
        pendingChatFriendId?.let { friendId ->
            navController.navigate("chat/$friendId/Chat") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onChatNavigated()
        }
    }

    // consume SOS alert deep link from notification tap
    LaunchedEffect(pendingSosAlert) {
        pendingSosAlert?.let { alert ->
            navController.navigate(
                "sos_alert/${alert.friendId}/${alert.senderName}/${alert.latitude ?: 0.0}/${alert.longitude ?: 0.0}/${alert.receivedAt}"
            ) {
                launchSingleTop = true
            }
            onSosAlertNavigated()
        }
    }

    val destinations = listOf(
        Destination.DISCOVERY,
        Destination.FRIENDS,
        Destination.MAP,
        Destination.SETTINGS
    )

    Scaffold(
        bottomBar = {
            // bottom bar is hidden on sub-screens - only shown on the four top-level tabs
            val showBottomBar = destinations.any { currentRoute?.startsWith(it.route) == true }
            if (showBottomBar) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute?.startsWith(destination.route) == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.contentDescription)
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(contentPadding)
        )
    }
}