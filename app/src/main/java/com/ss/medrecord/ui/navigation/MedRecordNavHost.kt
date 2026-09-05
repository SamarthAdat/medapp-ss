package com.ss.medrecord.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.ui.feature.auth.forgot.ForgotPasswordRoute
import com.ss.medrecord.ui.feature.auth.login.LoginRoute
import com.ss.medrecord.ui.feature.auth.signup.SignUpRoute
import com.ss.medrecord.ui.feature.consent.ConsentRoute
import com.ss.medrecord.ui.feature.home.HomeRoute
import com.ss.medrecord.ui.feature.patient.list.PatientListScreen
import com.ss.medrecord.ui.feature.settings.SettingsRoute
import com.ss.medrecord.ui.feature.splash.SplashScreen

/**
 * Single NavHost for the app.
 *
 * Which area the user is in is not decided by individual screens - it is driven
 * entirely by [AuthSession]. Signing in, signing out, or a consent version bump
 * changes the session, and this host relocates the user accordingly, clearing
 * the back stack so no gesture can walk back into an area the session no longer
 * permits.
 */
@Composable
fun MedRecordNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    LaunchedEffect(session) {
        when (session) {
            // Still resolving; the splash destination stays put.
            AuthSession.Unknown -> Unit

            AuthSession.SignedOut ->
                navController.navigate(AuthGraph) { clearBackStack(navController) }

            is AuthSession.PendingConsent ->
                navController.navigate(ConsentDestination) { clearBackStack(navController) }

            is AuthSession.Authenticated ->
                navController.navigate(MainGraph) { clearBackStack(navController) }
        }
    }

    NavHost(
        navController = navController,
        startDestination = SplashDestination,
        modifier = modifier,
    ) {
        composable<SplashDestination> {
            SplashScreen()
        }

        // The consent gate sits outside both graphs: it is reachable only from
        // the session state, and it has no route into the main graph of its own.
        composable<ConsentDestination> {
            ConsentRoute(
                // Both outcomes change the session, which relocates the user
                // through the effect above. Nothing to navigate to here.
                onConsentGranted = {},
                onDeclined = {},
            )
        }

        navigation<AuthGraph>(startDestination = LoginDestination) {
            composable<LoginDestination> {
                LoginRoute(
                    onSignedIn = {},
                    onNavigateToSignUp = { navController.navigate(SignUpDestination) },
                    onNavigateToForgotPassword = {
                        navController.navigate(ForgotPasswordDestination)
                    },
                )
            }
            composable<SignUpDestination> {
                SignUpRoute(
                    onAccountCreated = {},
                    onNavigateToSignIn = { navController.popBackStack() },
                )
            }
            composable<ForgotPasswordDestination> {
                ForgotPasswordRoute(onNavigateBack = { navController.popBackStack() })
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
                SettingsRoute(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Drops the entire back stack. Used on every session transition so that, for
 * example, back from the login screen after signing out cannot resurface a
 * screen full of the previous session's records.
 *
 * This pops the root graph itself rather than its start destination. Popping to
 * the start destination only works while that destination is still on the
 * stack - once the splash screen has been popped inclusively, the same call
 * silently does nothing and the new destination is pushed on top of the old
 * session's screens, leaving them one back gesture away.
 */
private fun NavOptionsBuilder.clearBackStack(navController: NavHostController) {
    popUpTo(navController.graph.id) { inclusive = true }
    launchSingleTop = true
}
