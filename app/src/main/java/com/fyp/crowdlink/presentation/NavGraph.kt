package com.fyp.crowdlink.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fyp.crowdlink.presentation.chat.ChatScreen
import com.fyp.crowdlink.presentation.discovery.DiscoveryScreen
import com.fyp.crowdlink.presentation.friends.FriendsScreen
import com.fyp.crowdlink.presentation.pairing.PairingScreen
import com.fyp.crowdlink.presentation.pairing.PairingViewModel
import com.fyp.crowdlink.presentation.pairing.QRScannerScreen
import com.fyp.crowdlink.presentation.relay.RelayDiscoveryScreen
import com.fyp.crowdlink.presentation.settings.ProfileScreen
import com.fyp.crowdlink.presentation.settings.SettingsScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    DISCOVERY("discovery", "Discovery", Icons.Default.Radar, "Discovery"),
    FRIENDS("friends", "Friends", Icons.Default.Groups, "Friends"),
    SETTINGS("settings", "Settings", Icons.Default.Settings, "Settings")
}

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
                onNavigateToFriends = { navController.navigate(Destination.FRIENDS.route) },
                onNavigateToRelay = { navController.navigate("relay_discovery") }
            )
        }
        composable(Destination.FRIENDS.route) {
            FriendsScreen(
                onNavigateToPairing = { navController.navigate("pairing") },
                onNavigateToSettings = { navController.navigate(Destination.SETTINGS.route) },
                onNavigateToChat = { id, name -> 
                    navController.navigate("chat/$id/$name") 
                }
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
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate("profile") }
            )
        }
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("relay_discovery") {
            RelayDiscoveryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("pairing") { backStackEntry ->
            val viewModel: PairingViewModel = hiltViewModel()
            val scannedQr = backStackEntry.savedStateHandle.get<String>("scanned_qr")
            
            if (scannedQr != null) {
                backStackEntry.savedStateHandle.remove<String>("scanned_qr")
                viewModel.onQRScanned(scannedQr, "Friend")
            }

            PairingScreen(
                viewModel = viewModel,
                onPairingSuccess = {
                    navController.popBackStack()
                },
                onScanClick = {
                    navController.navigate("scanner")
                }
            )
        }
        composable("scanner") {
            QRScannerScreen(
                onScanned = { scannedData ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("scanned_qr", scannedData)
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val destinations = listOf(
        Destination.DISCOVERY,
        Destination.FRIENDS,
        Destination.SETTINGS
    )

    Scaffold(
        bottomBar = {
            // Only show bottom bar on top-level destinations to avoid overlap with detail screens
            val showBottomBar = destinations.any { it.route == currentRoute }
            if (showBottomBar) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.contentDescription
                                )
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
