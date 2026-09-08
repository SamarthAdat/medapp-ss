package com.ss.medrecord.ui.feature.auth.signup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.feature.auth.AuthScaffold
import com.ss.medrecord.ui.feature.auth.OfflineNote
import com.ss.medrecord.ui.feature.auth.AuthTextField
import com.ss.medrecord.ui.feature.auth.PasswordTextField
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

@Composable
fun SignUpRoute(
    onAccountCreated: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SignUpEffect.AccountCreated -> onAccountCreated()
                SignUpEffect.NavigateToSignIn -> onNavigateToSignIn()
                is SignUpEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SignUpScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun SignUpScreen(
    state: SignUpUiState,
    onEvent: (SignUpEvent) -> Unit,
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
            title = "Create account",
            subtitle = "One account holds records for you and your family.",
            modifier = Modifier.padding(innerPadding),
        ) {
            AuthTextField(
                value = state.name,
                onValueChange = { onEvent(SignUpEvent.NameChanged(it)) },
                label = "Full name",
                errorMessage = state.nameError,
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = state.email,
                onValueChange = { onEvent(SignUpEvent.EmailChanged(it)) },
                label = "Email",
                errorMessage = state.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(12.dp))
            PasswordTextField(
                value = state.password,
                onValueChange = { onEvent(SignUpEvent.PasswordChanged(it)) },
                label = "Password",
                errorMessage = state.passwordError,
                enabled = !state.isSubmitting,
            )

            Spacer(Modifier.height(12.dp))
            PasswordTextField(
                value = state.confirmPassword,
                onValueChange = { onEvent(SignUpEvent.ConfirmPasswordChanged(it)) },
                label = "Confirm password",
                errorMessage = state.confirmPasswordError,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting,
            )

            Text(
                text = "You will be asked to review and accept the data-processing " +
                    "consent before adding any records.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 14.dp),
            )

            Spacer(Modifier.height(14.dp))
            MedPrimaryButton(
                text = "Create account",
                onClick = { onEvent(SignUpEvent.Submit) },
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
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.jadeSoft,
                    modifier = Modifier.clickable { onEvent(SignUpEvent.SignInClicked) },
                )
            }

            Spacer(Modifier.height(30.dp))
            OfflineNote()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignUpScreenPreview() {
    MedRecordTheme {
        SignUpScreen(state = SignUpUiState(name = "Asha"), onEvent = {})
    }
}
