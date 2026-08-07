package com.travelingsalesman.agap

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshService
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.navigation.AgapNavGraph
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import com.travelingsalesman.agap.ui.theme.RedPrimary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    @Inject
    lateinit var bleMeshManager: BleMeshManager

    private var cachedAuthToken: String? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasCorePermissions()) {
            checkAndRequestBluetoothEnable()
            startBleMeshForegroundService()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.i("MainActivity: Bluetooth enabled by user via OS dialog.")
            bleMeshManager.checkHardwareSupport()
            if (hasCorePermissions()) {
                startBleMeshForegroundService()
            }
        } else {
            Timber.w("MainActivity: Bluetooth enable request was cancelled by user.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val isDarkMode by authTokenManager.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            var isAuthReady by remember { mutableStateOf(false) }
            var startDest by remember { mutableStateOf(AgapDestinations.LOGIN_ROUTE) }
            LaunchedEffect(Unit) {
                val initialToken = authTokenManager.authToken.first()
                cachedAuthToken = initialToken
                val initialRole = authTokenManager.userRole.first()
                val isLoggedIn = !initialToken.isNullOrEmpty()
                startDest = when {
                    !isLoggedIn -> AgapDestinations.LOGIN_ROUTE
                    initialRole == "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                    initialRole == "RESPONDER" || initialRole == "DISPATCHER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                    else -> AgapDestinations.HOME_ROUTE
                }
                isAuthReady = true

                // Only request permissions and activate BLE Mesh after successful login
                if (isLoggedIn) {
                    checkAndRequestPermissions()
                }
            }

            val meshState by bleMeshManager.meshState.collectAsStateWithLifecycle()

            LaunchedEffect(meshState.needsPermissionsPrompt) {
                if (meshState.needsPermissionsPrompt && !cachedAuthToken.isNullOrEmpty()) {
                    checkAndRequestPermissions()
                    bleMeshManager.resetPermissionsPrompt()
                }
            }

            LaunchedEffect(meshState.needsBluetoothEnable) {
                if (meshState.needsBluetoothEnable && !cachedAuthToken.isNullOrEmpty()) {
                    requestBluetoothEnable()
                    bleMeshManager.resetBluetoothEnablePrompt()
                }
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
                        onSosClick = {
                            Timber.d("CRITICAL: SOS ACTION TRIGGERED FROM NAV BAR")
                            bleMeshManager.ensureBluetoothEnabled()
                            if (!hasCorePermissions()) {
                                checkAndRequestPermissions()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val token = authTokenManager.authToken.first()
            cachedAuthToken = token
            if (!token.isNullOrEmpty() && hasCorePermissions()) {
                startBleMeshForegroundService()
            }
        }
    }

    fun hasCorePermissions(): Boolean {
        var isGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val adGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            isGranted = isGranted && scanGranted && adGranted
        }
        return isGranted
    }

    private fun startBleMeshForegroundService() {
        try {
            val serviceIntent = Intent(this, BleMeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bleMeshManager.startMeshServices()
            com.travelingsalesman.agap.worker.SyncWorker.schedulePeriodicSync(this)
            Timber.i("MainActivity: Started BleMeshService, mesh scanning, and 15-min periodic sync worker successfully")
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Failed to start BleMeshService")
        }
    }

    fun requestBluetoothEnable() {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            Timber.i("MainActivity: Launched OS Bluetooth Enable dialog.")
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Unable to launch Bluetooth enable intent.")
        }
    }

    private fun checkAndRequestBluetoothEnable() {
        if (!bleMeshManager.meshState.value.isEnabled) {
            requestBluetoothEnable()
        }
    }

    fun checkAndRequestPermissions() {
        lifecycleScope.launch {
            val token = authTokenManager.authToken.first()
            cachedAuthToken = token
            if (token.isNullOrEmpty()) {
                Timber.d("MainActivity: User is not logged in yet. Skipping pre-login BLE permission prompts.")
                return@launch
            }

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
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            val missingPermissions = permissionsToRequest.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
            } else {
                checkAndRequestBluetoothEnable()
                startBleMeshForegroundService()
            }
        }
    }
}