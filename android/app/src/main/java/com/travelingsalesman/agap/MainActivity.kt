package com.travelingsalesman.agap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.navigation.AgapNavGraph
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            val token by authTokenManager.authToken.collectAsStateWithLifecycle(initialValue = null)
            val role by authTokenManager.userRole.collectAsStateWithLifecycle(initialValue = null)

            val startDest = when {
                token.isNullOrEmpty() -> AgapDestinations.LOGIN_ROUTE
                role == "RESPONDER" || role == "DISPATCHER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                else -> AgapDestinations.HOME_ROUTE
            }

            AGAPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgapNavGraph(
                        startDestination = startDest,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}