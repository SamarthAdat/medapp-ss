package com.ss.medrecord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One row of merged history.
 *
 * The kind is carried by a small lettered dot rather than an icon set. Three
 * kinds do not justify pulling in an icon dependency, and a single letter in a
 * tinted circle is legible at a glance and readable by a screen reader without
 * a content description that repeats the label below it.
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPatientName: Boolean = false,
    showDate: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KindDot(kind = entry.kind)

        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        if (showDate) {
            Text(
                text = entry.date.format(ROW_DATE_FORMAT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun KindDot(kind: TimelineKind) {
    val colour = when (kind) {
        TimelineKind.VISIT -> MaterialTheme.colorScheme.primary
        TimelineKind.REPORT -> MaterialTheme.colorScheme.tertiary
        TimelineKind.MEDICINE -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = CircleShape,
        color = colour,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = kind.label.take(1),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
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

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 2.dp),
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
