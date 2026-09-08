package com.ss.medrecord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ss.medrecord.ui.theme.MedTheme
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
            color = MedTheme.colors.canvas,
            fontWeight = FontWeight.Bold,
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
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.jade.copy(alpha = 0.14f))
            .border(1.dp, colors.jade.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (patient != null) {
            PatientAvatar(patient = patient, size = 26.dp)
        }
        Text(
            text = patient?.name?.substringBefore(' ') ?: "Select patient",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

/**
 * The four accents, used as identity colours here rather than as meanings.
 *
 * Only four, and always in the same order: a palette this small means two
 * profiles in a family of three are unlikely to collide, and a stable order
 * means a profile keeps its colour when another is added or removed.
 */
@Composable
private fun avatarPalette(): List<Color> = MedTheme.colors.let { colors ->
    listOf(colors.jade, colors.azure, colors.violet, colors.amber)
}

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
