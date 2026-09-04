package com.ss.medrecord.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.ss.medrecord.ui.feature.auth.login.LoginScreen
import com.ss.medrecord.ui.feature.home.HomeRoute
import com.ss.medrecord.ui.feature.patient.list.PatientListScreen
import com.ss.medrecord.ui.feature.settings.SettingsScreen
import com.ss.medrecord.ui.feature.splash.SplashScreen

/**
 * Single NavHost for the app. Auth and main flows are separate nested graphs so
 * that signing out can pop the entire authenticated back stack in one call.
 */
@Composable
fun MedRecordNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SplashDestination,
        modifier = modifier,
    ) {
        composable<SplashDestination> {
            SplashScreen(
                onSessionResolved = {
                    // Phase 1 decides between AuthGraph and MainGraph from the
                    // real session + consent state.
                    navController.navigate(AuthGraph) {
                        popUpTo(SplashDestination) { inclusive = true }
                    }
                },
            )
        }

        navigation<AuthGraph>(startDestination = LoginDestination) {
            composable<LoginDestination> {
                LoginScreen(
                    onSignedIn = {
                        navController.navigate(MainGraph) {
                            popUpTo(AuthGraph) { inclusive = true }
                        }
                    },
                )
            }
        }

        navigation<MainGraph>(startDestination = HomeDestination) {
            composable<HomeDestination> {
                HomeRoute(
                    onNavigateToPatients = { navController.navigate(PatientListDestination) },
                    onNavigateToSettings = { navController.navigate(SettingsDestination) },
                )
            }
            composable<PatientListDestination> {
                PatientListScreen()
            }
            composable<SettingsDestination> {
                SettingsScreen()
            }
        }
    }
}
