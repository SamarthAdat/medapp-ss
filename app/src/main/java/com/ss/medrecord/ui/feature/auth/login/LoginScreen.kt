package com.ss.medrecord.ui.feature.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.feature.auth.AuthScaffold
import com.ss.medrecord.ui.feature.auth.AuthTextField
import com.ss.medrecord.ui.feature.auth.OfflineNote
import com.ss.medrecord.ui.feature.auth.PasswordTextField
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

@Composable
fun LoginRoute(
    onSignedIn: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                LoginEffect.SignedIn -> onSignedIn()
                LoginEffect.NavigateToSignUp -> onNavigateToSignUp()
                LoginEffect.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    LoginScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MedTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // A transparent container leaves the content colour unspecified, and
        // Text draws that in black - invisible on the dark canvas.
        contentColor = colors.textPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AuthScaffold(
            title = "Welcome back",
            subtitle = "Your family's records, kept on this device and encrypted " +
                "before they go anywhere.",
            modifier = Modifier.padding(innerPadding),
        ) {
            AuthTextField(
                value = state.email,
                onValueChange = { onEvent(LoginEvent.EmailChanged(it)) },
                label = "Email",
                errorMessage = state.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(12.dp))
            PasswordTextField(
                value = state.password,
                onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                label = "Password",
                errorMessage = state.passwordError,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(18.dp))
            MedPrimaryButton(
                text = "Sign in",
                onClick = { onEvent(LoginEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                icon = MedIcons.ArrowForward,
            )

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.clickable {
                        onEvent(LoginEvent.ForgotPasswordClicked)
                    },
                )
                Text(
                    text = "Create account",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.jadeSoft,
                    modifier = Modifier.clickable { onEvent(LoginEvent.SignUpClicked) },
                )
            }

            Spacer(Modifier.height(34.dp))
            OfflineNote()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MedRecordTheme {
        LoginScreen(state = LoginUiState(email = "user@example.com"), onEvent = {})
    }
}
