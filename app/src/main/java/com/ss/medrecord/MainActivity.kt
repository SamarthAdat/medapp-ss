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
}
