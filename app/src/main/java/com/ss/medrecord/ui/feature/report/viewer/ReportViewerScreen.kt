package com.ss.medrecord.ui.feature.report.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.ErrorState
import com.ss.medrecord.domain.model.formatFileSize

@Composable
fun ReportViewerRoute(
    onNavigateBack: () -> Unit,
    viewModel: ReportViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ReportViewerEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    ReportViewerScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportViewerScreen(
    state: ReportViewerUiState,
    onEvent: (ReportViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.report?.fileName ?: "Report",
                            maxLines = 1,
                        )
                        state.report?.let { report ->
                            Text(
                                text = buildString {
                                    append(formatFileSize(report.fileSizeBytes))
                                    if (state.isMultiPage) {
                                        append(" - ${state.pages.size} pages")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = { onEvent(ReportViewerEvent.BackClicked) }) {
                        Text(text = "Close")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Documents read better against a neutral ground than against
                // the app's surface colour, in either theme.
                .background(ViewerBackground)
                .onSizeChanged { onEvent(ReportViewerEvent.ViewportMeasured(it.width)) },
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> LoadingIndicator(isDownloading = state.isDownloading)

                state.errorMessage != null -> ErrorState(
                    message = state.errorMessage,
                    onRetry = { onEvent(ReportViewerEvent.RetryClicked) },
                )

                state.hasContent -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.pages) { index, page ->
                        Image(
                            bitmap = page,
                            contentDescription = if (state.isMultiPage) {
                                "Page ${index + 1} of ${state.pages.size}"
                            } else {
                                state.report?.fileName
                            },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(isDownloading: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        if (isDownloading) {
            Text(
                text = "Fetching this report from the cloud",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private val ViewerBackground = Color(0xFF1C1B1F)
