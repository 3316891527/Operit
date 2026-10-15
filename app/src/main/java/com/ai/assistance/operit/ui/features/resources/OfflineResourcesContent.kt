package com.ai.assistance.operit.ui.features.resources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import java.util.Locale

@Composable
internal fun OfflineResourcesContent(
    groups: List<OfflineResources.Group>,
    ready: Map<String, Boolean>,
    requestedGroup: String?,
    active: String?,
    checking: Boolean,
    progress: Float,
    operationLabel: String,
    message: String,
    failed: Boolean,
    ubuntuInstalled: Boolean,
    modifier: Modifier = Modifier,
    onDownload: (String) -> Unit,
    onImport: (String) -> Unit,
    onExport: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: () -> Unit,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CloudDownload, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.resource_overview_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.resource_overview_description), style = MaterialTheme.typography.bodyMedium)
                    Text(if (checking) stringResource(R.string.resource_checking) else {
                        stringResource(R.string.resource_ready_count, groups.count { ready[it.id] == true }, groups.size)
                    }, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (requestedGroup != null) item {
            Text(stringResource(R.string.offline_resources_retry_feature), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (message.isNotBlank()) item {
            Card(colors = CardDefaults.cardColors(containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(groups.sortedBy { it.id != requestedGroup }, key = { it.id }) { group ->
            ResourceGroupCard(
                group = group, ready = ready[group.id], busy = active != null || checking,
                active = active == group.id, progress = progress, operationLabel = operationLabel,
                ubuntuInstalled = group.id == OfflineResources.UBUNTU && ubuntuInstalled,
                onDownload = { onDownload(group.id) }, onImport = { onImport(group.id) },
                onExport = { onExport(group.id) }, onCancel = onCancel,
            )
        }
        item {
            TextButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.offline_resources_source))
            }
        }
    }
}

@Composable
private fun ResourceGroupCard(
    group: OfflineResources.Group, ready: Boolean?, busy: Boolean, active: Boolean,
    progress: Float, operationLabel: String, ubuntuInstalled: Boolean,
    onDownload: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onCancel: () -> Unit,
) {
    val icon = when (group.id) {
        OfflineResources.SPEECH -> Icons.Default.Mic
        OfflineResources.UBUNTU -> Icons.Default.Terminal
        OfflineResources.ANDROID_EXPORT -> Icons.Default.Android
        OfflineResources.WINDOWS_EXPORT -> Icons.Default.Computer
        else -> error("Unknown resource group: ${group.id}")
    }
    val description = when (group.id) {
        OfflineResources.SPEECH -> R.string.resource_speech_description
        OfflineResources.UBUNTU -> R.string.resource_ubuntu_description
        OfflineResources.ANDROID_EXPORT -> R.string.resource_android_description
        OfflineResources.WINDOWS_EXPORT -> R.string.resource_windows_description
        else -> error("Unknown resource group: ${group.id}")
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(group.label), style = MaterialTheme.typography.titleMedium)
                    Text(String.format(Locale.getDefault(), "%.1f MiB", group.bytes / 1048576.0),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ready == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.CloudDownload, null,
                    Modifier.size(16.dp), tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(when (ready) {
                    true -> R.string.offline_resources_ready
                    false -> R.string.offline_resources_not_saved
                    null -> R.string.resource_checking
                }), style = MaterialTheme.typography.labelMedium)
            }
            if (ubuntuInstalled) Text(stringResource(R.string.offline_resources_ubuntu_installed),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (active) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.resource_transfer_progress, operationLabel, (progress * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (ready == true) onExport else onDownload, enabled = !busy && ready != null,
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
                    Icon(if (ready == true) Icons.Default.SaveAlt else Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (ready == true) R.string.resource_save_backup else R.string.offline_resources_download))
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.offline_resources_import))
                }
            }
        }
    }
}
