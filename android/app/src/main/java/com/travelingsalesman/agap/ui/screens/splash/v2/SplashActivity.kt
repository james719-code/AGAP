package com.travelingsalesman.agap.ui.screens.splash.v2

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.databinding.ActivitySplashBinding
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    private lateinit var binding: ActivitySplashBinding

    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)

        configureSystemBars()

        binding =
            ActivitySplashBinding.inflate(layoutInflater)

        setContentView(binding.root)

        applySystemBarInsets()

        prepareInitialState()

        playAnimationTimeline()

        /*
         * Resolve authentication WHILE splash is visible.
         *
         * MainActivity will therefore already know exactly
         * what screen it should render.
         */
        lifecycleScope.launch {

            val startedAt =
                SystemClock.elapsedRealtime()

            var destination =
                AgapDestinations.LOGIN_ROUTE

            var darkMode = true

            try {

                val token =
                    authTokenManager
                        .authToken
                        .first()

                val role =
                    authTokenManager
                        .userRole
                        .first()

                darkMode =
                    authTokenManager
                        .isDarkMode
                        .first()

                val isLoggedIn =
                    !token.isNullOrBlank()

                destination =
                    when {

                        !isLoggedIn -> {
                            AgapDestinations.LOGIN_ROUTE
                        }

                        role.equals(
                            "ADMIN",
                            ignoreCase = true
                        ) -> {
                            AgapDestinations.ADMIN_MOBILE_ROUTE
                        }

                        role.equals(
                            "RESPONDER",
                            ignoreCase = true
                        ) -> {
                            AgapDestinations.RESPONDER_MAP_ROUTE
                        }

                        else -> {
                            AgapDestinations.HOME_ROUTE
                        }
                    }

                Timber.d(
                    "Splash resolved route=$destination role=$role"
                )

            } catch (e: Exception) {

                Timber.e(
                    e,
                    "Unable to resolve startup route"
                )

                destination =
                    AgapDestinations.LOGIN_ROUTE
            }

            /*
             * Allow a brief branding fade while preferences load, rather than
             * adding the former 1.9-second hold to every cold launch.
             */
            val elapsed =
                SystemClock.elapsedRealtime() -
                        startedAt

            val remaining =
                MIN_VISIBLE_TIME_MS -
                        elapsed

            if (remaining > 0L) {

                delay(remaining)
            }

            navigateToMain(
                startDestination = destination,
                darkMode = darkMode
            )
        }
    }

    private fun configureSystemBars() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.TRANSPARENT

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {

            isAppearanceLightStatusBars = true

            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemBarInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.splashRoot
        ) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                0,
                bars.top,
                0,
                bars.bottom
            )

            insets
        }
    }

    private fun prepareInitialState() =
        with(binding) {

            logoView.apply {

                alpha = 0f

                scaleX = 0.90f

                scaleY = 0.90f

                translationY =
                    dp(14f)
            }

            loadingSection.apply {

                alpha = 0f

                translationY =
                    dp(8f)
            }

            waveView.alpha =
                0f
        }

    private fun playAnimationTimeline() =
        with(binding) {

            waveView
                .animate()
                .alpha(1f)
                .setStartDelay(0L)
                .setDuration(350L)
                .setInterpolator(
                    AccelerateDecelerateInterpolator()
                )
                .start()

            logoView
                .animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(0L)
                .setDuration(300L)
                .setInterpolator(
                    AccelerateDecelerateInterpolator()
                )
                .start()

            loadingSection
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(320L)
                .setDuration(180L)
                .setInterpolator(
                    AccelerateDecelerateInterpolator()
                )
                .start()
        }

    private fun navigateToMain(
        startDestination: String,
        darkMode: Boolean
    ) {

        if (
            hasNavigated ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        hasNavigated = true

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                /*
                 * Pass startup information.
                 */
                putExtra(
                    MainActivity.EXTRA_START_DESTINATION,
                    startDestination
                )

                putExtra(
                    MainActivity.EXTRA_INITIAL_DARK_MODE,
                    darkMode
                )

                /*
                 * Preserve original deep-link data.
                 */
                data =
                    this@SplashActivity
                        .intent
                        ?.data
            }

        /*
         * IMPORTANT:
         *
         * Do NOT use fade_in / fade_out.
         *
         * That transition was exposing the white
         * MainActivity window before Compose drew.
         */
        startActivity(intent)

        /*
         * Disable Activity transition entirely.
         */
        // MainActivity now paints its window in the theme colour before its
        // first frame, so a short crossfade no longer exposes a blank window.
        @Suppress("DEPRECATION")
        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }

    private fun dp(
        value: Float
    ): Float {

        return value *
                resources.displayMetrics.density
    }

    private companion object {

        const val MIN_VISIBLE_TIME_MS =
            600L
    }
}
