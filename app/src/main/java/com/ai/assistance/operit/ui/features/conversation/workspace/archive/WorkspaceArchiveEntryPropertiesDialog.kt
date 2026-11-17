package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.*
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 包内属性复用外部字段与复制交互，完整索引在后台汇总，不读取或解密文件内容。 */
@Composable
internal fun WorkspaceArchiveEntryPropertiesDialog(
    source: WorkspaceLocation,
    entries: List<WorkspaceArchiveEntry>,
    targets: Set<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var properties by remember(entries, targets) { mutableStateOf<WorkspaceArchiveProperties?>(null) }
    var loading by remember(entries, targets) { mutableStateOf(true) }
    var error by remember(entries, targets) { mutableStateOf<String?>(null) }
    LaunchedEffect(entries, targets) {
        try {
            properties = withContext(Dispatchers.Default) { workspaceArchiveProperties(entries, targets) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppLogger.e("WorkspaceArchive", "汇总包内条目属性失败", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }
    val info = properties
    val single = info?.single
    val unknown = stringResource(R.string.workspace_properties_unknown)
    val pending = if (loading) stringResource(R.string.workspace_properties_calculating) else unknown
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_properties_title)) },
        text = {
            DisableSelection {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (targets.size == 1) {
                        val path = targets.single()
                        val name = path.substringAfterLast('/')
                        val directory = "/" + path.substringBeforeLast('/', "")
                        PropertyRow(stringResource(R.string.workspace_properties_name), name) { copyProperty(context, name) }
                        PropertyRow(stringResource(R.string.workspace_properties_directory), directory) { copyProperty(context, directory) }
                    } else {
                        Text(stringResource(R.string.workspace_selection_count, targets.size), style = MaterialTheme.typography.titleSmall)
                    }
                    PropertyRow(stringResource(R.string.workspace_archive_source), source.path) { copyProperty(context, source.path) }
                    if (single != null) {
                        val extension = single.name.substringAfterLast('.', "")
                        val type = when {
                            single.symbolicLink -> stringResource(R.string.workspace_properties_symlink)
                            single.directory -> stringResource(R.string.workspace_properties_folder)
                            extension.isNotBlank() -> stringResource(R.string.workspace_properties_extension_type, extension.uppercase(Locale.ROOT))
                            else -> stringResource(R.string.workspace_properties_file)
                        }
                        PropertyRow(stringResource(R.string.workspace_properties_type), type)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    PropertyRow(stringResource(R.string.workspace_properties_size), info?.bytes?.let(::propertySize) ?: pending)
                    PropertyRow(stringResource(R.string.workspace_archive_packed_size), info?.packedBytes?.let(::propertySize) ?: pending)
                    if (single != null) {
                        PropertyRow(stringResource(R.string.workspace_properties_modified), single.modified?.let(::propertyTime) ?: unknown)
                        if (!single.directory) PropertyRow(stringResource(R.string.workspace_archive_encrypted), stringResource(if (single.encrypted) R.string.yes else R.string.no))
                    }
                    if (targets.size > 1 || single?.let { it.directory && !it.symbolicLink } == true) {
                        PropertyRow(stringResource(R.string.workspace_properties_file_count), info?.files?.let(::propertyCount) ?: pending)
                        PropertyRow(stringResource(R.string.workspace_properties_folder_count), info?.directories?.let(::propertyCount) ?: pending)
                        Text(stringResource(if (targets.size > 1) R.string.workspace_selection_properties_hint else R.string.workspace_properties_recursive_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
