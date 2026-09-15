package com.ai.assistance.operit.ui.features.storage

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.CleanupTarget
import com.ai.assistance.operit.data.storage.CleanupTargetUsage
import com.ai.assistance.operit.data.storage.DataStorageSnapshot
import com.ai.assistance.operit.data.storage.StorageCategory
import com.ai.assistance.operit.data.storage.StorageCategoryUsage
import com.ai.assistance.operit.data.storage.StorageDetail
import com.ai.assistance.operit.data.storage.StorageDetailUsage
import com.ai.assistance.operit.data.storage.StorageScope
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import java.util.Locale
import kotlin.math.min

@Composable
fun DataStorageScreen(
    onManageCategory: (StorageCategory) -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context) { DataStorageViewModel.Factory(context) }
    val storageViewModel: DataStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTargets by remember { mutableStateOf<Set<CleanupTarget>>(emptySet()) }
    var showCleanupConfirmation by remember { mutableStateOf(false) }

    val snapshot = state.snapshot
    LaunchedEffect(snapshot?.scannedAtMillis) {
        val availableTargets =
            snapshot?.cleanupTargets
                ?.filter { it.fileCount > 0L }
                ?.mapTo(mutableSetOf()) { it.target }
                .orEmpty()
        selectedTargets = selectedTargets.intersect(availableTargets)
    }

    LaunchedEffect(state.cleanupResult) {
        val result = state.cleanupResult ?: return@LaunchedEffect
        selectedTargets = emptySet()
        val message =
            if (result.failedEntryCount == 0) {
                context.getString(
                    R.string.data_storage_cleanup_result,
                    formatStorageSize(result.deletedBytes),
                    result.deletedFileCount,
                )
            } else {
                context.getString(
                    R.string.data_storage_cleanup_partial_result,
                    formatStorageSize(result.deletedBytes),
                    result.deletedFileCount,
                    result.failedEntryCount,
                )
            }
        snackbarHostState.showSnackbar(message)
        storageViewModel.consumeCleanupResult()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isScanning && snapshot != null) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                }
            }

            if (snapshot == null) {
                item {
                    InitialScanState(
                        isScanning = state.isScanning,
                        errorMessage = state.errorMessage,
                        onRetry = storageViewModel::refresh,
                    )
                }
            } else {
                item {
                    StorageOverview(
                        snapshot = snapshot,
                        isRefreshing = state.isScanning || state.isCleaning,
                        onRefresh = storageViewModel::refresh,
                    )
                }

                if (state.errorMessage != null) {
                    item {
                        ScanErrorNotice(
                            errorMessage = state.errorMessage,
                            onRetry = storageViewModel::refresh,
                        )
                    }
                }

                if (
                    snapshot.inaccessibleEntryCount > 0 ||
                        snapshot.skippedSymbolicLinkCount > 0
                ) {
                    item { PartialScanNotice(snapshot) }
                }

                item {
                    StorageCategories(
                        categories = snapshot.categories,
                        onManageCategory = onManageCategory,
                    )
                }

                item {
                    CleanupSection(
                        targets = snapshot.cleanupTargets,
                        selectedTargets = selectedTargets,
                        isScanning = state.isScanning,
                        isCleaning = state.isCleaning,
                        onTargetChanged = { target, selected ->
                            selectedTargets =
                                if (selected) selectedTargets + target else selectedTargets - target
                        },
                        onCleanup = { showCleanupConfirmation = true },
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

    if (showCleanupConfirmation) {
        CleanupConfirmationDialog(
            selectedTargets = selectedTargets,
            targetUsages = snapshot?.cleanupTargets.orEmpty(),
            isCleaning = state.isCleaning,
            onDismiss = { showCleanupConfirmation = false },
            onConfirm = {
                showCleanupConfirmation = false
                storageViewModel.cleanup(selectedTargets)
            },
        )
    }
}

@Composable
private fun InitialScanState(
    isScanning: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    val containerColor = groupedContainerColor()
    Surface(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.data_storage_scan_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun StorageOverview(
    snapshot: DataStorageSnapshot,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val containerColor = overviewContainerColor()
    val locale = Locale.getDefault()
    val deviceStorage = snapshot.deviceStorage

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.data_storage_overview_title),
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

            if (deviceStorage != null) {
                Text(
                    text = formatStorageSize(deviceStorage.totalBytes, locale),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.data_storage_device_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                StorageSegmentBar(snapshot)
                Spacer(modifier = Modifier.height(12.dp))
                StorageLegend(snapshot)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OverviewMetric(
                        label = stringResource(R.string.data_storage_operit_tracked),
                        value = formatStorageSize(snapshot.trackedBytes, locale),
                        modifier = Modifier.weight(1f),
                    )
                    OverviewMetric(
                        label = stringResource(R.string.data_storage_available),
                        value = formatStorageSize(deviceStorage.availableBytes, locale),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = formatStorageSize(snapshot.trackedBytes, locale),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.data_storage_operit_tracked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.data_storage_device_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageSegmentBar(snapshot: DataStorageSnapshot) {
    val device = snapshot.deviceStorage ?: return
    val total = device.totalBytes.coerceAtLeast(0L)
    if (total == 0L) return

    val available = device.availableBytes.coerceIn(0L, total)
    val used = total - available
    val rawScopeBytes = StorageScope.entries.associateWith { scope ->
        snapshot.scopes.firstOrNull { it.scope == scope }?.bytes?.coerceAtLeast(0L) ?: 0L
    }
    val rawTracked = rawScopeBytes.values.sum()
    val boundedTracked = min(rawTracked, used)
    val scale = if (rawTracked > 0L) boundedTracked.toDouble() / rawTracked.toDouble() else 0.0
    val otherUsed = (used - boundedTracked).coerceAtLeast(0L)
    val colors = scopeColors()

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        StorageScope.entries.forEach { scope ->
            val weight = rawScopeBytes.getValue(scope).toDouble() * scale
            if (weight > 0.0) {
                Box(
                    modifier =
                        Modifier.weight(weight.toFloat())
                            .fillMaxHeight()
                            .background(colors.getValue(scope)),
                )
            }
        }
        if (otherUsed > 0L) {
            Box(
                modifier =
                    Modifier.weight(otherUsed.toFloat())
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        if (available > 0L) {
            Spacer(modifier = Modifier.weight(available.toFloat()).fillMaxHeight())
        }
    }
}

@Composable
private fun StorageLegend(snapshot: DataStorageSnapshot) {
    val device = snapshot.deviceStorage ?: return
    val locale = Locale.getDefault()
    val scopeBytes = StorageScope.entries.associateWith { scope ->
        snapshot.scopes.firstOrNull { it.scope == scope }?.bytes ?: 0L
    }
    val used = (device.totalBytes - device.availableBytes).coerceAtLeast(0L)
    val otherUsed = (used - snapshot.trackedBytes).coerceAtLeast(0L)
    val scopeColors = scopeColors()
    val items =
        buildList {
            StorageScope.entries.forEach { scope ->
                add(
                    LegendValue(
                        label = stringResource(scope.labelRes),
                        value = formatStorageSize(scopeBytes.getValue(scope), locale),
                        color = scopeColors.getValue(scope),
                    ),
                )
            }
            add(
                LegendValue(
                    label = stringResource(R.string.data_storage_other_used),
                    value = formatStorageSize(otherUsed, locale),
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item -> StorageLegendItem(item) }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.StorageLegendItem(item: LegendValue) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier.padding(top = 5.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(item.color),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageCategories(
    categories: List<StorageCategoryUsage>,
    onManageCategory: (StorageCategory) -> Unit,
) {
    var expandedCategories by remember { mutableStateOf<Set<StorageCategory>>(emptySet()) }
    val visibleCategories = categories.filter { it.category != StorageCategory.OTHER || it.bytes > 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = groupedContainerColor(),
    ) {
        Column {
            Text(
                text = stringResource(R.string.data_storage_categories_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            visibleCategories.forEachIndexed { index, usage ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
                StorageCategoryRow(
                    usage = usage,
                    expanded = usage.category in expandedCategories,
                    onToggleExpanded = {
                        expandedCategories =
                            if (usage.category in expandedCategories) {
                                expandedCategories - usage.category
                            } else {
                                expandedCategories + usage.category
                            }
                    },
                    onManage = { onManageCategory(usage.category) },
                )
            }
        }
    }
}

@Composable
private fun StorageCategoryRow(
    usage: StorageCategoryUsage,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onManage: () -> Unit,
) {
    val categoryTitle = stringResource(usage.category.titleRes)
    val hasDetails = usage.details.isNotEmpty()
    val manageable = usage.category.isManageable

    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(enabled = hasDetails || manageable) {
                        if (hasDetails) onToggleExpanded() else onManage()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = usage.category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = categorySummary(usage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasDetails) {
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription =
                            stringResource(
                                if (expanded) R.string.data_storage_collapse_details
                                else R.string.data_storage_expand_details,
                            ),
                    )
                }
            }
            if (manageable) {
                IconButton(onClick = onManage) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription =
                            stringResource(R.string.data_storage_manage_category, categoryTitle),
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded && hasDetails) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f))
                        .padding(start = 64.dp, end = 16.dp, bottom = 8.dp),
            ) {
                usage.details.forEachIndexed { index, detail ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                    StorageDetailRow(detail)
                }
            }
        }
    }
}

@Composable
private fun StorageDetailRow(detail: StorageDetailUsage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(detail.detail.titleRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.data_storage_file_count, detail.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.inaccessibleEntryCount > 0) {
                Text(
                    text =
                        stringResource(
                            R.string.data_storage_unavailable_entries,
                            detail.inaccessibleEntryCount,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = formatStorageSize(detail.bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CleanupSection(
    targets: List<CleanupTargetUsage>,
    selectedTargets: Set<CleanupTarget>,
    isScanning: Boolean,
    isCleaning: Boolean,
    onTargetChanged: (CleanupTarget, Boolean) -> Unit,
    onCleanup: () -> Unit,
) {
    val selectedBytes = targets.filter { it.target in selectedTargets }.sumOf { it.bytes }
    val hasCleanableFiles = targets.any { it.fileCount > 0L }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = groupedContainerColor(),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.data_storage_cleanup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.data_storage_cleanup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (hasCleanableFiles) {
                targets.forEachIndexed { index, usage ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                    CleanupTargetRow(
                        usage = usage,
                        checked = usage.target in selectedTargets,
                        enabled = !isScanning && !isCleaning && usage.fileCount > 0L,
                        onCheckedChange = { selected -> onTargetChanged(usage.target, selected) },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.data_storage_nothing_to_clean),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Text(
                text = stringResource(R.string.data_storage_cleanup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Button(
                onClick = onCleanup,
                enabled = selectedTargets.isNotEmpty() && !isScanning && !isCleaning,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                if (isCleaning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.data_storage_cleanup_running))
                } else if (selectedTargets.isEmpty()) {
                    Text(stringResource(R.string.data_storage_cleanup_select))
                } else {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.data_storage_cleanup_action,
                            formatStorageSize(selectedBytes),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanupTargetRow(
    usage: CleanupTargetUsage,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = stringResource(usage.target.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(usage.target.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatStorageSize(usage.bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CleanupConfirmationDialog(
    selectedTargets: Set<CleanupTarget>,
    targetUsages: List<CleanupTargetUsage>,
    isCleaning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val selectedBytes = targetUsages.filter { it.target in selectedTargets }.sumOf { it.bytes }
    AlertDialog(
        onDismissRequest = { if (!isCleaning) onDismiss() },
        title = { Text(stringResource(R.string.data_storage_cleanup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.data_storage_cleanup_confirm_message,
                        formatStorageSize(selectedBytes),
                    ),
                )
                Text(
                    text = stringResource(R.string.data_storage_cleanup_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isCleaning) {
                Text(
                    text = stringResource(R.string.data_storage_cleanup_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCleaning) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PartialScanNotice(snapshot: DataStorageSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.data_storage_partial_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        stringResource(
                            R.string.data_storage_partial_description,
                            snapshot.inaccessibleEntryCount,
                            snapshot.skippedSymbolicLinkCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ScanErrorNotice(
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text =
                    errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.data_storage_scan_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.data_storage_retry),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun categorySummary(usage: StorageCategoryUsage): String {
    val parts = mutableListOf(formatStorageSize(usage.bytes))
    when (usage.category) {
        StorageCategory.CHAT_HISTORY -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_chat_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        StorageCategory.MEMORY_LIBRARY -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_memory_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        StorageCategory.CONFIGURATION -> {
            if (usage.itemCount != null && usage.secondaryItemCount != null) {
                parts +=
                    stringResource(
                        R.string.data_storage_configuration_counts,
                        usage.itemCount,
                        usage.secondaryItemCount,
                    )
            }
        }

        else -> usage.itemCount?.let { parts += stringResource(R.string.data_storage_item_count, it) }
    }
    if (usage.inaccessibleEntryCount > 0) {
        parts += stringResource(R.string.data_storage_unavailable_entries, usage.inaccessibleEntryCount)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun scopeColors(): Map<StorageScope, Color> =
    mapOf(
        StorageScope.APP_DATA to MaterialTheme.colorScheme.primary,
        StorageScope.USER_FILES to MaterialTheme.colorScheme.secondary,
        StorageScope.CACHE to MaterialTheme.colorScheme.tertiary,
    )

@Composable
private fun groupedContainerColor(): Color {
    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    return if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
}

@Composable
private fun overviewContainerColor(): Color {
    val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
    return if (hasBackgroundImage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    }
}

private data class LegendValue(
    val label: String,
    val value: String,
    val color: Color,
)

private val StorageScope.labelRes: Int
    @StringRes get() =
        when (this) {
            StorageScope.APP_DATA -> R.string.data_storage_scope_app_data
            StorageScope.USER_FILES -> R.string.data_storage_scope_user_files
            StorageScope.CACHE -> R.string.data_storage_scope_cache
        }

private val StorageCategory.titleRes: Int
    @StringRes get() =
        when (this) {
            StorageCategory.LINUX_ENVIRONMENT -> R.string.data_storage_category_linux
            StorageCategory.LOCAL_MODELS -> R.string.data_storage_category_models
            StorageCategory.WORKSPACES_AND_MEDIA -> R.string.data_storage_category_workspaces
            StorageCategory.CHAT_HISTORY -> R.string.data_storage_category_chats
            StorageCategory.MEMORY_LIBRARY -> R.string.data_storage_category_memory
            StorageCategory.BACKUPS_AND_EXPORTS -> R.string.data_storage_category_backups
            StorageCategory.CONFIGURATION -> R.string.data_storage_category_configuration
            StorageCategory.PACKAGES_AND_PLUGINS -> R.string.data_storage_category_packages
            StorageCategory.CACHE_AND_TEMPORARY -> R.string.data_storage_category_cache
            StorageCategory.OTHER -> R.string.data_storage_category_other
        }

private val StorageCategory.icon: ImageVector
    get() =
        when (this) {
            StorageCategory.LINUX_ENVIRONMENT -> Icons.Default.Terminal
            StorageCategory.LOCAL_MODELS -> Icons.Default.SmartToy
            StorageCategory.WORKSPACES_AND_MEDIA -> Icons.Default.Folder
            StorageCategory.CHAT_HISTORY -> Icons.Default.Forum
            StorageCategory.MEMORY_LIBRARY -> Icons.Default.Psychology
            StorageCategory.BACKUPS_AND_EXPORTS -> Icons.Default.Backup
            StorageCategory.CONFIGURATION -> Icons.Default.Tune
            StorageCategory.PACKAGES_AND_PLUGINS -> Icons.Default.Extension
            StorageCategory.CACHE_AND_TEMPORARY -> Icons.Default.CleaningServices
            StorageCategory.OTHER -> Icons.Default.MoreHoriz
        }

private val StorageCategory.isManageable: Boolean
    get() =
        when (this) {
            StorageCategory.LINUX_ENVIRONMENT,
            StorageCategory.LOCAL_MODELS,
            StorageCategory.WORKSPACES_AND_MEDIA,
            StorageCategory.CHAT_HISTORY,
            StorageCategory.MEMORY_LIBRARY,
            StorageCategory.BACKUPS_AND_EXPORTS,
            StorageCategory.CONFIGURATION,
            StorageCategory.PACKAGES_AND_PLUGINS -> true
            StorageCategory.CACHE_AND_TEMPORARY,
            StorageCategory.OTHER -> false
        }

private val StorageDetail.titleRes: Int
    @StringRes get() =
        when (this) {
            StorageDetail.LINUX_SYSTEM -> R.string.data_storage_detail_linux_system
            StorageDetail.MNN_MODELS -> R.string.data_storage_detail_mnn_models
            StorageDetail.LLAMA_MODELS -> R.string.data_storage_detail_llama_models
            StorageDetail.SPEECH_MODELS -> R.string.data_storage_detail_speech_models
            StorageDetail.OTHER_MODELS -> R.string.data_storage_detail_other_models
            StorageDetail.INTERNAL_WORKSPACES -> R.string.data_storage_detail_internal_workspaces
            StorageDetail.SHARED_WORKSPACES -> R.string.data_storage_detail_shared_workspaces
            StorageDetail.MEDIA_POOLS -> R.string.data_storage_detail_media_pools
            StorageDetail.CHAT_DATABASE -> R.string.data_storage_detail_chat_database
            StorageDetail.MEMORY_DATABASES -> R.string.data_storage_detail_memory_databases
            StorageDetail.VECTOR_INDEXES -> R.string.data_storage_detail_vector_indexes
            StorageDetail.BACKUPS -> R.string.data_storage_detail_backups
            StorageDetail.EXPORTS -> R.string.data_storage_detail_exports
            StorageDetail.PREFERENCES -> R.string.data_storage_detail_preferences
            StorageDetail.CHARACTER_ASSETS -> R.string.data_storage_detail_character_assets
            StorageDetail.PLUGIN_FILES -> R.string.data_storage_detail_plugin_files
            StorageDetail.SKILL_FILES -> R.string.data_storage_detail_skill_files
            StorageDetail.APP_CACHE -> R.string.data_storage_detail_app_cache
            StorageDetail.CODE_CACHE -> R.string.data_storage_detail_code_cache
            StorageDetail.EXTERNAL_CACHE -> R.string.data_storage_detail_external_cache
            StorageDetail.TEMPORARY_FILES -> R.string.data_storage_detail_temporary_files
            StorageDetail.LOG_FILES -> R.string.data_storage_detail_log_files
            StorageDetail.PACKAGE_CACHE -> R.string.data_storage_detail_package_cache
            StorageDetail.LINUX_PACKAGE_CACHE -> R.string.data_storage_detail_linux_package_cache
            StorageDetail.BROWSER_DATA -> R.string.data_storage_detail_browser_data
            StorageDetail.OTHER_APP_DATA -> R.string.data_storage_detail_other_app_data
            StorageDetail.OTHER_USER_FILES -> R.string.data_storage_detail_other_user_files
        }

private val CleanupTarget.titleRes: Int
    @StringRes get() =
        when (this) {
            CleanupTarget.APP_CACHE -> R.string.data_storage_cleanup_app_cache
            CleanupTarget.TEMPORARY_FILES -> R.string.data_storage_cleanup_temporary_files
            CleanupTarget.LOG_FILES -> R.string.data_storage_cleanup_logs
            CleanupTarget.PACKAGE_CACHE -> R.string.data_storage_cleanup_package_cache
            CleanupTarget.LINUX_PACKAGE_CACHE -> R.string.data_storage_cleanup_linux_package_cache
        }

private val CleanupTarget.descriptionRes: Int
    @StringRes get() =
        when (this) {
            CleanupTarget.APP_CACHE -> R.string.data_storage_cleanup_app_cache_desc
            CleanupTarget.TEMPORARY_FILES -> R.string.data_storage_cleanup_temporary_files_desc
            CleanupTarget.LOG_FILES -> R.string.data_storage_cleanup_logs_desc
            CleanupTarget.PACKAGE_CACHE -> R.string.data_storage_cleanup_package_cache_desc
            CleanupTarget.LINUX_PACKAGE_CACHE -> R.string.data_storage_cleanup_linux_package_cache_desc
        }
