package com.ai.assistance.operit.ui.features.conversation.workspace.editor

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.conversation.workspace.archive.WorkspaceArchivePicker
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserOperations
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.isValidWorkspaceEntryName
import com.ai.assistance.operit.ui.features.conversation.workspace.text.readWorkspaceTextWithinLimit
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EditorDiskContent(val exists: Boolean, val content: String = "")
internal data class EditorExternalChange(val file: OpenFileInfo, val disk: EditorDiskContent)

/** 与首次打开共用文件工具和文本大小限制，区分删除与读取失败。 */
internal suspend fun readEditorDisk(context: Context, file: OpenFileInfo): EditorDiskContent = withContext(Dispatchers.IO) {
    val handler = AIToolHandler.getInstance(context.applicationContext)
    val parameters = listOf(ToolParameter("path", file.path), ToolParameter("environment", file.environment))
    val exists = handler.executeTool(AITool("file_exists", parameters))
    check(exists.success) { exists.error.orEmpty() }
    val info = exists.result
    check(info is FileExistsData)
    if (!info.exists) return@withContext EditorDiskContent(false)
    check(!info.isDirectory)
    checkNotNull(readWorkspaceTextWithinLimit(info.size) {
        val read = handler.executeTool(AITool("read_file_full", parameters + ToolParameter("text_only", "true")))
        check(read.success) { read.error.orEmpty() }
        val result = read.result
        check(result is FileContentData)
        EditorDiskContent(true, result.content)
    }) { context.getString(R.string.workspace_text_preview_too_large, 10) }
}

/** 新文件写入隐藏临时项，完成后复用跨环境改名发布；目标同名拒绝覆盖。 */
private suspend fun saveEditorAs(context: Context, destination: WorkspaceLocation, name: String, content: String): OpenFileInfo = withContext(Dispatchers.IO) {
    require(isValidWorkspaceEntryName(name))
    val operations = FileBrowserOperations(context)
    val path = destination.path.trimEnd('/') + "/" + name
    val temporary = destination.path.trimEnd('/') + "/." + name + ".editor-" + UUID.randomUUID()
    operations.requireNewDestination(path, destination.environment)
    val handler = AIToolHandler.getInstance(context.applicationContext)
    var published = false
    try {
        val write = handler.executeTool(AITool("write_file", listOf(ToolParameter("path", temporary),
            ToolParameter("environment", destination.environment), ToolParameter("content", content))))
        check(write.success) { write.error.orEmpty() }
        operations.rename(temporary, path, destination.environment)
        published = true
        OpenFileInfo(path, content, System.currentTimeMillis(), environment = destination.environment)
    } finally {
        if (!published) withContext(NonCancellable) {
            try {
                val exists = handler.executeTool(AITool("file_exists", listOf(ToolParameter("path", temporary), ToolParameter("environment", destination.environment))))
                check(exists.success) { exists.error.orEmpty() }
                if ((exists.result as FileExistsData).exists) operations.delete(temporary, destination.environment, false)
            } catch (failure: Exception) { AppLogger.e("WorkspaceEditor", "清理未发布的另存为文件失败", failure) }
        }
    }
}

@Composable
internal fun EditorSaveAsDialog(file: OpenFileInfo, onDismiss: () -> Unit, onSaved: (OpenFileInfo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(file.path) { mutableStateOf(file.name) }
    var destination by remember(file.path) { mutableStateOf(WorkspaceLocation(file.path.substringBeforeLast('/', "").ifEmpty { "/" }, file.environment)) }
    var picking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (picking) {
        WorkspaceArchivePicker(destination, stringResource(R.string.workspace_editor_save_as), onDismiss = onDismiss) { location, _ -> destination = location; picking = false }
    } else AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.workspace_editor_save_as)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { picking = true }, enabled = !busy) { Text("${destination.environment}: ${destination.path}") }
            OutlinedTextField(name, { name = it }, singleLine = true, enabled = !busy, label = { Text(stringResource(R.string.workspace_properties_name)) },
                isError = !isValidWorkspaceEntryName(name), modifier = Modifier.fillMaxWidth())
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = {
            busy = true; error = null
            scope.launch {
                try { onSaved(saveEditorAs(context.applicationContext, destination, name, file.content)) } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { AppLogger.e("WorkspaceEditor", "另存为失败", failure); error = failure.localizedMessage ?: failure.javaClass.simpleName } finally { busy = false }
            }
        }, enabled = !busy && isValidWorkspaceEntryName(name)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
