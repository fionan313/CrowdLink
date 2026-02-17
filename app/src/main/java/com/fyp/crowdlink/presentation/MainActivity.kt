package com.fyp.crowdlink.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fyp.crowdlink.presentation.chat.ChatScreen
import com.fyp.crowdlink.presentation.discovery.DiscoveryScreen
import com.fyp.crowdlink.presentation.friends.FriendsScreen
import com.fyp.crowdlink.presentation.pairing.PairingScreen
import com.fyp.crowdlink.presentation.pairing.PairingViewModel
import com.fyp.crowdlink.presentation.pairing.QRScannerScreen
import com.fyp.crowdlink.presentation.relay.RelayDiscoveryScreen
import com.fyp.crowdlink.presentation.settings.SettingsScreen
import com.fyp.crowdlink.ui.theme.CrowdLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            CrowdLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "discovery") {
                        composable("discovery") {
                            DiscoveryScreen(
                                onNavigateToFriends = {
                                    navController.navigate("friends")
                                },
                                onNavigateToRelay = {
                                    navController.navigate("relay_discovery")
                                }
                            )
                        }
                        
                        composable("relay_discovery") {
                            RelayDiscoveryScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("friends") {
                            FriendsScreen(
                                onNavigateToPairing = {
                                    navController.navigate("pairing")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToChat = { friendId, friendName ->
                                    navController.navigate("chat/$friendId/$friendName")
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
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        
                        composable("pairing") { backStackEntry ->
                            val viewModel: PairingViewModel = hiltViewModel()
                            val savedStateHandle = backStackEntry.savedStateHandle
                            val scannedQr = savedStateHandle.get<String>("scanned_qr")
                            
                            if (scannedQr != null) {
                                savedStateHandle.remove<String>("scanned_qr")
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
            }
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }
}
