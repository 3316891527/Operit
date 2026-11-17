package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException

/** 所选文件夹自身与其子项都计数，无法读取或未知大小时不展示虚假的总字节数。 */
internal data class WorkspaceBatchProperties(
    val processed: Int = 0,
    val bytes: Long? = 0L,
    val files: Long = 0,
    val directories: Long = 0,
    val unreadable: Long = 0,
    val unknownSizes: Long = 0,
) {
    fun add(node: PropertyNode, totals: WorkspaceDirectoryTotals?): WorkspaceBatchProperties {
        val directory = node.directory && !node.symbolicLink
        require(!directory || totals != null)
        val itemBytes = if (directory) totals!!.bytes else node.size
        return copy(
            processed = processed + 1,
            bytes = if (bytes != null && itemBytes != null) Math.addExact(bytes, itemBytes) else null,
            files = Math.addExact(files, if (directory) totals!!.files else 1L),
            directories = Math.addExact(directories, if (directory) Math.addExact(1L, totals!!.directories) else 0L),
            unreadable = Math.addExact(unreadable, if (directory) totals!!.unreadable else 0L),
            unknownSizes = Math.addExact(unknownSizes, if (directory) totals!!.unknownSizes else if (node.size == null) 1L else 0L),
        )
    }
    fun failed(): WorkspaceBatchProperties = copy(processed = processed + 1, bytes = null, unreadable = unreadable + 1)
}

@Composable
internal fun WorkspaceBatchPropertiesDialog(targets: List<WorkspacePropertiesTarget>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var totals by remember(targets) { mutableStateOf(WorkspaceBatchProperties()) }
    var loading by remember(targets) { mutableStateOf(true) }
    var error by remember(targets) { mutableStateOf<String?>(null) }
    LaunchedEffect(targets) {
        totals = WorkspaceBatchProperties()
        loading = true
        error = null
        try {
            for (target in targets) {
                val item = try {
                    val reader = WorkspacePropertiesRepository.open(context.applicationContext, target)
                    val node = reader.read().node
                    node to if (node.directory && !node.symbolicLink) reader.totals(node) else null
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    AppLogger.e("WorkspaceProperties", "读取多选属性失败：${target.path}", failure)
                    totals = totals.failed()
                    continue
                }
                totals = totals.add(item.first, item.second)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceProperties", "汇总多选属性失败", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_properties_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.workspace_selection_count, targets.size), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.workspace_selection_properties_progress, totals.processed, targets.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PropertyRow(stringResource(R.string.workspace_properties_size), when {
                    loading -> stringResource(R.string.workspace_properties_calculating)
                    error != null || totals.bytes == null -> stringResource(R.string.workspace_properties_unknown)
                    else -> propertySize(checkNotNull(totals.bytes))
                })
                PropertyRow(stringResource(R.string.workspace_properties_file_count), propertyCount(totals.files))
                PropertyRow(stringResource(R.string.workspace_properties_folder_count), propertyCount(totals.directories))
                Text(stringResource(R.string.workspace_selection_properties_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (totals.unreadable > 0 || totals.unknownSizes > 0) Text(
                    stringResource(R.string.workspace_properties_incomplete, totals.unreadable, totals.unknownSizes),
                    color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}