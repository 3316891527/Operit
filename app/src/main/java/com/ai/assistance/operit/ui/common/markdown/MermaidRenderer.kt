package com.ai.assistance.operit.ui.common.markdown

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidCanvasView
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidParser
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidScene
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface NativeMermaidState {
    data object Loading : NativeMermaidState
    data class Ready(val scene: MermaidScene) : NativeMermaidState
    data class Failed(val detail: String) : NativeMermaidState
}

/** 聊天和工作区共用原生 Canvas 图表，源码入口和全屏调用保持一致。 */
@Composable
fun MermaidRenderer(code: String, modifier: Modifier = Modifier) {
    key(code) { NativeMermaidPreview(code, modifier) }
}

@Composable
private fun NativeMermaidPreview(code: String, modifier: Modifier) {
    val state by produceState<NativeMermaidState>(NativeMermaidState.Loading, code) {
        value = try {
            withContext(Dispatchers.Default) {
                NativeMermaidState.Ready(MermaidCanvasView.buildScene(MermaidParser.parse(code)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("MermaidRenderer", "原生图表解析或布局失败", error)
            NativeMermaidState.Failed(error.message.orEmpty())
        }
    }
    Box(modifier.background(Color(0xFF1E1E1E))) {
        when (val result = state) {
            NativeMermaidState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is NativeMermaidState.Failed -> Text(
                text = stringResource(R.string.markdown_mermaid_render_failed) + "\n" + result.detail,
                color = Color(0xFFF2B8B5),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            )
            is NativeMermaidState.Ready -> {
                val context = LocalContext.current
                val canvasView = remember(context) { MermaidCanvasView(context) }
                Column(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { canvasView },
                        update = { it.render(result.scene) },
                        modifier = Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = result.scene.description },
                    )
                    // 操作栏独占布局空间，不覆盖图形或参与图表坐标计算。
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { canvasView.zoom(1f / 1.25f) }) {
                            Icon(Icons.Default.Remove, stringResource(R.string.file_manager_zoom_out), tint = Color.White)
                        }
                        IconButton(onClick = { canvasView.fit() }) {
                            Icon(Icons.Default.MyLocation, stringResource(R.string.common_reset_zoom), tint = Color.White)
                        }
                        IconButton(onClick = { canvasView.zoom(1.25f) }) {
                            Icon(Icons.Default.Add, stringResource(R.string.file_manager_zoom_in), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}