package com.ss.medrecord

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ss.medrecord.core.biometric.BiometricGate
import com.ss.medrecord.core.biometric.BiometricResult
import com.ss.medrecord.domain.model.AppearanceMode
import com.ss.medrecord.ui.feature.lock.AppLockScreen
import com.ss.medrecord.ui.navigation.AppShellViewModel
import com.ss.medrecord.ui.navigation.MedRecordNavHost
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity host. Every screen is a Compose destination inside
 * [MedRecordNavHost].
 *
 * A [FragmentActivity] rather than a ComponentActivity because
 * [androidx.biometric.BiometricPrompt] presents itself through the fragment
 * manager. Nothing else in the app uses fragments, and nothing should - this
 * is the one place the platform still requires one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var biometricGate: BiometricGate

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val shellViewModel: AppShellViewModel = hiltViewModel()
            val appearance by shellViewModel.appearanceMode.collectAsStateWithLifecycle()
            val darkTheme = appearance.resolveDark()

            MedRecordTheme(darkTheme = darkTheme) {
                SystemBarIcons(darkTheme = darkTheme)
                // Paints the whole window, system bars included. Without it the
                // strips behind the status and navigation bars keep the colour
                // from themes.xml, which follows the *device* setting - so
                // choosing Light on a dark phone leaves two dark bands on
                // screen that no screen composable can reach.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MedTheme.colors.canvas),
                ) {
                    AppShell(shellViewModel = shellViewModel)
                }
            }
        }
    }

    /**
     * The lock, and everything it covers.
     *
     * Kept inside the activity because unlocking needs the activity itself -
     * [BiometricPrompt] attaches to its fragment manager - and passing an
     * activity down into a screen composable would be a much worse leak than
     * keeping this one function here.
     */
    @Composable
    private fun AppShell(shellViewModel: AppShellViewModel) {
        val locked by shellViewModel.locked.collectAsStateWithLifecycle()
        val lockError by shellViewModel.lockError.collectAsStateWithLifecycle()

        // Re-lock whenever the app leaves the foreground, so an unlock is good
        // for one visit rather than for the life of the process.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) shellViewModel.onBackgrounded()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // The NavHost stays composed behind the lock rather than being swapped
        // out. Tearing the graph down and rebuilding it on every unlock would
        // reset the back stack and every screen's scroll position.
        MedRecordNavHost()

        if (locked) {
            // Offered automatically the first time, then on demand: a prompt
            // that re-appears the instant it is dismissed cannot be dismissed.
            LaunchedEffect(Unit) { promptUnlock(shellViewModel) }

            AppLockScreen(
                onUnlock = { promptUnlockFromUi(shellViewModel) },
                error = lockError,
            )
        }
    }

    private suspend fun promptUnlock(shellViewModel: AppShellViewModel) {
        when (val result = biometricGate.authenticate(this)) {
            BiometricResult.Succeeded -> shellViewModel.onUnlocked()
            // Backing out is a choice, not a fault. The lock screen stays up
            // with its own button, and says nothing.
            BiometricResult.Cancelled -> shellViewModel.onUnlockFailed(null)
            is BiometricResult.Failed -> shellViewModel.onUnlockFailed(result.message)
        }
    }

    private fun promptUnlockFromUi(shellViewModel: AppShellViewModel) {
        lifecycleScope.launch { promptUnlock(shellViewModel) }
    }

    /**
     * Status and navigation bar icons have to be inverted by hand: the system
     * picks them from the *device* theme, which is not necessarily the theme
     * this app has been told to draw in.
     */
    @Composable
    private fun SystemBarIcons(darkTheme: Boolean) {
        val view = LocalView.current
        DisposableEffect(darkTheme) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            onDispose { }
        }
    }

    companion object {
        /**
         * Which kind of reminder opened the app, set by [ReminderNotifier].
         *
         * Carried but not yet routed on: the navigation graph decides where to
         * put the user from the session, and jumping past that from a
         * notification could land a signed-out or un-consented user on a data
         * screen. Phase 7 gives the dashboard somewhere sensible to send them.
         */
        const val EXTRA_REMINDER_TYPE = "reminder_type"
    }
}

/** The stored preference against what the device is currently doing. */
@Composable
private fun AppearanceMode.resolveDark(): Boolean = when (this) {
    AppearanceMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}
