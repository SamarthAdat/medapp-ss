package com.ss.medrecord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.ui.theme.MedIcon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One row of merged history.
 *
 * The kind is carried by a tinted icon tile, using the accent that kind owns
 * everywhere else in the app - jade for a visit, azure for a report, violet
 * for a medicine. Someone who has learned the colours on the dashboard can
 * read this list without reading the labels at all.
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPatientName: Boolean = false,
    showDate: Boolean = true,
) {
    val colors = MedTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = entry.kind.icon,
            accent = entry.kind.accent(),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
            )

            val details = buildList {
                add(entry.kind.label)
                if (showPatientName) entry.patientName?.let(::add)
                entry.subtitle?.takeIf { it.isNotBlank() }?.let(::add)
            }
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }

        if (showDate) {
            Text(
                text = entry.date.format(ROW_DATE_FORMAT),
                style = MedTypography.monoCaption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The glyph each kind is drawn with, in one place so no screen picks its own. */
val TimelineKind.icon: MedIcon
    get() = when (this) {
        TimelineKind.VISIT -> MedIcons.Stethoscope
        TimelineKind.REPORT -> MedIcons.Description
        TimelineKind.MEDICINE -> MedIcons.Medication
    }

/** And the accent, which is the same one the dashboard counts use. */
@Composable
fun TimelineKind.accent(): Color = when (this) {
    TimelineKind.VISIT -> MedTheme.colors.jade
    TimelineKind.REPORT -> MedTheme.colors.azure
    TimelineKind.MEDICINE -> MedTheme.colors.violet
}

/** The sticky-ish header above each day's entries on the timeline screen. */
@Composable
fun TimelineDateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(HEADER_DATE_FORMAT)
    }

    SectionLabel(
        text = label,
        color = MedTheme.colors.textTertiary,
        modifier = modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

private val ROW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Preview(showBackground = true)
@Composable
private fun TimelineRowPreview() {
    MedRecordTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimelineDateHeader(date = LocalDate.now())
            TimelineRow(
                entry = TimelineEntry(
                    id = "VISIT:v1",
                    kind = TimelineKind.VISIT,
                    targetId = "v1",
                    patientId = "p1",
                    patientName = "Asha Rao",
                    title = "City Care Clinic",
                    subtitle = "Dr Mehta",
                    onEpochDay = LocalDate.now().toEpochDay(),
                    recordedAtMillis = 0L,
                ),
                onClick = {},
                showPatientName = true,
            )
            TimelineRow(
                entry = TimelineEntry(
                    id = "REPORT:r1",
                    kind = TimelineKind.REPORT,
                    targetId = "r1",
                    patientId = "p1",
                    patientName = "Asha Rao",
                    title = "blood-panel.pdf",
                    subtitle = "City Care Clinic · 726 KB",
                    onEpochDay = LocalDate.now().toEpochDay(),
                    recordedAtMillis = 0L,
                ),
                onClick = {},
                showPatientName = true,
            )
            TimelineRow(
                entry = TimelineEntry(
                    id = "MEDICINE:m1",
                    kind = TimelineKind.MEDICINE,
                    targetId = "m1",
                    patientId = "p1",
                    patientName = "Asha Rao",
                    title = "Metformin",
                    subtitle = "500 mg · Every day",
                    onEpochDay = LocalDate.now().toEpochDay(),
                    recordedAtMillis = 0L,
                ),
                onClick = {},
                showPatientName = true,
            )
        }
    }
}
