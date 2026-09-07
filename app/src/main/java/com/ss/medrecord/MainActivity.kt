package com.ss.medrecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ss.medrecord.ui.navigation.MedRecordNavHost
import com.ss.medrecord.ui.theme.MedRecordTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host. Every screen is a Compose destination inside
 * [MedRecordNavHost].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedRecordTheme {
                MedRecordNavHost()
            }
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
