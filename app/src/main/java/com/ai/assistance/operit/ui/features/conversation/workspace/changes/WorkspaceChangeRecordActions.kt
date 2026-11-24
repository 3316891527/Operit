package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.theme.script.ThemeScriptAlertDialog as AlertDialog
import com.ai.assistance.operit.ui.theme.script.ThemeScriptTextButton as TextButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

private data class RecordRemoval(val record: FileChangeRecord? = null, val file: WorkspaceRecordRemoval? = null) {
    val all: Boolean get() = file != null
    val count: Int get() = file?.count ?: 1
}

/** 操作菜单绑定当前文件和历史选择，汇总视图必须先选具体操作才能删除单次。 */
@Composable
internal fun ChangeRecordActions(
    file: ReviewFile, record: FileChangeRecord?, chatId: String?, scope: CoroutineScope,
    onChanged: () -> Unit, onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { WorkspaceChangeStore.get(context) }
    var expanded by remember(file.id) { mutableStateOf(false) }
    var pending by remember(file.id) { mutableStateOf<RecordRemoval?>(null) }
    var busy by remember(file.id) { mutableStateOf(false) }
    val latestError by rememberUpdatedState(onError)
    Box {
        ChangeIconButton(Icons.Default.MoreVert, stringResource(R.string.workspace_records_manage), { expanded = true }, enabled = !busy)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.workspace_records_remove_one), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)) }, enabled = record != null,
                onClick = { expanded = false; pending = RecordRemoval(record = checkNotNull(record)) })
            DropdownMenuItem(text = { Text(stringResource(R.string.workspace_records_remove_all), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)) }, onClick = {
                    expanded = false
                    busy = true
                    scope.launch {
                        try {
                            val removal = withContext(Dispatchers.IO) { store.prepareFileRemoval(file.location, chatId) }
                            if (removal.count > 0) pending = RecordRemoval(file = removal) else onChanged()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            AppLogger.e("WorkspaceChanges", "读取待清理记录失败", failure)
                            latestError(context.getString(R.string.workspace_records_remove_failed, failure.message ?: failure.javaClass.simpleName))
                        } finally { busy = false }
                    }
                })
        }
    }
    val removal = pending
    if (removal != null) AlertDialog(
        onDismissRequest = { if (!busy) pending = null },
        title = { Text(stringResource(if (removal.all) R.string.workspace_records_remove_all else R.string.workspace_records_remove_one)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(file.location.path, fontSize = 13.sp)
            if (!removal.all) {
                val selected = checkNotNull(removal.record)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(selected.timestamp)) + " · " + selected.toolName, fontSize = 12.sp)
            }
            Text(stringResource(R.string.workspace_records_remove_message, removal.count))
            Text(stringResource(if (chatId == null) R.string.workspace_records_scope_workspace else R.string.workspace_records_scope_chat), fontSize = 12.sp)
            if (!removal.all) Text(stringResource(R.string.workspace_records_remove_other_history), fontSize = 12.sp)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val fileRemoval = removal.file
                    if (fileRemoval != null) store.removeFileRecords(fileRemoval) else store.removeRecords(setOf(checkNotNull(removal.record).id))
                    pending = null
                    onChanged()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLogger.e("WorkspaceChanges", "清理操作记录失败", failure)
                    pending = null
                    latestError(context.getString(R.string.workspace_records_remove_failed, failure.message ?: failure.javaClass.simpleName))
                } finally { busy = false }
            }
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
    )
}