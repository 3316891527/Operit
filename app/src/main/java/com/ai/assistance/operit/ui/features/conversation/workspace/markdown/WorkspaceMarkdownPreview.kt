package com.ai.assistance.operit.ui.features.conversation.workspace.markdown

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownOutline
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownDocumentPath
import com.ai.assistance.operit.ui.common.markdown.MarkdownOutlineState
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.links.openMarkdownLink
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.conversation.workspace.key
import com.ai.assistance.operit.ui.features.conversation.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceFileLink
import com.ai.assistance.operit.ui.features.conversation.workspace.links.WorkspaceMarkdownLink
import com.ai.assistance.operit.ui.features.conversation.workspace.links.resolveWorkspaceMarkdownLink
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 正文直接铺满预览区域，目录使用同一份渲染节点和布局位置。 */
@Composable
internal fun WorkspaceMarkdownPreview(
    fileInfo: OpenFileInfo,
    onFileLink: (WorkspaceFileLink) -> Unit,
    modifier: Modifier = Modifier,
    outlineVisible: Boolean = false,
    onOutlineDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val outline = remember(fileInfo.key, fileInfo.content) { MarkdownOutlineState() }
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    val topInset = with(LocalDensity.current) { 12.dp.roundToPx() }
    val active by remember(outline, scroll, topInset) { derivedStateOf { outline.activeAt(scroll.value - topInset) } }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp)) {
            CompositionLocalProvider(LocalMarkdownOutline provides outline,
                LocalMarkdownDocumentPath provides if (fileInfo.environment == "android" || fileInfo.environment.isNullOrBlank()) fileInfo.path else null) {
                StreamMarkdownRenderer(
                    content = fileInfo.content,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    onLinkClick = { url ->
                        try {
                            when (val link = resolveWorkspaceMarkdownLink(url, fileInfo.path, fileInfo.environment)) {
                                is WorkspaceMarkdownLink.File -> onFileLink(link.target)
                                is WorkspaceMarkdownLink.External -> openMarkdownLink(context, link.url)
                                WorkspaceMarkdownLink.Invalid -> OKToast.error(context, R.string.markdown_link_not_openable, OKToast.LENGTH_SHORT).show()
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            AppLogger.w("WorkspaceMarkdown", "处理文档链接失败：$url", error)
                            OKToast.error(context, R.string.markdown_link_not_openable, OKToast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
        WorkspaceMarkdownOutline(outline, outlineVisible, active, onOutlineDismiss) { index ->
            val offset = outline.offset(index)
            if (offset != null) {
                onOutlineDismiss()
                navigation?.cancel()
                navigation = scope.launch { scroll.animateScrollTo((offset + topInset).coerceIn(0, scroll.maxValue), tween(220)) }
            }
        }
    }
}
