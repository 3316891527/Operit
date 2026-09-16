package com.ai.assistance.operit.ui.features.storage

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.PackageSkillKind
import com.ai.assistance.operit.data.storage.formatStorageSize
import com.ai.assistance.operit.ui.common.icons.rememberProviderLogoPainter

@Composable
fun ConfigurationResourceStorageScreen() {
    val context = LocalContext.current
    val factory = remember(context) { ConfigurationResourceStorageViewModel.Factory(context) }
    val storageViewModel: ConfigurationResourceStorageViewModel = viewModel(factory = factory)
    val state by storageViewModel.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        StorageManageScaffold(
            isBusy = state.isLoading || state.job.running,
            errorMessage = state.errorMessage,
            bottomBar = {
                StorageBottomBar(
                    selectedCount = state.selectedCount,
                    selectedBytes = state.selectedBytes,
                    enabled = state.selectedCount > 0 && !state.job.running,
                    actionLabel = stringResource(R.string.data_storage_delete_selected),
                    onAction = { showConfirm = true },
                )
            },
        ) {
            item {
                StorageSummaryCard(
                    icon = Icons.Default.Extension,
                    title = stringResource(R.string.screen_title_configuration_resource_storage),
                    primaryValue = formatStorageSize(
                        (state.snapshot?.totalBytes ?: 0L) + (state.packages?.totalBytes ?: 0L),
                    ),
                    extras = listOf(
                        stringResource(
                            R.string.data_storage_configuration_counts,
                            state.displayedCards.size,
                            state.displayedConfigs.size + state.displayedExtensions.size,
                        ),
                    ),
                    scannedAtMillis = state.snapshot?.scannedAtMillis,
                    isRefreshing = state.isLoading,
                    onRefresh = storageViewModel::refresh,
                )
            }
            item {
                StorageFilterRow(
                    chips = ConfigurationResourceTab.entries.map { tab ->
                        StorageChip(tab.name, stringResource(tab.labelRes))
                    },
                    selectedId = state.tab.name,
                    onSelect = { storageViewModel.setTab(ConfigurationResourceTab.valueOf(it)) },
                )
            }
            item { StorageJobCard(state.job) }
            when (state.tab) {
                ConfigurationResourceTab.CARDS -> {
                    if (state.displayedCards.isEmpty() && !state.isLoading) {
                        item { StorageEmptyCard(stringResource(R.string.data_storage_cards_empty)) }
                    } else {
                        items(state.displayedCards, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !entry.locked && !state.job.running,
                                locked = entry.locked,
                                title = entry.name,
                                subtitle = stringResource(R.string.data_storage_bound_chats, entry.boundCount),
                                bytes = entry.bytes,
                                leadingPainter = rememberStorageAvatarPainter(entry.avatarUri),
                                leadingInitial = entry.name,
                                statusTags = listOfNotNull(
                                    if (entry.inUse) {
                                        StorageStatusTag(stringResource(R.string.data_storage_in_use), emphasis = true)
                                    } else if (entry.boundCount > 0) {
                                        StorageStatusTag(stringResource(R.string.data_storage_bound), emphasis = true)
                                    } else {
                                        null
                                    },
                                ),
                                onToggle = { storageViewModel.toggle(entry.id, entry.locked) },
                            )
                        }
                    }
                }
                ConfigurationResourceTab.CONFIGS -> {
                    if (state.displayedConfigs.isEmpty() && !state.isLoading) {
                        item { StorageEmptyCard(stringResource(R.string.data_storage_configs_empty)) }
                    } else {
                        items(state.displayedConfigs, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !entry.locked && !state.job.running,
                                locked = entry.locked,
                                title = entry.name,
                                subtitle = listOfNotNull(
                                    entry.providerDisplayName,
                                    entry.primaryModelName?.takeIf { it.isNotBlank() }?.let {
                                        stringResource(R.string.data_storage_config_primary_model, it)
                                    },
                                ).joinToString(" · "),
                                bytes = entry.bytes,
                                showBytes = false,
                                leadingPainter = rememberProviderLogoPainter(entry.providerTypeId, 32.dp),
                                leadingInitial = entry.providerDisplayName ?: entry.name,
                                leadingCircular = false,
                                statusTags = listOfNotNull(
                                    if (entry.inUse) {
                                        StorageStatusTag(stringResource(R.string.data_storage_in_use), emphasis = true)
                                    } else {
                                        null
                                    },
                                ),
                                onToggle = { storageViewModel.toggle(entry.id, entry.locked) },
                            )
                        }
                    }
                }
                ConfigurationResourceTab.EXTENSIONS -> {
                    if (state.displayedExtensions.isEmpty() && !state.isLoading) {
                        item { StorageEmptyCard(stringResource(R.string.data_storage_extensions_empty)) }
                    } else {
                        items(state.displayedExtensions, key = { it.id }) { entry ->
                            StorageSelectableRow(
                                selected = entry.id in state.selectedIds,
                                enabled = !state.job.running,
                                locked = false,
                                title = entry.name,
                                subtitle = entry.subtitle.ifBlank { entry.path?.absolutePath.orEmpty() },
                                bytes = entry.bytes,
                                leadingIcon = Icons.Default.Extension,
                                statusTags = listOf(
                                    StorageStatusTag(
                                        stringResource(
                                            if (entry.kind == PackageSkillKind.PLUGIN) {
                                                R.string.data_storage_kind_plugin
                                            } else {
                                                R.string.data_storage_kind_skill
                                            },
                                        ),
                                    ),
                                ),
                                onToggle = { storageViewModel.toggle(entry.id, false) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        StorageConfirmDialog(
            title = stringResource(R.string.data_storage_config_delete_title),
            message = stringResource(
                R.string.data_storage_delete_items_message,
                state.selectedCount,
                formatStorageSize(state.selectedBytes),
            ),
            warnings = listOf(stringResource(R.string.data_storage_config_delete_warning)),
            confirmLabel = stringResource(R.string.data_storage_confirm_delete),
            onConfirm = {
                showConfirm = false
                storageViewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

private val ConfigurationResourceTab.labelRes: Int
    get() = when (this) {
        ConfigurationResourceTab.CARDS -> R.string.data_storage_tab_cards
        ConfigurationResourceTab.CONFIGS -> R.string.data_storage_tab_configs
        ConfigurationResourceTab.EXTENSIONS -> R.string.data_storage_tab_extensions
    }
