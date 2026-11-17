package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.WorkspaceLocation
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 解密口令只用于本次读取，和创建 ZIP 的加密密码分别命名。 */
@Composable
internal fun WorkspaceArchivePasswordDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    error: String? = null,
    busy: Boolean = false,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.workspace_archive_password)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workspace_archive_read_password_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), isError = error != null, enabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotEmpty() && !busy) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** 外部长按直接进入解压流程，共用选择器、来源校验与按需解密，不先打开归档浏览页。 */
@Composable
internal fun WorkspaceArchiveExtractDialog(
    sources: List<WorkspaceLocation>,
    initial: WorkspaceLocation,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    require(sources.isNotEmpty())
    val context = LocalContext.current
    val store = remember(context) { WorkspaceArchiveStore(context) }
    val progress by store.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val passwords = remember { mutableMapOf<String, String>() }
    var destination by remember { mutableStateOf(initial) }
    var choosing by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    fun start() {
        if (busy || index >= sources.size) return
        busy = true
        error = null
        job = scope.launch {
            try {
                while (index < sources.size) {
                    val source = sources[index]
                    var session: WorkspaceArchiveSession? = null
                    try {
                        session = store.open(source, passwords[source.key].orEmpty())
                        store.extract(session, emptySet(), destination)
                        index++
                    } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                        AppLogger.e("WorkspaceArchive", "从外部解压归档失败：${source.path}", failure)
                        if (workspaceArchiveNeedsPassword(failure)) {
                            passwordInput = passwords[source.key].orEmpty()
                            passwordError = if (passwordInput.isEmpty()) null else failure.localizedMessage
                            needsPassword = true
                        } else error = failure.localizedMessage ?: failure.javaClass.simpleName
                        break
                    } finally { session?.let { store.close(it) } }
                }
                if (index == sources.size) onComplete()
            } finally { busy = false; job = null }
        }
    }
    if (choosing) {
        WorkspaceArchivePicker(destination, stringResource(R.string.workspace_archive_destination), onDismiss = onDismiss) { target, _ ->
            destination = target
            index = 0
            choosing = false
            start()
        }
    } else if (!needsPassword && index < sources.size) {
        if (busy) WorkspaceArchiveOperationDialog(progress, onCancel = { job?.cancel() })
        else AlertDialog(onDismissRequest = { if (busy) job?.cancel() else onDismiss() },
            title = { Text(stringResource(if (busy) R.string.workspace_archive_working else R.string.workspace_archive_extract)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sources[index].path)
                    Text("${destination.environment}: ${destination.path}", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.workspace_archive_destination)) }
                }
            },
            confirmButton = { if (!busy) TextButton(onClick = ::start) { Text(stringResource(R.string.workspace_archive_extract)) } },
            dismissButton = { if (!busy) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    }
    if (needsPassword) WorkspaceArchivePasswordDialog(passwordInput, { passwordInput = it },
        onDismiss = { needsPassword = false; error = context.getString(R.string.workspace_archive_password_required) },
        onConfirm = { passwords[sources[index].key] = passwordInput; needsPassword = false; start() }, error = passwordError, busy = busy)
}
