package com.ai.assistance.operit.ui.features.resources

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OfflineResourcesActivity : ComponentActivity() {
    companion object { const val EXTRA_GROUP = "resource_group" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OperitTheme { ResourceScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ResourceScreen() {
        val context = this
        val scope = rememberCoroutineScope()
        val groups = remember { OfflineResources.groups(context) }
        val ready = remember { mutableStateMapOf<String, Boolean>() }
        var selected by rememberSaveable { mutableStateOf(intent.getStringExtra(EXTRA_GROUP) ?: OfflineResources.SPEECH) }
        var active by remember { mutableStateOf<String?>(null) }
        val transferProgress = remember { MutableStateFlow(0f) }
        val fraction by transferProgress.collectAsState()
        var message by remember { mutableStateOf("") }
        var job by remember { mutableStateOf<Job?>(null) }
        var confirmDownload by remember { mutableStateOf<String?>(null) }
        val progress: (Long, Long) -> Unit = { done, total ->
            val next = (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
            // StateFlow 合并后台线程的高频进度，避免为每个数据块创建主线程任务。
            transferProgress.value = next
        }
        fun perform(id: String, operation: suspend () -> Unit) {
            active = id
            transferProgress.value = 0f
            message = ""
            job = scope.launch {
                try {
                    operation()
                    ready[id] = OfflineResources.isReady(context, id)
                    message = getString(R.string.offline_resources_done)
                } catch (error: CancellationException) {
                    message = getString(R.string.offline_resources_cancelled)
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("OfflineResources", "Resource operation failed: $id", error)
                    message = getString(R.string.offline_resources_failed, error.message)
                } finally { active = null }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) perform(selected) { OfflineResources.importZip(context, selected, uri, progress) }
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) perform(selected) {
                try { OfflineResources.exportZip(context, selected, uri, progress) }
                catch (error: Exception) {
                    // 失败或取消的备份不可复用，删除文件选择器刚创建的不完整文档。
                    withContext(NonCancellable + Dispatchers.IO) {
                        try { DocumentsContract.deleteDocument(contentResolver, uri) }
                        catch (cleanup: Exception) { AppLogger.w("OfflineResources", "Cannot remove incomplete export", cleanup) }
                    }
                    throw error
                }
            }
        }
        LaunchedEffect(Unit) {
            try { groups.forEach { ready[it.id] = OfflineResources.isReady(context, it.id) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = getString(R.string.offline_resources_failed, error.message) }
        }
        BackHandler(enabled = active != null) { job?.cancel() }
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.offline_resources_title)) }, navigationIcon = {
                TextButton(onClick = { if (active != null) job?.cancel() else finish() }) {
                    Text(stringResource(if (active != null) R.string.cancel else R.string.back))
                }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.offline_resources_description))
                TextButton(onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfflineResources.RELEASE_URL))) }) {
                    Text(stringResource(R.string.offline_resources_source))
                }
                if (intent.hasExtra(EXTRA_GROUP)) Text(stringResource(R.string.offline_resources_retry_feature))
                if (message.isNotEmpty()) Text(message)
                groups.sortedBy { it.id != intent.getStringExtra(EXTRA_GROUP) }.forEach { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(group.label), style = MaterialTheme.typography.titleMedium)
                            Text(String.format(Locale.getDefault(), "%.1f MiB", group.bytes / 1048576.0))
                            Text(stringResource(if (ready[group.id] == true) R.string.offline_resources_ready else R.string.offline_resources_not_saved))
                            if (group.id == OfflineResources.UBUNTU && OfflineResources.ubuntuInstalled(context)) {
                                Text(stringResource(R.string.offline_resources_ubuntu_installed))
                            }
                            if (active == group.id) {
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                Text("${(fraction * 100).toInt()}%")
                                TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.cancel)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = active == null && ready[group.id] == false, onClick = { confirmDownload = group.id }) { Text(stringResource(R.string.offline_resources_download)) }
                                TextButton(enabled = active == null && ready[group.id] == true, onClick = {
                                    selected = group.id
                                    exportLauncher.launch("operit-${group.id}-resources-v1.zip")
                                }) { Text(stringResource(R.string.offline_resources_export)) }
                                TextButton(enabled = active == null, onClick = {
                                    selected = group.id
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                                }) { Text(stringResource(R.string.offline_resources_import)) }
                            }
                        }
                    }
                }
            }
        }
        confirmDownload?.let { id ->
            val group = groups.first { it.id == id }
            AlertDialog(onDismissRequest = { confirmDownload = null }, title = { Text(stringResource(group.label)) },
                text = { Text(getString(R.string.offline_resources_confirm, String.format(Locale.getDefault(), "%.1f", group.bytes / 1048576.0))) },
                confirmButton = { TextButton(onClick = {
                    confirmDownload = null
                    perform(id) { OfflineResources.download(context, id, progress) }
                }) { Text(stringResource(R.string.offline_resources_download)) } },
                dismissButton = { TextButton(onClick = { confirmDownload = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}
