package com.ai.assistance.operit.ui.features.conversation.workspace.archive

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.*
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties.*
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer.WorkspaceOperationDetail
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

internal data class WorkspaceApkInfo(
    val label: String,
    val icon: ImageBitmap,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val size: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val installedVersion: String?,
)

private fun workspaceApkApplication(context: Context, file: File): ApplicationInfo {
    val info = checkNotNull(context.packageManager.getPackageArchiveInfo(file.path, 0)) {
        context.getString(R.string.workspace_apk_invalid)
    }
    return checkNotNull(info.applicationInfo).apply {
        sourceDir = file.path
        publicSourceDir = file.path
    }
}

internal fun workspaceReadApkIcon(context: Context, file: File, pixels: Int) =
    workspaceApkApplication(context, file).loadIcon(context.packageManager).toBitmap(pixels, pixels)

internal fun workspaceReadApkInfo(context: Context, file: File): WorkspaceApkInfo {
    val manager = context.packageManager
    val info = checkNotNull(manager.getPackageArchiveInfo(file.path, 0)) { context.getString(R.string.workspace_apk_invalid) }
    val app = checkNotNull(info.applicationInfo)
    app.sourceDir = file.path
    app.publicSourceDir = file.path
    val installed = try { manager.getPackageInfo(info.packageName, 0) }
        catch (_: PackageManager.NameNotFoundException) { null }
    val installedVersion = installed?.let {
        val code = PackageInfoCompat.getLongVersionCode(it)
        if (it.versionName == null) code.toString() else "${it.versionName} ($code)"
    }
    return WorkspaceApkInfo(app.loadLabel(manager).toString(), app.loadIcon(manager).toBitmap(96, 96).asImageBitmap(),
        info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info), file.length(),
        app.minSdkVersion, app.targetSdkVersion, installedVersion)
}

/** 安装始终交给系统，文件通过临时读取授权提供，不自行执行静默安装。 */
internal fun workspaceApkInstallIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply { clipData = android.content.ClipData.newRawUri(file.name, uri) }
}


@Composable
internal fun WorkspaceApkInfoDialog(source: WorkspaceLocation, onDismiss: () -> Unit, onBrowse: () -> Unit) {
    val context = LocalContext.current
    val operations = remember(context) { FileBrowserOperations(context) }
    val scope = rememberCoroutineScope()
    var installing by remember(source.key) { mutableStateOf(false) }
    var loading by remember(source.key) { mutableStateOf(true) }
    var file by remember(source.key) { mutableStateOf<File?>(null) }
    var info by remember(source.key) { mutableStateOf<WorkspaceApkInfo?>(null) }
    var error by remember(source.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        try {
            val prepared = WorkspaceThumbnailLoader.prepareFile(context, source)
            file = prepared
            info = withContext(Dispatchers.IO) { workspaceReadApkInfo(context, prepared) }
        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
            AppLogger.e("WorkspaceApk", "读取安装包信息失败：${source.path}", failure)
            error = failure.localizedMessage ?: failure.javaClass.simpleName
        } finally { loading = false }
    }
    val current = info
    AlertDialog(onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current != null) Image(current.icon, null, Modifier.size(52.dp))
                Column {
                    Text(if (current == null) source.path.substringAfterLast('/') else current.label,
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (current != null) Text(if (current.versionName == null) current.versionCode.toString() else
                        "${current.versionName} (${current.versionCode})", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading || installing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (installing) R.string.workspace_archive_preparing else R.string.workspace_apk_loading),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (current != null) {
                    WorkspaceOperationDetail(stringResource(R.string.package_name), current.packageName)
                    WorkspaceOperationDetail(stringResource(R.string.workspace_apk_version_code), current.versionCode.toString())
                    WorkspaceOperationDetail(stringResource(R.string.workspace_apk_size), Formatter.formatShortFileSize(context, current.size))
                    WorkspaceOperationDetail(stringResource(R.string.workspace_apk_min_sdk), "API ${current.minSdk}")
                    WorkspaceOperationDetail(stringResource(R.string.workspace_apk_target_sdk), "API ${current.targetSdk}")
                    WorkspaceOperationDetail(stringResource(R.string.workspace_apk_installed), current.installedVersion ?:
                        stringResource(R.string.workspace_apk_not_installed))
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onBrowse, enabled = !loading && !installing && file != null) { Text(stringResource(R.string.workspace_apk_view)) }
                TextButton(onClick = {
                    installing = true
                    error = null
                    scope.launch {
                        try {
                            if (context.packageManager.canRequestPackageInstalls()) {
                                val prepared = checkNotNull(file)
                                // Linux 挂载位置也先取得共享副本，安装器只接收 FileProvider 明确覆盖的路径。
                                val shared = withContext(Dispatchers.IO) {
                                    val directory = File(context.cacheDir, "workspace_shared_files/install-${UUID.randomUUID()}")
                                    check(directory.mkdirs())
                                    try {
                                        operations.transfer(listOf(FileBrowserClipboardItem(prepared.path, "android", false, false)),
                                            directory.path, "android")
                                        File(directory, prepared.name)
                                    } catch (failure: Exception) { directory.deleteRecursively(); throw failure }
                                }
                                context.startActivity(workspaceApkInstallIntent(context, shared))
                            } else {
                                error = context.getString(R.string.workspace_apk_allow_install)
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) {
                            AppLogger.e("WorkspaceApk", "打开系统安装入口失败", failure)
                            error = failure.localizedMessage ?: failure.javaClass.simpleName
                        } finally { installing = false }
                    }
                }, enabled = !loading && !installing && current != null && file != null) { Text(stringResource(R.string.workspace_apk_install)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}