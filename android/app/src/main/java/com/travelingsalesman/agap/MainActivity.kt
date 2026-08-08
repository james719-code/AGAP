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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.travelingsalesman.agap.core.bluetooth.BleMeshManager
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.bluetooth.BleMeshService
import com.travelingsalesman.agap.core.bluetooth.ResponderTelemetryService
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.navigation.AgapNavGraph
import com.travelingsalesman.agap.ui.theme.AGAPTheme
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

    private var cachedAuthToken: String? =
        null

    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            BleMeshLog.i(
                "PERMISSION_RESULT",
                "scan" to grants[Manifest.permission.BLUETOOTH_SCAN],
                "advertise" to grants[Manifest.permission.BLUETOOTH_ADVERTISE],
                "connect" to grants[Manifest.permission.BLUETOOTH_CONNECT],
                "fineLocation" to grants[Manifest.permission.ACCESS_FINE_LOCATION]
            )

            if (hasCorePermissions()) {
                checkAndRequestBluetoothEnable()
                startBleMeshForegroundService()
            } else {
                BleMeshLog.w("BLE_PERMISSIONS_STILL_MISSING")
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode ==
                RESULT_OK
            ) {

                BleMeshLog.i("BLUETOOTH_ENABLE_CONFIRMED")

                bleMeshManager
                    .checkHardwareSupport()

                if (
                    hasCorePermissions()
                ) {

                    startBleMeshForegroundService()
                }

            } else {

                BleMeshLog.w("BLUETOOTH_ENABLE_CANCELLED")
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        /*
         * SplashActivity has ALREADY resolved this.
         *
         * There is no DataStore wait before rendering.
         */
        val initialDestination =
            intent.getStringExtra(
                EXTRA_START_DESTINATION
            )
                ?: AgapDestinations.LOGIN_ROUTE

        val initialDarkMode =
            intent.getBooleanExtra(
                EXTRA_INITIAL_DARK_MODE,
                true
            )

        Timber.d(
            "MainActivity immediately rendering: $initialDestination"
        )

        setContent {

            /*
             * Theme can still update reactively after startup.
             */
            val isDarkMode by
            authTokenManager
                .isDarkMode
                .collectAsStateWithLifecycle(
                    initialValue =
                        initialDarkMode
                )

            /*
             * Authentication is checked here ONLY for
             * background BLE initialization.
             *
             * It does NOT block UI rendering.
             */
            LaunchedEffect(Unit) {

                val token =
                    authTokenManager
                        .authToken
                        .first()

                cachedAuthToken =
                    token

                if (
                    !token.isNullOrBlank()
                ) {

                    checkAndRequestPermissions()
                }
            }

            val meshState by
            bleMeshManager
                .meshState
                .collectAsStateWithLifecycle()

            LaunchedEffect(
                meshState.needsPermissionsPrompt
            ) {

                if (
                    meshState.needsPermissionsPrompt &&
                    !cachedAuthToken.isNullOrEmpty()
                ) {

                    checkAndRequestPermissions()

                    bleMeshManager
                        .resetPermissionsPrompt()
                }
            }

            LaunchedEffect(
                meshState.needsBluetoothEnable
            ) {

                if (
                    meshState.needsBluetoothEnable &&
                    !cachedAuthToken.isNullOrEmpty()
                ) {

                    requestBluetoothEnable()

                    bleMeshManager
                        .resetBluetoothEnablePrompt()
                }
            }

            /*
             * Draw immediately.
             *
             * NO isAuthReady.
             * NO blank branch.
             * NO second loader.
             */
            AGAPTheme(
                darkTheme =
                    isDarkMode
            ) {

                AgapNavGraph(

                    startDestination =
                        initialDestination,

                    onSosClick = {

                        Timber.d(
                            "CRITICAL: SOS ACTION TRIGGERED FROM NAV BAR"
                        )

                        bleMeshManager
                            .ensureBluetoothEnabled()

                        if (
                            !hasCorePermissions()
                        ) {

                            checkAndRequestPermissions()
                        }
                    },

                    modifier =
                        Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {

        super.onResume()

        lifecycleScope.launch {

            val token =
                authTokenManager
                    .authToken
                    .first()

            cachedAuthToken =
                token

            if (
                !token.isNullOrBlank() &&
                hasCorePermissions()
            ) {

                startBleMeshForegroundService()
            }
        }
    }

    /**
     * Permissions required for the BLE relay itself.
     *
     * Android 12+ uses the Nearby Devices runtime permissions. GPS/location is
     * intentionally NOT part of this gate; location is required when creating an
     * SOS fix, but a phone must still be able to relay somebody else's BLE packet.
     * Android 11 and lower require fine location for BLE scanning.
     */
    fun hasCorePermissions(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val advertiseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            scanGranted && advertiseGranted && connectGranted
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        BleMeshLog.d(
            "BLE_PERMISSION_GATE",
            "sdk" to Build.VERSION.SDK_INT,
            "granted" to granted
        )
        return granted
    }

    private fun startBleMeshForegroundService() {

        try {

            val serviceIntent =
                Intent(
                    this,
                    BleMeshService::class.java
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                startForegroundService(
                    serviceIntent
                )

            } else {

                startService(
                    serviceIntent
                )
            }

            BleMeshLog.i("SERVICE_START_REQUESTED_FROM_ACTIVITY")
            bleMeshManager
                .startMeshServices()

            startResponderTelemetryService()

            com.travelingsalesman.agap.worker
                .SyncWorker
                .schedulePeriodicSync(
                    this
                )

            Timber.i(
                "BLE Mesh and periodic sync started."
            )

        } catch (e: Exception) {

            Timber.e(
                e,
                "Failed to start BLE Mesh."
            )
        }
    }

    private fun startResponderTelemetryService() {
        lifecycleScope.launch {
            val role = authTokenManager.userRole.first()
            if (role?.uppercase() == "RESPONDER" || role?.uppercase() == "ADMIN") {
                try {
                    val telemetryIntent = Intent(this@MainActivity, ResponderTelemetryService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(telemetryIntent)
                    } else {
                        startService(telemetryIntent)
                    }
                    Timber.i("Responder Telemetry Service started.")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start Responder Telemetry Service.")
                }
            }
        }
    }

    fun requestBluetoothEnable() {

        try {

            val intent =
                Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE
                )

            enableBluetoothLauncher.launch(
                intent
            )

        } catch (e: Exception) {

            Timber.e(
                e,
                "Unable to request Bluetooth enable."
            )
        }
    }

    private fun checkAndRequestBluetoothEnable() {

        if (
            !bleMeshManager
                .meshState
                .value
                .isEnabled
        ) {

            requestBluetoothEnable()
        }
    }

    fun checkAndRequestPermissions() {

        lifecycleScope.launch {

            val token =
                authTokenManager
                    .authToken
                    .first()

            cachedAuthToken =
                token

            /*
             * Don't request BLE permissions
             * while logged out.
             */
            if (
                token.isNullOrBlank()
            ) {

                return@launch
            }

            val permissions =
                mutableListOf<String>()

            permissions.add(
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            permissions.add(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
            ) {

                permissions.add(
                    Manifest.permission.BLUETOOTH_SCAN
                )

                permissions.add(
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )

                permissions.add(
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )

                permissions.add(
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            }

            val missingPermissions =
                permissions.filter { permission ->

                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        permission
                    ) !=
                            PackageManager.PERMISSION_GRANTED
                }

            if (
                missingPermissions.isNotEmpty()
            ) {
                BleMeshLog.i(
                    "PERMISSION_REQUEST",
                    "count" to missingPermissions.size,
                    "permissions" to missingPermissions.joinToString(",") { it.substringAfterLast('.') }
                )
                requestPermissionsLauncher.launch(
                    missingPermissions.toTypedArray()
                )

            } else {

                checkAndRequestBluetoothEnable()

                startBleMeshForegroundService()
            }
        }
    }

    companion object {

        const val EXTRA_START_DESTINATION =
            "agap_start_destination"

        const val EXTRA_INITIAL_DARK_MODE =
            "agap_initial_dark_mode"
    }
}