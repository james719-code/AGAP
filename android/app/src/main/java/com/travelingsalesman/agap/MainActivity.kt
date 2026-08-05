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
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.RedPrimary
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            val isDarkMode by authTokenManager.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            var isAuthReady by remember { mutableStateOf(false) }
            var startDest by remember { mutableStateOf(AgapDestinations.LOGIN_ROUTE) }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val initialToken = authTokenManager.authToken.first()
                val initialRole = authTokenManager.userRole.first()
                startDest = when {
                    initialToken.isNullOrEmpty() -> AgapDestinations.LOGIN_ROUTE
                    initialRole == "RESPONDER" || initialRole == "DISPATCHER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                    else -> AgapDestinations.HOME_ROUTE
                }
                isAuthReady = true
            }

            AGAPTheme(darkTheme = isDarkMode) {
                if (!isAuthReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = RedPrimary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "AGAP COMMAND",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Initializing Mesh Telemetry...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    AgapNavGraph(
                        startDestination = startDest,
                        isDarkTheme = isDarkMode,
                        onToggleTheme = {
                            coroutineScope.launch {
                                authTokenManager.setDarkMode(!isDarkMode)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
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