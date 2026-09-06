package com.ss.medrecord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.ui.theme.MedRecordTheme
import kotlin.math.absoluteValue

/**
 * Initials avatar. Patient profiles carry no photo yet - the file pipeline
 * built in Phase 5 handles report attachments, not avatars; the
 * colour is derived from the patient id so a given profile always looks the
 * same, which makes the switcher scannable without reading names.
 */
@Composable
fun PatientAvatar(
    patient: Patient,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val palette = avatarPalette()
    val background = palette[(patient.patientId.hashCode().absoluteValue) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            // The initials repeat the name shown next to them, so they are
            // hidden from screen readers rather than announced twice.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = patient.initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value / 2.6f).sp,
        )
    }
}

/**
 * The persistent top-bar chip that shows whose records are on screen
 * (spec section 5.2). Tapping it opens the switcher.
 */
@Composable
fun ActivePatientChip(
    patient: Patient?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = patient?.name ?: "Select patient",
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = patient?.let { { PatientAvatar(patient = it, size = 24.dp) } },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

@Composable
private fun avatarPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    Color(0xFF6A4C93),
    Color(0xFF1B7F79),
    Color(0xFFB5651D),
    Color(0xFF4A6FA5),
)

@Preview(showBackground = true)
@Composable
private fun ActivePatientChipPreview() {
    MedRecordTheme {
        ActivePatientChip(
            patient = Patient(
                patientId = "p1",
                userId = "u1",
                name = "Asha Rao",
                relationship = Relationship.SELF,
                createdAt = 0L,
                updatedAt = 0L,
            ),
            onClick = {},
        )
    }
}
