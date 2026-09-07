package com.ss.medrecord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One medicine in a list.
 *
 * A paused course stays visible rather than being hidden or moved to a second
 * screen - people stop and restart medication constantly, and a record that
 * disappears when it is paused is a record the user stops trusting. It is
 * dimmed and labelled instead, and the switch is the way back.
 */
@Composable
fun MedicineTile(
    medicine: Medicine,
    subtitle: String?,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = !medicine.isActive

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (muted) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        color = if (muted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    medicine.dosage?.takeIf { it.isNotBlank() }?.let { dosage ->
                        Text(
                            text = "  $dosage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = medicine.scheduleSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )

                Text(
                    text = courseLine(medicine),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Switch(
                checked = medicine.isActive,
                onCheckedChange = onToggleActive,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** "From 3 Mar 2026", or a closed range once the course has an end. */
private fun courseLine(medicine: Medicine): String {
    val start = medicine.startDate.format(DATE_FORMAT)
    val end = medicine.endDate?.format(DATE_FORMAT)
    return when {
        !medicine.isActive -> if (end == null) "Paused · from $start" else "Paused · $start to $end"
        end == null -> "From $start · ongoing"
        else -> "$start to $end"
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Preview(showBackground = true)
@Composable
private fun MedicineTilePreview() {
    MedRecordTheme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicineTile(
                medicine = Medicine(
                    medicineId = "m1",
                    userId = "u1",
                    patientId = "p1",
                    name = "Metformin",
                    dosage = "500 mg",
                    frequency = MedicineFrequency.DAILY,
                    reminderTimes = listOf(8 * 60, 20 * 60),
                    startDateEpochDay = LocalDate.now().minusDays(30).toEpochDay(),
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                subtitle = "Asha Rao · City Care Clinic",
                onClick = {},
                onToggleActive = {},
            )
            MedicineTile(
                medicine = Medicine(
                    medicineId = "m2",
                    userId = "u1",
                    patientId = "p1",
                    name = "Ibuprofen",
                    dosage = "200 mg",
                    frequency = MedicineFrequency.AS_NEEDED,
                    startDateEpochDay = LocalDate.now().minusDays(4).toEpochDay(),
                    isActive = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                subtitle = "Asha Rao",
                onClick = {},
                onToggleActive = {},
            )
        }
    }
}
