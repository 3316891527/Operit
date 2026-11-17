package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.workspaceFileTypeIcon
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text

private data class ProjectTreeRow(val location: ChangeLocation, val name: String, val directory: Boolean, val depth: Int)

/** 项目树按展开目录读取，切换回变更列表不会扫描整个工作区。 */
@Composable
internal fun ChangeProjectTree(
    directory: String, environment: String?, query: String, selectedId: String?, refresh: Int,
    onSelect: (ChangeLocation) -> Unit,
) {
    val context = LocalContext.current
    val access = remember(context) { WorkspaceChangeAccess(context) }
    var root by remember(directory, environment, refresh) { mutableStateOf<ChangeLocation?>(null) }
    var children by remember(directory, environment, refresh) { mutableStateOf(emptyMap<String, List<ProjectTreeRow>>()) }
    var expanded by remember(directory, environment, refresh) { mutableStateOf(emptySet<String>()) }
    var loading by remember(directory, environment, refresh) { mutableStateOf(emptySet<String>()) }
    var error by remember(directory, environment, refresh) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load(location: ChangeLocation, depth: Int) {
        loading = loading + location.key
        error = null
        try {
            val entries = withContext(Dispatchers.IO) { access.directoryEntries(location) }
            val rows = entries.sortedWith(compareByDescending<DirectoryListingData.FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() }).map {
                ProjectTreeRow(location.copy(path = changePath(location.path + "/" + it.name)), it.name, it.isDirectory, depth)
            }
            children = children + (location.key to rows)
            expanded = expanded + location.key
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
            AppLogger.e("WorkspaceChanges", "读取项目树失败", failure)
            error = failure.message ?: failure.javaClass.simpleName
        } finally { loading = loading - location.key }
    }
    LaunchedEffect(directory, environment, refresh) {
        try {
            val location = withContext(Dispatchers.IO) { access.location(directory, environment) }
            root = location
            load(location, 0)
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
            AppLogger.e("WorkspaceChanges", "解析项目树来源失败", failure)
            error = failure.message ?: failure.javaClass.simpleName
        }
    }
    val rows = remember(root, children, expanded, query) {
        buildList {
            fun append(location: ChangeLocation) {
                for (entry in children[location.key].orEmpty()) {
                    if (query.isBlank() || entry.directory || entry.location.path.contains(query, ignoreCase = true)) add(entry)
                    if (entry.directory && entry.location.key in expanded) append(entry.location)
                }
            }
            root?.let(::append)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (loading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        error?.let { Text(it, Modifier.padding(8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val contentWidth = maxOf(maxWidth, (rows.maxOfOrNull { 48 + it.depth * 12 + it.name.length * 7 } ?: 180).dp)
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.width(contentWidth).fillMaxHeight()) {
                    items(rows, key = { it.location.key }) { row ->
                        val opened = row.location.key in expanded
                        Surface(onClick = {
                            if (!row.directory) onSelect(row.location)
                            else if (opened) expanded = expanded - row.location.key
                            else if (row.location.key in children) expanded = expanded + row.location.key
                            else if (row.location.key !in loading) scope.launch { load(row.location, row.depth + 1) }
                        }, color = if (selectedId == row.location.key) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f)) {
                            Row(Modifier.fillMaxWidth().height(28.dp).padding(start = (6 + row.depth * 12).dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (row.directory) Icon(if (opened) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight, null, Modifier.size(12.dp))
                                else Spacer(Modifier.width(12.dp))
                                if (row.directory) Icon(if (opened) Icons.Default.FolderOpen else Icons.Default.Folder, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                else Image(painterResource(workspaceFileTypeIcon(row.name)), null, Modifier.size(14.dp))
                                Text(row.name, Modifier.padding(start = 6.dp), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}