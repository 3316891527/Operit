package com.ai.assistance.operit.ui.common.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidBounds
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidCanvasView
import com.ai.assistance.operit.ui.common.markdown.mermaid.MermaidParser
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MermaidRendererAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeCanvasDrawsAllCommonDiagramsAndRefitsAfterZoomAndResize() {
        val sources = listOf(
            "flowchart TD\nA[中文入口] --> B{继续吗}\nB -->|是| C[生成代码]",
            "sequenceDiagram\nAlice->>Bob: Hello\nBob-->>Alice: Ready",
            "classDiagram\nAnimal <|-- Duck",
            "stateDiagram-v2\n[*] --> Ready\nReady --> [*]",
            "erDiagram\nCUSTOMER ||--o{ ORDER : places",
            "gantt\ndateFormat YYYY-MM-DD\nTask :2026-01-01, 1d",
            "pie\n\"Done\" : 60\n\"Remaining\" : 40",
            "mindmap\n  root((Plan))\n    Done\n    Remaining",
        )
        lateinit var view: MermaidCanvasView
        compose.setContent {
            AndroidView(factory = { context -> MermaidCanvasView(context).also { view = it } }, modifier = Modifier.width(320.dp).height(300.dp))
        }
        compose.runOnIdle {
            for (source in sources) {
                view.render(MermaidCanvasView.buildScene(MermaidParser.parse(source)))
                for ((width, height) in listOf(380 to 252, 380 to 832, 832 to 332)) {
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    view.zoom(2f)
                    view.fit()
                    val scene = requireNotNull(view.scene)
                    val area = MermaidBounds(0f, 0f, width.toFloat(), height.toFloat())
                    scene.commands.forEach { assertTrue("绘图内容超出视口", area.contains(view.viewport.screen(it.bounds), .1f)) }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bitmap))
                        val pixels = IntArray(width * height)
                        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                        assertTrue("Canvas 只有背景，没有图形像素", pixels.count { it != MermaidCanvasView.BACKGROUND } > 100)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test fun invalidSourceShowsAnErrorAndEditedSourceProducesANativeView() {
        val source = mutableStateOf("flowchart TD\nA -->")
        compose.setContent { MermaidRenderer(source.value, Modifier.width(320.dp).height(300.dp)) }
        compose.waitUntil(30_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Line 2", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Line 2", substring = true).assertExists()
        compose.runOnIdle { source.value = "flowchart TD\nA[已修正] --> B[完成]" }
        compose.waitUntil(30_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("已修正; 完成")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}