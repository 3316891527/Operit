package com.ai.assistance.operit.ui.features.storage

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.ai.assistance.operit.data.storage.DataStorageSnapshot
import com.ai.assistance.operit.data.storage.StorageCategory
import com.ai.assistance.operit.data.storage.StorageCategoryUsage
import com.ai.assistance.operit.data.storage.StorageDetail
import com.ai.assistance.operit.data.storage.StorageDetailUsage
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot

@Composable
fun LinuxEnvironmentStorageScreen(
    onOpenTerminal: () -> Unit,
    onOpenFileManager: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context) { DataStorageViewModel.Factory(context) }
    val storageViewModel: DataStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val snapshot = state.snapshot
            if (snapshot == null) {
                LinuxStorageInitialState(
                    isScanning = state.isScanning,
                    errorMessage = state.errorMessage,
                    onRetry = storageViewModel::refresh,
                )
            } else {
                LinuxStorageContent(
                    snapshot = snapshot,
                    isRefreshing = state.isScanning,
                    errorMessage = state.errorMessage,
                    onRefresh = storageViewModel::refresh,
                    onOpenTerminal = onOpenTerminal,
                    onOpenFileManager = onOpenFileManager,
                )
            }
        }
    }
}

@Composable
private fun LinuxStorageInitialState(
    isScanning: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = linuxStoragePanelColor(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.data_storage_scanning),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.data_storage_linux_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.data_storage_retry))
                }
            }
        }
    }
}

@Composable
private fun LinuxStorageContent(
    snapshot: DataStorageSnapshot,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFileManager: () -> Unit,
) {
    val usage = snapshot.categories.firstOrNull { it.category == StorageCategory.LINUX_ENVIRONMENT }
        ?: StorageCategoryUsage(
            category = StorageCategory.LINUX_ENVIRONMENT,
            bytes = 0L,
            fileCount = 0L,
            inaccessibleEntryCount = 0,
        )
    val detailByType = usage.details.associateBy { it.detail }
    val details = listOf(StorageDetail.LINUX_SYSTEM, StorageDetail.TERMINAL_RUNTIME).map { detail ->
        detailByType[detail]
            ?: StorageDetailUsage(
                detail = detail,
                bytes = 0L,
                fileCount = 0L,
                inaccessibleEntryCount = 0,
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isRefreshing) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
            }
        }

        if (errorMessage != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            LinuxStorageSummary(
                usage = usage,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
            )
        }

        item {
            Text(
                text = stringResource(R.string.data_storage_linux_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = linuxStoragePanelColor(),
                tonalElevation = 1.dp,
            ) {
                Column {
                    details.forEachIndexed { index, detailUsage ->
                        if (index > 0) {
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                        LinuxDetailRow(detailUsage)
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.data_storage_linux_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenTerminal,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.data_storage_linux_open_terminal))
                }
                OutlinedButton(
                    onClick = onOpenFileManager,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.data_storage_linux_open_file_manager))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun LinuxStorageSummary(
    usage: StorageCategoryUsage,
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
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.data_storage_linux_usage_title),
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
                text = formatStorageSize(usage.bytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.data_storage_file_count, usage.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinuxDetailRow(usage: StorageDetailUsage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = stringResource(usage.detail.titleResource),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (usage.inaccessibleEntryCount > 0) {
                Text(
                    text = stringResource(
                        R.string.data_storage_unavailable_entries,
                        usage.inaccessibleEntryCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatStorageSize(usage.bytes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.data_storage_file_count, usage.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val StorageDetail.titleResource: Int
    get() = when (this) {
        StorageDetail.LINUX_SYSTEM -> R.string.data_storage_detail_linux_system
        StorageDetail.TERMINAL_RUNTIME -> R.string.data_storage_detail_terminal_runtime
        else -> R.string.data_storage_detail_linux_system
    }

@Composable
private fun linuxStoragePanelColor(): Color {
    return if (LocalThemePreferenceSnapshot.current.useBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
}
