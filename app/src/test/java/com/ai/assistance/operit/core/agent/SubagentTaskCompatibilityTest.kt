package com.ai.assistance.operit.core.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SubagentTaskCompatibilityTest {
    @Test fun singleLayerSnapshotsWithoutTreeFieldsStillDecode() {
        val original = treeTask("legacy", status = SubagentStatus.COMPLETED)
        val json = Json { encodeDefaults = true }
        val treeFields = setOf("rootChatId", "parentTaskId", "agentName", "path", "depth", "letters", "waitingFor", "queuedAt")
        val legacy = JsonObject(json.encodeToJsonElement(original).jsonObject.filterKeys { it !in treeFields })
        val restored = json.decodeFromString<SubagentTask>(legacy.toString())
        assertEquals(original.parentChatId, restored.rootChatId)
        assertNull(restored.parentTaskId)
        assertEquals(1, restored.depth)
        assertEquals("/legacy", restored.path)
        assertTrue(restored.letters.isEmpty())
        SubagentTree.validate(listOf(restored))
    }
}
