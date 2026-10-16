@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ai.assistance.operit.ui.features.resources

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import java.util.Locale

@Composable
internal fun OfflineResourcesContent(
    groups: List<OfflineResources.Group>,
    ready: Map<String, Boolean>,
    stored: Map<String, Boolean>,
    requestedGroup: String?,
    active: String?,
    checking: Boolean,
    progress: Float,
    operationLabel: String,
    message: String,
    failed: Boolean,
    ubuntuInstalled: Boolean,
    ubuntuDeletionPending: Boolean,
    deleting: Boolean,
    modifier: Modifier = Modifier,
    onDownload: (String) -> Unit,
    onImport: (String) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSource: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.resource_overview_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.resource_overview_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onRefresh, enabled = active == null && !checking) {
                            if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, stringResource(R.string.resource_refresh))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResourceMetadataTag(Icons.Default.Android, "Android")
                        Build.SUPPORTED_ABIS.firstOrNull()?.let { ResourceMetadataTag(Icons.Default.Memory, it) }
                        ResourceMetadataTag(Icons.Default.CheckCircle, stringResource(R.string.resource_ready_count, groups.count { ready[it.id] == true }, groups.size))
                    }
                    HorizontalDivider()
                    TextButton(onClick = onOpenSource) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.offline_resources_source))
                    }
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
        item {
            Text(stringResource(R.string.resource_catalog_title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        val categories = listOf(
            R.string.resource_catalog_speech to setOf(OfflineResources.SPEECH),
            R.string.resource_catalog_environment to setOf(OfflineResources.UBUNTU),
            R.string.resource_catalog_export to setOf(OfflineResources.ANDROID_EXPORT, OfflineResources.WINDOWS_EXPORT),
        ).sortedBy { category -> category.second.none { it == requestedGroup } }
        categories.forEach { (title, ids) ->
            val entries = groups.filter { it.id in ids }.sortedBy { it.id != requestedGroup }
            if (entries.isNotEmpty()) {
                item(key = "section-$title") {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ResourceMetadataTag(null, entries.size.toString())
                    }
                }
                items(entries, key = { it.id }) { group ->
                    ResourceGroupCard(
                        group = group, ready = ready[group.id], busy = active != null || checking,
                        active = active == group.id, progress = progress, operationLabel = operationLabel,
                        ubuntuInstalled = group.id == OfflineResources.UBUNTU && ubuntuInstalled,
                        pendingDeletion = group.id == OfflineResources.UBUNTU && ubuntuDeletionPending,
                        onDownload = { onDownload(group.id) }, onImport = { onImport(group.id) },
                        onExport = { onExport(group.id) }, onDelete = { onDelete(group.id) }, onCancel = onCancel,
                        hasLocalData = stored[group.id] == true, deleting = deleting,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceGroupCard(
    group: OfflineResources.Group, ready: Boolean?, busy: Boolean, active: Boolean,
    progress: Float, operationLabel: String, ubuntuInstalled: Boolean,
    onDownload: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit,
    hasLocalData: Boolean, deleting: Boolean, pendingDeletion: Boolean,
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
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(group.label), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ResourceMetadataTag(Icons.Default.Storage, String.format(Locale.getDefault(), "%.1f MiB", group.bytes / 1048576.0))
                ResourceMetadataTag(Icons.Default.CloudDownload, stringResource(R.string.resource_source_distribution))
            }
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
            if (group.id == OfflineResources.UBUNTU) Text(
                stringResource(when {
                    pendingDeletion -> R.string.resource_ubuntu_delete_pending
                    ubuntuInstalled -> R.string.offline_resources_ubuntu_installed
                    else -> R.string.resource_ubuntu_not_installed
                }),
                style = MaterialTheme.typography.bodySmall,
                color = if (ubuntuInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active) {
                if (deleting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (deleting) stringResource(R.string.resource_deleting)
                        else stringResource(R.string.resource_transfer_progress, operationLabel, (progress * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium)
                    if (!deleting) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImport, enabled = !busy && !pendingDeletion) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.offline_resources_import))
                }
                if (hasLocalData || ubuntuInstalled) IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Default.Delete, stringResource(R.string.resource_delete), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = if (ready == true) onExport else onDownload, enabled = !busy && !pendingDeletion && ready != null) {
                    Icon(if (ready == true) Icons.Default.SaveAlt else Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (ready == true) R.string.resource_save_backup else R.string.offline_resources_download))
                }
            }
        }
    }
}


@Composable
private fun ResourceMetadataTag(icon: ImageVector?, text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
