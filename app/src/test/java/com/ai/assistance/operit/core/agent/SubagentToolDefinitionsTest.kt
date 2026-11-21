package com.ai.assistance.operit.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentToolDefinitionsTest {
    @Test
    fun bothLanguagesExposeExactlyTheSameProtocol() {
        val english = SubagentToolDefinitions.category(true)
        val chinese = SubagentToolDefinitions.category(false)
        assertEquals(11, english.tools.size)
        assertEquals(english.tools.map { it.name }, chinese.tools.map { it.name })
        english.tools.zip(chinese.tools).forEach { (en, cn) ->
            assertNotEquals(en.description, cn.description)
            assertTrue(cn.description.any { it in '\u4e00'..'\u9fff' })
            assertFalse(en.description.any { it in '\u4e00'..'\u9fff' })
            val enParameters = requireNotNull(en.parametersStructured)
            val cnParameters = requireNotNull(cn.parametersStructured)
            assertEquals(enParameters.map { it.copy(description = "") }, cnParameters.map { it.copy(description = "") })
            enParameters.zip(cnParameters).forEach { (enParameter, cnParameter) ->
                assertNotEquals(enParameter.description, cnParameter.description)
                assertTrue(cnParameter.description.any { it in '\u4e00'..'\u9fff' })
            }
        }
    }

    @Test
    fun inheritanceIsAnOptionalSpawnParameterAndDoesNotChangeFollowupIdentity() {
        for (english in listOf(true, false)) {
            val tools = SubagentToolDefinitions.category(english).tools.associateBy { it.name }
            val spawn = requireNotNull(tools.getValue("spawn_task").parametersStructured).associateBy { it.name }
            assertFalse(spawn.getValue("fork_turns").required)
            assertEquals("none", spawn.getValue("fork_turns").default)
            assertTrue(spawn.getValue("enable_thinking").required)
            val followup = requireNotNull(tools.getValue("followup_task").parametersStructured).associateBy { it.name }
            assertFalse(followup.containsKey("fork_turns"))
            assertTrue(followup.getValue("task_id").required)
            assertTrue(followup.getValue("execution_id").required)
            assertFalse(followup.getValue("enable_thinking").required)
        }
    }
}