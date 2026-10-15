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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        val ubuntuInstalled = remember { OfflineResources.ubuntuInstalled(context) }
        var selected by rememberSaveable { mutableStateOf(intent.getStringExtra(EXTRA_GROUP) ?: OfflineResources.SPEECH) }
        var active by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(true) }
        val transferProgress = remember { MutableStateFlow(0f) }
        val fraction by transferProgress.collectAsState()
        var operationLabel by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }
        var job by remember { mutableStateOf<Job?>(null) }
        var confirmDownload by remember { mutableStateOf<String?>(null) }
        val progress: (Long, Long) -> Unit = { done, total ->
            // 合并后台高频进度，不为每个数据块创建主线程任务。
            transferProgress.value = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
        }
        fun refresh() {
            checking = true
            failed = false
            message = ""
            scope.launch {
                try {
                    groups.forEach { ready[it.id] = OfflineResources.isReady(context, it.id) }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    AppLogger.e("OfflineResources", "Checking resources failed", error)
                    failed = true
                    message = getString(R.string.offline_resources_failed, error.message)
                } finally { checking = false }
            }
        }
        fun perform(id: String, label: Int, operation: suspend () -> Unit) {
            active = id
            operationLabel = getString(label)
            transferProgress.value = 0f
            message = ""
            failed = false
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
                    failed = true
                    message = getString(R.string.offline_resources_failed, error.message)
                } finally { active = null }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) perform(selected, R.string.offline_resources_import) {
                OfflineResources.importZip(context, selected, uri, progress)
            }
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) perform(selected, R.string.resource_save_backup) {
                try { OfflineResources.exportZip(context, selected, uri, progress) }
                catch (error: Exception) {
                    // 取消或失败时删除刚创建的不完整备份文档。
                    withContext(NonCancellable + Dispatchers.IO) {
                        try { DocumentsContract.deleteDocument(contentResolver, uri) }
                        catch (cleanup: Exception) { AppLogger.w("OfflineResources", "Cannot remove incomplete export", cleanup) }
                    }
                    throw error
                }
            }
        }
        LaunchedEffect(Unit) { refresh() }
        BackHandler(enabled = active != null) { job?.cancel() }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_resources_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (active != null) job?.cancel() else finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(if (active != null) R.string.cancel else R.string.back))
                    }
                },
            )
        }) { padding ->
            OfflineResourcesContent(
                groups = groups, ready = ready, requestedGroup = intent.getStringExtra(EXTRA_GROUP),
                active = active, checking = checking, progress = fraction, operationLabel = operationLabel,
                message = message, failed = failed, ubuntuInstalled = ubuntuInstalled,
                modifier = Modifier.fillMaxSize().padding(padding),
                onDownload = { confirmDownload = it },
                onImport = { id ->
                    selected = id
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                },
                onExport = { id ->
                    selected = id
                    exportLauncher.launch("operit-$id-resources-v1.zip")
                },
                onCancel = { job?.cancel() },
                onRefresh = ::refresh,
                onOpenSource = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OfflineResources.RELEASE_URL))) },
            )
        }
        confirmDownload?.let { id ->
            val group = groups.first { it.id == id }
            AlertDialog(onDismissRequest = { confirmDownload = null }, title = { Text(stringResource(group.label)) },
                text = { Text(getString(R.string.offline_resources_confirm, String.format(Locale.getDefault(), "%.1f", group.bytes / 1048576.0))) },
                confirmButton = { TextButton(onClick = {
                    confirmDownload = null
                    perform(id, R.string.offline_resources_download) { OfflineResources.download(context, id, progress) }
                }) { Text(stringResource(R.string.offline_resources_download)) } },
                dismissButton = { TextButton(onClick = { confirmDownload = null }) { Text(stringResource(R.string.cancel)) } })
        }
    }
}
