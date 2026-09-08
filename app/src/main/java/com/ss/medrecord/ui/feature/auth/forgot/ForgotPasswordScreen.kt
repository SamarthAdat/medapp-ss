package com.ss.medrecord.ui.feature.auth.forgot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.NoticeCard
import com.ss.medrecord.ui.feature.auth.AuthScaffold
import com.ss.medrecord.ui.feature.auth.AuthTextField
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

@Composable
fun ForgotPasswordRoute(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ForgotPasswordEffect.NavigateBack -> onNavigateBack()
                is ForgotPasswordEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ForgotPasswordScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEvent: (ForgotPasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MedTheme.colors
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AuthScaffold(
            title = "Reset password",
            subtitle = "We will email you a link to set a new password.",
            modifier = Modifier.padding(innerPadding),
        ) {
            AuthTextField(
                value = state.email,
                onValueChange = { onEvent(ForgotPasswordEvent.EmailChanged(it)) },
                label = "Email",
                errorMessage = state.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                enabled = !state.isSubmitting,
            )

            if (state.isSent) {
                Spacer(Modifier.height(14.dp))
                NoticeCard(
                    icon = MedIcons.Check,
                    accent = colors.jade,
                    title = "Check your inbox",
                    text = "If an account exists for that address, a reset link is " +
                        "on its way. Check your spam folder too.",
                )
            }

            Spacer(Modifier.height(18.dp))
            MedPrimaryButton(
                text = "Send reset link",
                onClick = { onEvent(ForgotPasswordEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )

            Spacer(Modifier.height(22.dp))
            Text(
                text = "Back to sign in",
                style = MaterialTheme.typography.titleSmall,
                color = colors.jadeSoft,
                modifier = Modifier.clickable { onEvent(ForgotPasswordEvent.BackToSignIn) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordScreenPreview() {
    MedRecordTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordUiState(email = "user@example.com", isSent = true),
            onEvent = {},
        )
    }
}
