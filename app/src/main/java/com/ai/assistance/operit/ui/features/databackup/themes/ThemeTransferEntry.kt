package com.ai.assistance.operit.ui.features.databackup.themes

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.themesharing.ThemeTransferError
import com.ai.assistance.operit.ui.features.databackup.components.DataBackupDialog
import com.ai.assistance.operit.ui.features.databackup.components.DataBackupSectionHeader
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.settings.screens.theme.workbench.ThemeWorkbenchPreview

/** 单项导入导出中的主题入口，所有操作通过独立抽屉完成。 */
@Composable
internal fun ThemeTransferEntry() {
    val context = LocalContext.current
    // 路由容器不提供 AndroidViewModel 工厂，显式传入 Application 才能创建主题分享状态。
    val factory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    }
    val vm: ThemeTransferViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    var launchedArchive by rememberSaveable { mutableStateOf<String?>(null) }
    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) {
            vm.saveExport(it)
        }
    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.readFile(uri)
        }
    LaunchedEffect(state.pendingArchive, state.busy) {
        val file = state.pendingArchive
        if (file != null && !state.busy && launchedArchive != file.absolutePath) {
            launchedArchive = file.absolutePath
            val safeName = state.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
            saveLauncher.launch("$safeName.oktheme")
        }
    }
    LaunchedEffect(state.notification) {
        if (state.notification) {
            Toast.makeText(context, R.string.theme_transfer_success, Toast.LENGTH_SHORT).show()
            vm.consumeNotification()
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            DataBackupSectionHeader(
                stringResource(R.string.theme_transfer_title),
                "",
                Icons.Default.Palette,
            )
        }
        TextButton(onClick = vm::openExport, enabled = !state.busy) {
            Text(stringResource(R.string.export))
        }
        VerticalDivider(Modifier.height(18.dp))
        TextButton(onClick = vm::openImport, enabled = !state.busy) {
            Text(stringResource(R.string.import_action))
        }
    }
    if (state.panel == ThemeTransferPanel.CLOSED) return
    val title =
        when (state.panel) {
            ThemeTransferPanel.EXPORT -> R.string.theme_transfer_export
            ThemeTransferPanel.GENERATED -> R.string.theme_transfer_generated
            ThemeTransferPanel.PREVIEW -> R.string.theme_transfer_preview
            else -> R.string.theme_transfer_import
        }
    DataBackupDialog(
        onDismissRequest = { if (!state.busy) vm.close() },
        dismissEnabled = !state.busy,
        title = { Text(stringResource(title)) },
        text = {
            when (state.panel) {
                ThemeTransferPanel.EXPORT -> {
                    MethodSelector(state.method, !state.busy, vm::method)
                    state.source?.let { source ->
                        DataBackupSectionHeader(source.name, "", Icons.Default.Palette)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            state.name,
                            vm::name,
                            label = { Text(stringResource(R.string.theme_transfer_name)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !state.busy,
                        )
                        OutlinedTextField(
                            state.author,
                            vm::author,
                            label = { Text(stringResource(R.string.theme_transfer_author)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !state.busy,
                        )
                    }
                    if (state.method == ThemeTransferMethod.FILE) {
                        Text(
                            stringResource(R.string.theme_transfer_full_contents),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                ThemeTransferPanel.GENERATED -> {
                    DataBackupSectionHeader(state.name, state.author, Icons.Default.Palette)
                    OutlinedTextField(
                        state.generated,
                        {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.theme_transfer_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6,
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                    )
                }
                ThemeTransferPanel.IMPORT -> {
                    MethodSelector(state.method, !state.busy, vm::method)
                    if (state.method == ThemeTransferMethod.CODE) {
                        OutlinedTextField(
                            state.input,
                            vm::input,
                            label = { Text(stringResource(R.string.theme_transfer_paste)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 6,
                            enabled = !state.busy,
                        )
                    } else {
                        OutlinedButton(
                            onClick = { openLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Default.FileOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.theme_transfer_choose_file))
                        }
                        state.prepared?.document?.let { document ->
                            DataBackupSectionHeader(
                                document.name,
                                document.author,
                                Icons.Default.Palette,
                            )
                        }
                    }
                }
                ThemeTransferPanel.PREVIEW -> ThemeImportPreview(state, vm)
                else -> Unit
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error ->
                Text(
                    stringResource(errorResource(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = vm::close, enabled = !state.busy) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            val enabled =
                !state.busy &&
                    when (state.panel) {
                        ThemeTransferPanel.EXPORT ->
                            state.source != null &&
                                state.name.isNotBlank() &&
                                state.pendingArchive == null
                        ThemeTransferPanel.IMPORT ->
                            if (state.method == ThemeTransferMethod.CODE) state.input.isNotBlank()
                            else state.prepared != null
                        ThemeTransferPanel.PREVIEW ->
                            state.selectedId != null && state.previewValues != null
                        else -> true
                    }
            val actionLabel =
                when (state.panel) {
                    ThemeTransferPanel.GENERATED -> R.string.theme_transfer_copy
                    ThemeTransferPanel.PREVIEW -> R.string.theme_transfer_apply
                    ThemeTransferPanel.IMPORT -> R.string.theme_transfer_validate
                    else ->
                        if (state.method == ThemeTransferMethod.CODE)
                            R.string.theme_transfer_generate
                        else R.string.theme_transfer_export_file
                }
            Button(
                enabled = enabled,
                onClick = {
                    when (state.panel) {
                        ThemeTransferPanel.GENERATED -> {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    context.getString(R.string.theme_transfer_code),
                                    state.generated,
                                )
                            )
                            Toast.makeText(
                                    context,
                                    R.string.theme_transfer_copied,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                        ThemeTransferPanel.IMPORT -> vm.preview()
                        ThemeTransferPanel.PREVIEW -> vm.apply()
                        ThemeTransferPanel.EXPORT ->
                            if (state.method == ThemeTransferMethod.CODE) vm.generate()
                            else vm.prepareExport()
                        else -> Unit
                    }
                },
            ) {
                Text(stringResource(actionLabel))
            }
        },
    )
    state.overwriteTargetId?.let { id ->
        val name = state.targets.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { if (!state.busy) vm.cancelOverwrite() },
            title = { Text(stringResource(R.string.theme_transfer_overwrite_title)) },
            text = { Text(stringResource(R.string.theme_transfer_overwrite_message, name)) },
            dismissButton = {
                TextButton(onClick = vm::cancelOverwrite, enabled = !state.busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(onClick = { vm.apply(overwrite = true) }, enabled = !state.busy) {
                    Text(stringResource(R.string.theme_transfer_overwrite))
                }
            },
        )
    }
}

@Composable
private fun MethodSelector(
    selected: ThemeTransferMethod,
    enabled: Boolean,
    onSelect: (ThemeTransferMethod) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            ThemeTransferMethod.values().forEach { method ->
                TextButton(
                    onClick = { onSelect(method) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor =
                                if (selected == method) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor =
                                if (selected == method) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Text(
                        stringResource(
                            if (method == ThemeTransferMethod.CODE) R.string.theme_transfer_code
                            else R.string.theme_transfer_file
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeImportPreview(state: ThemeTransferUiState, vm: ThemeTransferViewModel) {
    val theme = state.prepared ?: return
    val context = LocalContext.current
    DataBackupSectionHeader(theme.document.name, theme.document.author, Icons.Default.Palette)
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                state.targets.firstOrNull { it.id == state.selectedId }?.name
                    ?: stringResource(R.string.theme_transfer_target),
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            state.targets.forEach { card ->
                DropdownMenuItem(
                    text = { Text(card.name) },
                    onClick = {
                        expanded = false
                        vm.select(card.id)
                    },
                )
            }
        }
    }
    state.previewValues?.let { values ->
        val preferences = remember(context) { UserPreferencesManager.getInstance(context) }
        val userName = stringResource(R.string.theme_transfer_preview_user)
        val session =
            remember(values, theme.document.layout) {
                ThemeEditorSession(preferences, values, userName, theme.document.layout)
            }
        DisposableEffect(session) { onDispose { session.dispose() } }
        ThemeWorkbenchPreview(session, full = true, layoutPreview = true, showDescription = false)
    }
    Text(
        stringResource(R.string.theme_transfer_global_layout),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun errorResource(error: ThemeTransferError): Int =
    when (error) {
        ThemeTransferError.INVALID_CODE -> R.string.theme_transfer_error_code
        ThemeTransferError.INVALID_CONFIG -> R.string.theme_transfer_error_config
        ThemeTransferError.UNSUPPORTED_VERSION -> R.string.theme_transfer_error_version
        ThemeTransferError.MISSING_MANIFEST -> R.string.theme_transfer_error_manifest
        ThemeTransferError.INVALID_ARCHIVE -> R.string.theme_transfer_error_archive
        ThemeTransferError.MISSING_ASSET -> R.string.theme_transfer_error_asset
        ThemeTransferError.TOO_LARGE -> R.string.theme_transfer_error_size
        ThemeTransferError.CHARACTER_MISSING -> R.string.theme_transfer_error_target
        ThemeTransferError.OVERWRITE_REQUIRED -> R.string.theme_transfer_overwrite_title
        ThemeTransferError.IO_ERROR -> R.string.theme_transfer_error_io
    }
