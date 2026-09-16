package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mnn.DownloadState
import com.ai.assistance.operit.data.storage.LocalModelCompleteness
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelEntry
import com.ai.assistance.operit.data.storage.LocalModelKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot

@Composable
fun LocalModelStorageScreen(
    onDownloadMnnModels: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context) { LocalModelStorageViewModel.Factory(context) }
    val storageViewModel: LocalModelStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<LocalModelEntry?>(null) }

    LaunchedEffect(state.deleteOutcome) {
        val outcome = state.deleteOutcome ?: return@LaunchedEffect
        val message = when (outcome) {
            LocalModelDeleteOutcome.DELETED ->
                context.getString(R.string.data_storage_local_models_deleted)
            LocalModelDeleteOutcome.IN_USE ->
                context.getString(R.string.data_storage_local_models_in_use)
            LocalModelDeleteOutcome.FAILED ->
                context.getString(R.string.data_storage_local_models_delete_failed)
        }
        snackbarHostState.showSnackbar(message)
        storageViewModel.consumeDeleteOutcome()
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isLoading || state.isDeleting) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        )
                    }
                }

                item {
                    LocalModelSummary(
                        modelCount = state.displayedModels.size,
                        totalBytes = state.totalBytes,
                        isRefreshing = state.isLoading || state.isDeleting,
                        onRefresh = storageViewModel::refresh,
                    )
                }

                item {
                    LocalModelFilterRow(
                        selected = state.filter,
                        onSelect = storageViewModel::setFilter,
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onDownloadMnnModels,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.data_storage_local_models_download_mnn))
                    }
                }

                if (state.errorMessage != null) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = state.errorMessage ?: "",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (state.displayedModels.isEmpty() && !state.isLoading) {
                    item {
                        LocalModelEmptyState()
                    }
                } else {
                    items(state.displayedModels, key = { it.id }) { model ->
                        LocalModelCard(
                            model = model,
                            deleteEnabled = model.canDelete && !state.isDeleting,
                            onDelete = { pendingDelete = model },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.data_storage_local_models_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.data_storage_local_models_delete_message,
                        model.displayName,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        storageViewModel.delete(model)
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LocalModelSummary(
    modelCount: Int,
    totalBytes: Long,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.data_storage_local_models_usage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.data_storage_refresh),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = formatStorageSize(totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.data_storage_local_models_count, modelCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalModelFilterRow(
    selected: LocalModelFilter,
    onSelect: (LocalModelFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocalModelFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelResource)) },
            )
        }
    }
}

@Composable
private fun LocalModelEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = localModelPanelColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.data_storage_local_models_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LocalModelCard(
    model: LocalModelEntry,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = localModelPanelColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(model.kind.labelResource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = deleteEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.data_storage_local_models_delete_title),
                        tint = if (deleteEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatStorageSize(model.bytes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = model.path.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = modelStatusText(model),
                style = MaterialTheme.typography.bodySmall,
                color = if (model.inUse) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            val downloading = model.downloadState as? DownloadState.Downloading
            if (downloading != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun modelStatusText(model: LocalModelEntry): String {
    if (model.inUse) {
        return stringResource(R.string.data_storage_local_models_status_in_use)
    }
    return when (val downloadState = model.downloadState) {
        is DownloadState.Connecting ->
            stringResource(R.string.data_storage_local_models_status_connecting)
        is DownloadState.Downloading ->
            stringResource(
                R.string.data_storage_local_models_status_downloading,
                (downloadState.progress * 100).toInt(),
            )
        is DownloadState.Paused ->
            stringResource(
                R.string.data_storage_local_models_status_paused,
                (downloadState.progress * 100).toInt(),
            )
        is DownloadState.Failed ->
            stringResource(R.string.data_storage_local_models_status_failed, downloadState.error)
        else -> when (model.completeness) {
            LocalModelCompleteness.COMPLETE ->
                stringResource(R.string.data_storage_local_models_status_complete)
            LocalModelCompleteness.INCOMPLETE ->
                stringResource(R.string.data_storage_local_models_status_incomplete)
            LocalModelCompleteness.DOWNLOADING ->
                stringResource(R.string.data_storage_local_models_status_connecting)
        }
    }
}

private val LocalModelFilter.labelResource: Int
    get() = when (this) {
        LocalModelFilter.ALL -> R.string.data_storage_local_models_filter_all
        LocalModelFilter.MNN -> R.string.data_storage_detail_mnn_models
        LocalModelFilter.LLAMA -> R.string.data_storage_detail_llama_models
        LocalModelFilter.SPEECH -> R.string.data_storage_detail_speech_models
    }

private val LocalModelKind.labelResource: Int
    get() = when (this) {
        LocalModelKind.MNN -> R.string.data_storage_detail_mnn_models
        LocalModelKind.LLAMA -> R.string.data_storage_detail_llama_models
        LocalModelKind.SPEECH -> R.string.data_storage_detail_speech_models
    }

@Composable
private fun localModelPanelColor(): Color {
    return if (LocalThemePreferenceSnapshot.current.useBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
}
