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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography

/**
 * How a report describes its own upload state.
 *
 * UPLOADED is deliberately silent. Once the bytes are safe there is nothing for
 * the user to do, and a grid where every tile shouts "Uploaded" makes the two
 * tiles that do need attention harder to find.
 */
@Composable
fun UploadStatusChip(status: UploadStatus, modifier: Modifier = Modifier) {
    val colors = MedTheme.colors
    val (label, accent) = when (status) {
        UploadStatus.UPLOADED -> return
        UploadStatus.PENDING -> "Queued" to colors.amber
        UploadStatus.UPLOADING -> "Uploading" to colors.azure
        UploadStatus.FAILED -> "Upload failed" to colors.coral
        UploadStatus.REJECTED_SIZE_LIMIT -> "Too large" to colors.coral
    }

    StatusPill(text = label, accent = accent, modifier = modifier)
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

    val colors = MedTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.hairline, shape)
            .clickable(enabled = report.isViewable || report.isRejected) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .background(
                    // A faint wash in the kind's own accent, so a tile with no
                    // thumbnail still reads as a file rather than as a hole.
                    colors.azure.copy(alpha = if (colors.isDark) 0.16f else 0.10f),
                ),
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
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
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
                    style = MedTypography.monoMicro,
                    color = colors.textTertiary,
                )
                UploadStatusChip(status = report.uploadStatus)
            }
        }
    }
}

@Composable
private fun FileTypeBadge(report: Report) {
    val colors = MedTheme.colors
    val accent: Color = if (report.isRejected) colors.coral else colors.azure
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MedIconGlyph(
            icon = when {
                report.isRejected -> MedIcons.PriorityHigh
                report.fileType == ReportFileType.PDF -> MedIcons.PictureAsPdf
                else -> MedIcons.Image
            },
            size = 30.dp,
            tint = accent,
            contentDescription = null,
        )
        if (!report.isAvailableOffline && !report.isRejected) {
            Text(
                text = "Tap to open",
                style = MedTypography.monoMicro,
                color = colors.textTertiary,
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
