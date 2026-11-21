package com.ai.assistance.operit.ui.common.markdown.mermaid

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NativeMermaidTest {
    private val layout = MermaidLayout { text, size -> text.codePointCount(0, text.length) * size * .65f }
    private fun graph(source: String) = MermaidParser.parse(source) as MermaidDiagram.Graph
    private fun scene(source: String) = layout.layout(MermaidParser.parse(source))
    private fun fits(scene: MermaidScene, width: Float, height: Float) {
        val viewport = MermaidViewport()
        viewport.fit(scene.bounds, width, height)
        val area = MermaidBounds(0f, 0f, width, height)
        for (command in scene.commands) assertTrue("Command outside viewport: $command", area.contains(viewport.screen(command.bounds), .01f))
        val box = viewport.screen(scene.bounds)
        assertEquals(width / 2f, box.center.x, .01f)
        assertEquals(height / 2f, box.center.y, .01f)
        assertTrue(maxOf(box.width / width, box.height / height) > .99f)
    }

    @Test fun parsesChineseNodesShapesAndLabeledBranches() {
        val value = graph("flowchart TD\nA[开始] --> B{要继续吗}\nB -->|是| C(执行)\nB -->|否| D([结束])")
        assertEquals(listOf("开始", "要继续吗", "执行", "结束"), value.nodes.map { it.label })
        assertEquals(listOf(MermaidShape.RECT, MermaidShape.DIAMOND, MermaidShape.ROUND, MermaidShape.STADIUM), value.nodes.map { it.shape })
        assertEquals(listOf("", "是", "否"), value.edges.map { it.label })
    }

    @Test fun preservesSemicolonsArrowsAndCommentsInsideQuotedLabels() {
        val value = graph("graph LR; A[\"a; b --> c %% text\"] --> B[\"第二行<br/>第三行\"]; %% ignored\nB --> C")
        assertEquals("a; b --> c %% text", value.nodes[0].label)
        assertEquals("第二行\n第三行", value.nodes[1].label)
        assertEquals(2, value.edges.size)
    }

    @Test fun expandsFanOutAndKeepsChainedRelationships() {
        val value = graph("flowchart LR\nA & B --> C & D --> E")
        assertEquals(5, value.nodes.size)
        assertEquals(6, value.edges.size)
        assertEquals(setOf("A" to "C", "A" to "D", "B" to "C", "B" to "D", "C" to "E", "D" to "E"), value.edges.map { it.from to it.to }.toSet())
    }

    @Test fun distinguishesDottedThickBidirectionalAndUndirectedEdges() {
        val value = graph("flowchart TD\nA -.-> B\nB ==> C\nC <--> D\nD --- E\nE -- label --> F")
        assertEquals(MermaidLine.DASHED, value.edges[0].line)
        assertEquals(MermaidLine.THICK, value.edges[1].line)
        assertEquals(MermaidMarker.ARROW, value.edges[2].start)
        assertEquals(MermaidMarker.NONE, value.edges[3].end)
        assertEquals("label", value.edges[4].label)
    }

    @Test fun appliesDefinedAndInlineNodeColors() {
        val value = graph("flowchart TD\nA[入口]:::good --> B\nclassDef good fill:#123,stroke:#abcdef,color:white\nstyle B fill:#456")
        assertEquals("#123", value.nodes[0].style.fill)
        assertEquals("white", value.nodes[0].style.color)
        assertEquals("#456", value.nodes[1].style.fill)
    }

    @Test fun preservesRootMembershipWhenReferencedFromASubgraph() {
        val value = graph("flowchart TD\nA[Root]\nsubgraph G[Group]\nB --> A\nend")
        assertNull(value.nodes.first { it.id == "A" }.group)
        assertEquals("G", value.nodes.first { it.id == "B" }.group)
    }

    @Test fun nestedGroupsContainTheirNodesAndSiblingsDoNotOverlap() {
        val value = scene("flowchart LR\nsubgraph G[Outer]\nsubgraph H[Inner]\nA[长中文标题] --> B\nend\nC\nend\nsubgraph J[Other]\nD --> E\nend\nB --> D")
        val boxes = value.commands.filterIsInstance<MermaidDraw.Box>()
        val groups = boxes.filter { it.group }
        assertEquals(3, groups.size)
        val leaves = boxes.filterNot { it.group }
        leaves.forEach { box -> assertTrue(groups.any { it.bounds.contains(box.bounds) }) }
        for ((index, a) in leaves.withIndex()) for (b in leaves.drop(index + 1)) {
            assertFalse(a.bounds.left < b.bounds.right && a.bounds.right > b.bounds.left && a.bounds.top < b.bounds.bottom && a.bounds.bottom > b.bounds.top)
        }
        fits(value, 380f, 252f)
    }

    @Test fun allDirectionsPlaceDestinationsOnTheExpectedSide() {
        for (direction in listOf("TD", "BT", "LR", "RL")) {
            val boxes = scene("flowchart $direction\nA --> B").commands.filterIsInstance<MermaidDraw.Box>()
            val a = boxes[0].bounds.center
            val b = boxes[1].bounds.center
            when (direction) {
                "TD" -> assertTrue(b.y > a.y)
                "BT" -> assertTrue(b.y < a.y)
                "LR" -> assertTrue(b.x > a.x)
                "RL" -> assertTrue(b.x < a.x)
            }
        }
    }

    @Test fun cyclesSelfLoopsAndDisconnectedNodesHaveFiniteVisibleBounds() {
        val value = scene("flowchart TD\nA --> B --> C --> A\nB --> B\nD[独立节点]")
        assertEquals(4, value.commands.filterIsInstance<MermaidDraw.Box>().size)
        assertEquals(4, value.commands.filterIsInstance<MermaidDraw.Line>().size)
        fits(value, 320f, 252f)
        fits(value, 380f, 832f)
    }

    @Test fun labelsWrapWithoutLosingUnicodeOrEscapedEntities() {
        val title = "中文😀内容".repeat(24)
        val value = scene("flowchart TD\nA[\"$title\"]")
        val label = value.commands.filterIsInstance<MermaidDraw.Label>().single()
        assertEquals(title, label.text.lines.joinToString(""))
        assertTrue(value.commands.filterIsInstance<MermaidDraw.Box>().single().bounds.contains(label.bounds))
        assertEquals("\" < > & 😀", MermaidParser.label("#quot; " + "&" + "lt; " + "&" + "gt; " + "&" + "amp; #128512;"))
    }

    @Test fun stateInitialFinalAndCompositeNodesRemainDistinct() {
        val value = graph("stateDiagram-v2\n[*] --> Ready\nstate Running {\n[*] --> Work\nWork --> [*]\n}\nReady --> Running\nRunning --> [*]")
        assertEquals(2, value.nodes.count { it.shape == MermaidShape.INITIAL })
        assertEquals(2, value.nodes.count { it.shape == MermaidShape.FINAL })
        assertEquals(1, value.groups.size)
        fits(layout.layout(value), 380f, 252f)
    }

    @Test fun classDiagramKeepsMembersAndRelationshipMarkers() {
        val value = graph("classDiagram\nclass Animal {\n+String name\n+eat()\n}\nAnimal <|-- Duck : inherits")
        assertEquals(listOf("+String name", "+eat()"), value.nodes[0].members)
        assertEquals(MermaidMarker.TRIANGLE, value.edges.single().start)
        assertEquals("inherits", value.edges.single().label)
        fits(layout.layout(value), 380f, 252f)
    }

    @Test fun entityDiagramKeepsAttributesAndCardinality() {
        val value = graph("erDiagram\nCUSTOMER {\nint id PK\nstring name\n}\nCUSTOMER ||--o{ ORDER : places")
        assertEquals(listOf("int id PK", "string name"), value.nodes[0].members)
        assertEquals(MermaidMarker.ONE, value.edges.single().start)
        assertEquals(MermaidMarker.ZERO_MANY, value.edges.single().end)
    }

    @Test fun mindmapUsesIndentationAndKeepsEveryBranch() {
        val value = graph("mindmap\n  root((Plan))\n    Done\n      Details\n    Remaining")
        assertEquals(listOf("Plan", "Done", "Details", "Remaining"), value.nodes.map { it.label })
        assertEquals(listOf("__mind_0" to "__mind_1", "__mind_1" to "__mind_2", "__mind_0" to "__mind_3"), value.edges.map { it.from to it.to })
    }

    @Test fun sequenceKeepsAliasesNotesFramesNumberingAndActivation() {
        val value = MermaidParser.parse("sequenceDiagram\nparticipant A as 小明\nparticipant B as 服务\nautonumber\nloop retry\nA->>+B: 请求\nNote right of B: 正在处理\nB-->>-A: 返回\nend") as MermaidDiagram.Sequence
        assertEquals(listOf("A" to "小明", "B" to "服务"), value.actors)
        assertTrue(value.numbered)
        assertEquals(1, value.events.filterIsInstance<SequenceEvent.Message>()[0].activation)
        assertEquals(-1, value.events.filterIsInstance<SequenceEvent.Message>()[1].activation)
        fits(layout.layout(value), 380f, 832f)
    }

    @Test fun pieAnglesReflectValuesAndLegendIncludesAllSlices() {
        val value = scene("pie title Plan\n\"Done\" : 60\n\"Remaining\" : 40")
        assertEquals(listOf(216f, 144f), value.commands.filterIsInstance<MermaidDraw.Slice>().map { it.sweep })
        assertEquals(2, value.commands.filterIsInstance<MermaidDraw.Swatch>().size)
        fits(value, 380f, 252f)
    }

    @Test fun ganttResolvesDatesDependenciesAndSections() {
        val value = MermaidParser.parse("gantt\ntitle Plan\ndateFormat YYYY-MM-DD\nsection Work\nFirst :done, a, 2026-01-01, 2d\nSecond :active, b, after a, 3d") as MermaidDiagram.Gantt
        assertEquals(value.tasks[0].end, value.tasks[1].start)
        assertEquals(3L, value.tasks[1].end - value.tasks[1].start)
        assertEquals("Work", value.tasks[1].section)
        fits(layout.layout(value), 380f, 252f)
    }

    @Test fun commonDiagramFixturesFitInlineFullscreenAndLandscape() {
        val sources = listOf(
            "flowchart TD\nA[AI] --> B{Brain}\nB -->|Think| C[Generate code]",
            "sequenceDiagram\nAlice->>Bob: Hello\nBob-->>Alice: Ready",
            "classDiagram\nAnimal <|-- Duck",
            "stateDiagram-v2\n[*] --> Ready\nReady --> [*]",
            "erDiagram\nCUSTOMER ||--o{ ORDER : places",
            "gantt\ndateFormat YYYY-MM-DD\nTask :2026-01-01, 1d",
            "pie\n\"Done\" : 60\n\"Remaining\" : 40",
            "mindmap\n  root((Plan))\n    Done\n    Remaining",
        )
        for (source in sources) for ((w, h) in listOf(380f to 252f, 380f to 832f, 832f to 332f)) fits(scene(source), w, h)
    }

    @Test fun wideAndTallGraphsFitWithoutDiscardingNodes() {
        for (direction in listOf("TD", "LR")) {
            val value = scene("flowchart $direction\n" + (0..30).joinToString("\n") { "N$it --> N${it + 1}" })
            assertEquals(32, value.commands.filterIsInstance<MermaidDraw.Box>().size)
            fits(value, 320f, 252f)
        }
    }

    @Test fun zoomKeepsTheFocusPointFixedAndResetRestoresFullScene() {
        val value = scene("flowchart TD\nA --> B --> C")
        val viewport = MermaidViewport()
        viewport.fit(value.bounds, 380f, 252f)
        val initialScale = viewport.scale
        val focus = MermaidPoint(123f, 87f)
        val world = (focus - viewport.offset) * (1f / viewport.scale)
        viewport.zoom(2f, focus)
        val after = world * viewport.scale + viewport.offset
        assertEquals(focus.x, after.x, .001f)
        assertEquals(focus.y, after.y, .001f)
        viewport.pan(MermaidPoint(90f, -50f))
        viewport.fit(value.bounds, 380f, 252f)
        assertEquals(initialScale, viewport.scale, .001f)
        assertTrue(MermaidBounds(0f, 0f, 380f, 252f).contains(viewport.screen(value.bounds), .001f))
    }

    @Test fun resizingRefitsNegativeAndNonzeroOriginBounds() {
        val bounds = MermaidBounds(-80f, -40f, 450f, 900f)
        val viewport = MermaidViewport()
        for ((width, height) in listOf(380f to 252f, 800f to 332f, 380f to 832f)) {
            viewport.fit(bounds, width, height)
            val screen = viewport.screen(bounds)
            assertTrue(MermaidBounds(0f, 0f, width, height).contains(screen, .001f))
            assertTrue(abs(screen.center.x - width / 2f) < .001f)
        }
    }

    @Test fun malformedAndUnsupportedSyntaxFailWithLineNumbers() {
        for (source in listOf("flowchart TD\nA[open", "flowchart TD\nA -->", "flowchart TD\nclick A href", "flowchart TD\nsubgraph G\nA", "sequenceDiagram\nloop retry\nA->>B: hello", "gantt\nTask :after unknown, 1d", "xychart-beta\nx-axis [a,b]")) {
            val error = assertThrows(MermaidParseException::class.java) { MermaidParser.parse(source) }
            assertTrue(error.lineNumber >= 1)
        }
    }

    @Test fun nodeBudgetIsExplicitAndDoesNotSilentlyTruncate() {
        val source = "flowchart TD\n" + (0..400).joinToString("\n") { "Node$it" }
        assertThrows(MermaidParseException::class.java) { MermaidParser.parse(source) }
    }
}