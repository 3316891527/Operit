package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun WorkspaceCompressionDialog(
    targets: List<FileBrowserClipboardItem>,
    initial: WorkspaceLocation,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { WorkspaceArchiveStore(context) }
    val progress by store.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(workspaceCompressionBaseName(targets.first())) }
    var format by remember { mutableStateOf(WorkspaceArchiveKind.ZIP) }
    val outputName = workspaceCompressionFileName(name, format, targets.first())
    var destination by remember { mutableStateOf(initial) }
    var password by remember { mutableStateOf("") }
    var level by remember { mutableIntStateOf(1) }
    var chooseDirectory by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun dismiss() { if (busy) job?.cancel() else onDismiss() }
    if (busy) WorkspaceArchiveOperationDialog(progress, onCancel = { job?.cancel() })
    else AlertDialog(onDismissRequest = ::dismiss,
        title = { Text(stringResource(if (busy) R.string.workspace_archive_working else R.string.workspace_archive_compress)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.workspace_archive_name)) },
                    suffix = if (outputName != name) { { Text(outputName.removePrefix(name)) } } else null,
                    supportingText = { Text(stringResource(R.string.workspace_archive_name_hint)) },
                    isError = !isValidWorkspaceEntryName(name))
                Text(stringResource(R.string.workspace_archive_format), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(WorkspaceArchiveKind.ZIP, WorkspaceArchiveKind.SEVEN_Z, WorkspaceArchiveKind.TAR, WorkspaceArchiveKind.TAR_GZIP).forEach { option ->
                        FilterChip(format == option, { format = option }, modifier = Modifier.padding(end = 4.dp), label = { Text(workspaceArchiveExtension(option)) })
                    }
                }
                Text(stringResource(R.string.workspace_archive_destination), style = MaterialTheme.typography.labelLarge)
                Text("${destination.environment}: ${destination.path}", modifier = Modifier.fillMaxWidth().clickable { chooseDirectory = true }.padding(vertical = 8.dp))
                if (format == WorkspaceArchiveKind.ZIP) {
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.workspace_archive_encrypt_password)) }, visualTransformation = PasswordVisualTransformation())
                    Text(stringResource(R.string.workspace_archive_level), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(R.string.workspace_archive_level_fast, R.string.workspace_archive_level_normal, R.string.workspace_archive_level_maximum).forEachIndexed { index, label ->
                            FilterChip(level == index, { level = index }, modifier = Modifier.padding(end = 4.dp), label = { Text(stringResource(label)) })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!busy) TextButton(onClick = {
                error = null
                busy = true
                job = scope.launch {
                    try {
                        val fileName = outputName
                        store.create(targets, destination, fileName, format, if (format == WorkspaceArchiveKind.ZIP) password else "", level)
                        onComplete()
                    } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { AppLogger.e("WorkspaceArchive", "创建归档失败", failure); error = failure.localizedMessage ?: failure.javaClass.simpleName } finally { busy = false; job = null }
                }
            }, enabled = isValidWorkspaceEntryName(name)) { Text(stringResource(R.string.workspace_archive_compress)) }
        },
        dismissButton = { if (!busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (chooseDirectory) WorkspaceArchivePicker(destination, stringResource(R.string.workspace_archive_destination),
        onDismiss = { chooseDirectory = false }) { location, _ -> destination = location; chooseDirectory = false }
}
