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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fyp.crowdlink.presentation.discovery.DiscoveryScreen
import com.fyp.crowdlink.presentation.friends.FriendsScreen
import com.fyp.crowdlink.presentation.pairing.PairingScreen
import com.fyp.crowdlink.presentation.pairing.PairingViewModel
import com.fyp.crowdlink.presentation.pairing.QRScannerScreen
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
                                }
                            )
                        }
                        
                        composable("friends") {
                            FriendsScreen(
                                onAddFriend = {
                                    navController.navigate("pairing")
                                }
                            )
                        }
                        
                        composable("pairing") { backStackEntry ->
                            // Get the ViewModel scoped to this navigation graph entry
                            val viewModel: PairingViewModel = hiltViewModel()
                            
                            // Check for a scan result from the QRScannerScreen
                            val savedStateHandle = backStackEntry.savedStateHandle
                            val scannedQr = savedStateHandle.get<String>("scanned_qr")
                            
                            if (scannedQr != null) {
                                // Consume the result so we don't process it again
                                savedStateHandle.remove<String>("scanned_qr")
                                // Trigger the pairing logic in the ViewModel
                                viewModel.onQRScanned(scannedQr, "Friend") // You might want to ask for a name here
                            }

                            PairingScreen(
                                viewModel = viewModel,
                                onPairingSuccess = {
                                    navController.popBackStack() // Return to friends list
                                },
                                onScanClick = {
                                    navController.navigate("scanner")
                                }
                            )
                        }
                        
                        composable("scanner") {
                            QRScannerScreen(
                                onScanned = { scannedData ->
                                    // Pass the result back to the previous screen (PairingScreen)
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
