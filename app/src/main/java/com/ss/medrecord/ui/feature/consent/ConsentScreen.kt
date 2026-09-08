package com.ss.medrecord.ui.feature.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.SectionLabel
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography

@Composable
fun ConsentRoute(
    onConsentGranted: () -> Unit,
    onDeclined: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ConsentEffect.ConsentGranted -> onConsentGranted()
                ConsentEffect.Declined -> onDeclined()
                is ConsentEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ConsentScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun ConsentScreen(
    state: ConsentUiState,
    onEvent: (ConsentEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MedTheme.colors
    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = if (state.isReconsent) "Updated consent" else "Before you begin",
                actions = {
                    // How many of the three are accepted, without a progress bar
                    // implying the user is being moved through a funnel.
                    Text(
                        text = "${state.acceptedCount}/${ConsentTexts.clauses.size}",
                        style = MedTypography.monoCaption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.isReconsent) {
                    "We have updated how we describe the handling of your records. " +
                        "Please review and accept to continue."
                } else {
                    "Nothing is stored until you accept."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            ConsentTexts.clauses.forEach { clause ->
                ConsentClauseCard(
                    title = clause.title,
                    body = clause.body,
                    checked = state.isAccepted(clause.type),
                    enabled = !state.isSubmitting,
                    onCheckedChange = { checked ->
                        onEvent(ConsentEvent.ToggleConsent(clause.type, checked))
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ComplianceBadge(icon = MedIcons.Lock, headline = "Encrypted", detail = "at rest")
                ComplianceBadge(icon = MedIcons.VerifiedUser, headline = "DPDP Act", detail = "2023")
                ComplianceBadge(
                    icon = MedIcons.Schedule,
                    headline = "${AppConstants.SOFT_DELETE_GRACE_PERIOD_DAYS}-day",
                    detail = "erasure",
                )
            }

            SectionLabel(
                text = "Consent version ${state.version}",
                modifier = Modifier.padding(top = 8.dp),
            )

            MedPrimaryButton(
                text = if (state.canSubmit) "I accept" else "Accept all three to continue",
                onClick = { onEvent(ConsentEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )

            Text(
                text = "Decline and sign out",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isSubmitting) {
                        onEvent(ConsentEvent.Decline)
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/** One of the three reassurances that close the consent gate. */
@Composable
private fun RowScope.ComplianceBadge(
    icon: com.ss.medrecord.ui.theme.MedIcon,
    headline: String,
    detail: String,
) {
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp),
    ) {
        MedIconGlyph(icon = icon, size = 18.dp, tint = colors.jade, contentDescription = null)
        Text(
            text = headline,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ConsentClauseCard(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        // The whole card is one toggle target, so the checkbox itself is
        // merged away from the accessibility tree rather than announced twice.
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) colors.jade.copy(alpha = 0.10f) else colors.card)
            .border(
                width = 1.dp,
                color = if (checked) colors.jade.copy(alpha = 0.35f) else colors.hairline,
                shape = shape,
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // A filled tick rather than a Material checkbox: the accepted state has
        // to be unmistakable at a glance on a screen whose whole purpose is
        // that nobody accepts something by accident.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) colors.jade else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checked) colors.jade else colors.hairlineStrong,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                MedIconGlyph(
                    icon = MedIcons.Check,
                    size = 16.dp,
                    tint = colors.onJade,
                    contentDescription = null,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ConsentScreenPreview() {
    MedRecordTheme {
        ConsentScreen(state = ConsentUiState(version = 1), onEvent = {})
    }
}
