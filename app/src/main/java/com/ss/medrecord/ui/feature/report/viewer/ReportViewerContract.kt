package com.ss.medrecord.ui.feature.report.viewer

import androidx.compose.ui.graphics.ImageBitmap
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Report

/**
 * The full-screen report viewer (spec section 5.8).
 *
 * Images and PDFs share one state: both end up as a list of bitmaps, a
 * single-page one for an image and a page per sheet for a PDF, so the screen
 * has one thing to render rather than two branches to keep in step.
 */
data class ReportViewerUiState(
    val report: Report? = null,
    val pages: List<ImageBitmap> = emptyList(),
    val isLoading: Boolean = true,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
) : UiState {
    val hasContent: Boolean get() = pages.isNotEmpty()
    val isMultiPage: Boolean get() = pages.size > 1
}

sealed interface ReportViewerEvent : UiEvent {
    data object BackClicked : ReportViewerEvent
    data object RetryClicked : ReportViewerEvent

    /**
     * Rendering a PDF needs the width it will be drawn at, which only the
     * layout knows. The screen reports it once measured.
     */
    data class ViewportMeasured(val widthPx: Int) : ReportViewerEvent
}

sealed interface ReportViewerEffect : UiEffect {
    data object NavigateBack : ReportViewerEffect
}
