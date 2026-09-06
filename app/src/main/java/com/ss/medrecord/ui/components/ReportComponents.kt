package com.ss.medrecord.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.UploadStatus
import com.ss.medrecord.domain.model.formatFileSize
import com.ss.medrecord.ui.theme.MedRecordTheme

/**
 * How a report describes its own upload state.
 *
 * UPLOADED is deliberately silent. Once the bytes are safe there is nothing for
 * the user to do, and a grid where every tile shouts "Uploaded" makes the two
 * tiles that do need attention harder to find.
 */
@Composable
fun UploadStatusChip(status: UploadStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        UploadStatus.UPLOADED -> return
        UploadStatus.PENDING -> "Waiting to upload" to MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.UPLOADING -> "Uploading" to MaterialTheme.colorScheme.primary
        UploadStatus.FAILED -> "Upload failed" to MaterialTheme.colorScheme.error
        UploadStatus.REJECTED_SIZE_LIMIT -> "Too large - not saved" to
            MaterialTheme.colorScheme.error
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * A report tile for the grid.
 *
 * [loadThumbnail] is a suspend call rather than a bitmap, so decrypting and
 * decoding happens off the composition and only for tiles actually on screen.
 * It returns null for a report whose bytes are not on this device: fetching a
 * whole file from Cloud Storage just to draw a preview would spend the user's
 * data on something they may never open, so those tiles show the file type and
 * fetch on tap instead.
 */
@Composable
fun ReportTile(
    report: Report,
    subtitle: String?,
    onClick: () -> Unit,
    loadThumbnail: suspend (Report) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, report.reportId) {
        value = if (report.fileType == ReportFileType.IMAGE && report.isAvailableOffline) {
            loadThumbnail(report)
        } else {
            null
        }
    }

    Card(
        modifier = modifier.clickable(enabled = report.isViewable || report.isRejected) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FileTypeBadge(report = report)
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = report.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatFileSize(report.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UploadStatusChip(status = report.uploadStatus)
            }
        }
    }
}

@Composable
private fun FileTypeBadge(report: Report) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = report.fileType.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (report.isRejected) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (!report.isAvailableOffline && !report.isRejected) {
            Text(
                text = "Tap to open",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun ReportTilePreview() {
    MedRecordTheme {
        ReportTile(
            report = Report(
                reportId = "r1",
                userId = "u1",
                patientId = "p1",
                visitId = "v1",
                fileName = "blood-panel.pdf",
                fileType = ReportFileType.PDF,
                fileSizeBytes = 743_012,
                uploadStatus = UploadStatus.FAILED,
                createdAt = 0L,
                updatedAt = 0L,
            ),
            subtitle = "City Care Clinic - 3 Mar 2026",
            onClick = {},
            loadThumbnail = { null },
            modifier = Modifier.padding(8.dp),
        )
    }
}
