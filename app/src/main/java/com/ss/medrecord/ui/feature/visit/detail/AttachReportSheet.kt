package com.ss.medrecord.ui.feature.visit.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.validation.ReportValidator

/**
 * Where a report comes from: the photo picker, the document picker, or the
 * camera.
 *
 * All three are system pickers, which is the point. The app never asks for
 * READ_MEDIA_IMAGES or CAMERA: the user chooses one file in a system UI and the
 * app receives a grant for that file alone, so an app holding medical records
 * never gains standing access to the photo library or the camera.
 *
 * Every launch is guarded. A picker is another app, and there is no guarantee
 * one exists: a device can ship without a camera app, and the system photo
 * picker is missing or broken on some builds - which throws
 * ActivityNotFoundException from `launch` and takes the process down with it.
 * The photo picker degrades to the document picker, which is backed by the
 * documents UI every Android build has; the others say plainly that the device
 * cannot do it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachReportSheet(
    onFileSelected: (Uri) -> Unit,
    onDismiss: () -> Unit,
    createCaptureUri: () -> Uri,
    onPickerUnavailable: (String) -> Unit,
) {
    // Held so the result callback knows where the camera was told to write;
    // TakePicture hands back only a success flag.
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onFileSelected) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFileSelected) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) captureUri?.let(onFileSelected) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = "Attach a report",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = "Images over ${ReportValidator.maxSizeLabel} are compressed " +
                    "automatically. PDFs must already be under the limit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            SheetOption(
                title = "Choose a photo",
                description = "From the device photo picker",
                onClick = {
                    val launched = runCatching {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }.isSuccess
                    // No photo picker on this build. The documents UI can pick
                    // an image just as well, so the user gets on with it rather
                    // than being told their device is deficient.
                    if (!launched) {
                        runCatching { documentPicker.launch(arrayOf("image/*")) }
                            .onFailure { onPickerUnavailable(NO_FILE_PICKER) }
                    }
                },
            )
            SheetOption(
                title = "Choose a document",
                description = "A PDF or image file",
                onClick = {
                    runCatching { documentPicker.launch(arrayOf("application/pdf", "image/*")) }
                        .onFailure { onPickerUnavailable(NO_FILE_PICKER) }
                },
            )
            SheetOption(
                title = "Take a photo",
                description = "Scan a prescription or report with the camera",
                onClick = {
                    val uri = createCaptureUri()
                    captureUri = uri
                    runCatching { camera.launch(uri) }
                        .onFailure { onPickerUnavailable("No camera app is available on this device.") }
                },
            )
        }
    }
}

@Composable
private fun SheetOption(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val NO_FILE_PICKER =
    "No app on this device can pick a file. Try taking a photo instead."
