package com.ai.assistance.operit.core.tools.commands.waifu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.CharacterCardActivationResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.builtin.settings.StandardSoftwareSettingsModifyTools
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.character.CharacterGroupCard
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.WaifuSettings
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 只修改自建目标，限时暴露锁重入；清理时恢复原激活目标及其运行配置。 */
@RunWith(AndroidJUnit4::class)
class WaifuSettingsToolsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prompts = ActivePromptManager.getInstance(context)
    private val cards = CharacterCardManager.getInstance(context)
    private val groups = CharacterGroupCardManager.getInstance(context)
    private val preferences = WaifuPreferences.getInstance(context)
    private val tools = WaifuSettingsTools(context)
    private val settingsTools = StandardSoftwareSettingsModifyTools(context)
    private val createdCards = mutableListOf<String>()
    private val createdGroups = mutableListOf<String>()
    private val createdChats = mutableListOf<String>()
    private lateinit var originalPrompt: ActivePrompt
    private lateinit var originalSettings: WaifuSettings
    private lateinit var cardId: String
    private lateinit var activeCardId: String
    private lateinit var groupId: String
    private val cardSettings = WaifuSettings(charDelay = 400, customPrompt = "卡片原提示词", selfiePrompt = "卡片自拍词")
    private val activeSettings = WaifuSettings(charDelay = 300, customPrompt = "当前卡提示词")
    private val groupSettings = WaifuSettings(charDelay = 600, customPrompt = "群组原提示词", selfiePrompt = "群组自拍词")
    private fun bounded(action: suspend () -> Unit): Unit = runBlocking {
        withTimeout(30_000) { action() }
    }
    @Before fun setUp() = bounded {
        originalPrompt = prompts.getActivePrompt()
        originalSettings = preferences.loadSettingsForPrompt(originalPrompt)
        cardId = cards.createCharacterCard(CharacterCard("", "Waifu命令卡-${UUID.randomUUID()}"))
            .also { createdCards += it }
        activeCardId = cards.createCharacterCard(CharacterCard("", "Waifu当前卡-${UUID.randomUUID()}"))
            .also { createdCards += it }
        // 使用相同 ID 验证卡片与角色组的存储前缀确实隔离。
        groupId = groups.createCharacterGroupCard(CharacterGroupCard(cardId, "Waifu命令组-${UUID.randomUUID()}"))
            .also { createdGroups += it }
        prompts.setActivePrompt(ActivePrompt.CharacterCard(activeCardId))
        preferences.mutateSettingsForPrompt(ActivePrompt.CharacterCard(cardId)) { cardSettings }
        preferences.mutateSettingsForPrompt(ActivePrompt.CharacterCard(activeCardId)) { activeSettings }
        preferences.mutateSettingsForPrompt(ActivePrompt.CharacterGroup(groupId)) { groupSettings }
    }
    @After fun tearDown() = bounded {
        try {
            if (::originalPrompt.isInitialized) {
                prompts.setActivePrompt(originalPrompt)
                if (::originalSettings.isInitialized) preferences.mutateSettingsForPrompt(originalPrompt) { originalSettings }
            }
        } finally {
            try {
                val dao = AppDatabase.getDatabase(context).chatDao()
                createdChats.forEach { dao.deleteChat(it) }
            } finally {
                try { createdGroups.forEach { groups.deleteCharacterGroupCard(it) } }
                finally { createdCards.forEach { cards.deleteCharacterCard(it) } }
            }
        }
    }
    private suspend fun execute(name: String, parameters: Map<String, String> = emptyMap()): ToolResult = withTimeout(10_000) {
        tools.execute(AITool(name, parameters.map { ToolParameter(it.key, it.value) }))
    }
    private fun json(result: ToolResult): JSONObject {
        assertTrue(result.error, result.success)
        return JSONObject((result.result as StringResultData).value)
    }
    private fun target(type: String = "card", id: String = cardId) = mapOf("target_type" to type, "target_id" to id)
    private suspend fun show(parameters: Map<String, String> = target()) = json(execute("get_waifu_settings", parameters))
    private suspend fun patch(revision: String, changes: JSONObject, parameters: Map<String, String> = target()): ToolResult =
        execute("set_waifu_settings", parameters + mapOf("expected_revision" to revision, "changes" to changes.toString()))
    private suspend fun liveSettings(): WaifuSettings = withTimeout(10_000) {
        WaifuSettings(
            enabled = preferences.enableWaifuModeFlow.first(), charDelay = preferences.waifuCharDelayFlow.first(),
            removePunctuation = preferences.waifuRemovePunctuationFlow.first(), enableEmoticons = preferences.waifuEnableEmoticonsFlow.first(),
            enableSelfie = preferences.waifuEnableSelfieFlow.first(), enableMergeSend = preferences.waifuEnableMergeSendFlow.first(),
            mergeSendDelayMs = preferences.waifuMergeSendDelayMsFlow.first(), customPrompt = preferences.waifuCustomPromptFlow.first(),
            selfiePrompt = preferences.waifuSelfiePromptFlow.first(),
        )
    }
    @Test fun readThenPatchReportsSavedVerifiedActualTargetAndOnlyRequestedFields() = bounded {
        val before = show()
        val saved = json(patch(before.getString("revision"), JSONObject().put("enable_waifu_mode", true).put("waifu_char_delay", 450)))
        assertTrue(saved.getBoolean("saved"))
        assertTrue(saved.getBoolean("verified"))
        assertFalse(saved.getBoolean("active"))
        assertEquals("card", saved.getString("target_type"))
        assertEquals(cardId, saved.getString("target_id"))
        val fields = saved.getJSONArray("updated_fields")
        assertEquals(setOf("enable_waifu_mode", "waifu_char_delay"), (0 until fields.length()).map { fields.getString(it) }.toSet())
        val oldValues = before.getJSONObject("values")
        val newValues = saved.getJSONObject("values")
        assertTrue(newValues.getBoolean("enable_waifu_mode"))
        assertEquals(450, newValues.getInt("waifu_char_delay"))
        oldValues.keys().asSequence().filter { it !in setOf("enable_waifu_mode", "waifu_char_delay") }.forEach {
            assertEquals(oldValues.get(it), newValues.get(it))
        }
        assertNotEquals(before.getString("revision"), saved.getString("revision"))
        assertEquals(saved.getString("revision"), show().getString("revision"))
        assertEquals(activeSettings, liveSettings())
    }
    @Test fun staleRevisionRejectsTheEntirePatchWithoutWritingAnyField() = bounded {
        val before = show()
        json(patch(before.getString("revision"), JSONObject().put("waifu_char_delay", 450)))
        val latest = show()
        assertFalse(patch(before.getString("revision"), JSONObject().put("waifu_custom_prompt", "不能写入").put("enable_waifu_mode", true)).success)
        assertEquals(latest.getString("revision"), show().getString("revision"))
        assertEquals(cardSettings.copy(charDelay = 450), preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
    }
    @Test fun concurrentPatchesWithOneRevisionHaveExactlyOneWinner() = bounded {
        val revision = show().getString("revision")
        val results = coroutineScope {
            listOf(450, 650).map { delay ->
                async(Dispatchers.IO) { delay to patch(revision, JSONObject().put("waifu_char_delay", delay)) }
            }.awaitAll()
        }
        assertEquals(1, results.count { it.second.success })
        val winner = results.single { it.second.success }
        assertEquals(winner.first, show().getJSONObject("values").getInt("waifu_char_delay"))
        assertEquals(cardSettings.customPrompt, show().getJSONObject("values").getString("waifu_custom_prompt"))
    }
    @Test fun nonCurrentGroupPatchIsIsolatedFromSameIdCardAndLiveValues() = bounded {
        val selector = target("group", groupId)
        val before = show(selector)
        val saved = json(patch(before.getString("revision"), JSONObject().put("waifu_custom_prompt", "只改群组"), selector))
        assertEquals("group", saved.getString("target_type"))
        assertFalse(saved.getBoolean("active"))
        assertEquals(groupSettings.copy(customPrompt = "只改群组"), preferences.loadSettingsForPrompt(ActivePrompt.CharacterGroup(groupId)))
        assertEquals(cardSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
        assertEquals(activeSettings, liveSettings())
    }
    @Test fun activeCardPatchUpdatesLiveValuesAndSurvivesSwitchingAwayAndBack() = bounded {
        prompts.setActivePrompt(ActivePrompt.CharacterCard(cardId))
        val before = show(emptyMap())
        val saved = json(patch(before.getString("revision"), JSONObject().put("waifu_char_delay", 550), emptyMap()))
        assertTrue(saved.getBoolean("active"))
        assertEquals(cardId, saved.getString("target_id"))
        assertEquals(cardSettings.copy(charDelay = 550), liveSettings())
        prompts.setActivePrompt(ActivePrompt.CharacterCard(activeCardId))
        assertEquals(activeSettings, liveSettings())
        prompts.setActivePrompt(ActivePrompt.CharacterCard(cardId))
        assertEquals(cardSettings.copy(charDelay = 550), liveSettings())
    }
    @Test fun activeGroupPatchUpdatesLiveValuesAndSurvivesSwitchingAwayAndBack() = bounded {
        prompts.setActivePrompt(ActivePrompt.CharacterGroup(groupId))
        val selector = target("group", groupId)
        val saved = json(patch(show(selector).getString("revision"), JSONObject().put("waifu_merge_send_delay_ms", 2000), selector))
        assertTrue(saved.getBoolean("active"))
        assertEquals(groupSettings.copy(mergeSendDelayMs = 2000), liveSettings())
        prompts.setActivePrompt(ActivePrompt.CharacterCard(activeCardId))
        prompts.setActivePrompt(ActivePrompt.CharacterGroup(groupId))
        assertEquals(groupSettings.copy(mergeSendDelayMs = 2000), liveSettings())
        assertEquals(cardSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
    }
    @Test fun trustedCallerCardSelectsItsOwnSettingsWithoutActivatingIt() = bounded {
        val caller = mapOf("__operit_package_caller_card_id" to cardId)
        val before = show(caller)
        assertEquals(cardId, before.getString("target_id"))
        json(patch(before.getString("revision"), JSONObject().put("waifu_selfie_prompt", "调用卡自拍词"), caller))
        assertEquals(ActivePrompt.CharacterCard(activeCardId), prompts.getActivePrompt())
        assertEquals(activeSettings, liveSettings())
        assertEquals("调用卡自拍词", show().getJSONObject("values").getString("waifu_selfie_prompt"))
    }
    @Test fun groupConversationSelectsGroupInsteadOfTheSpeakingCard() = bounded {
        val chat = ChatEntity(title = "Waifu命令上下文", characterGroupId = groupId)
        AppDatabase.getDatabase(context).chatDao().insertChat(chat)
        createdChats += chat.id
        val caller = mapOf("__operit_package_chat_id" to chat.id, "__operit_package_caller_card_id" to cardId)
        val before = show(caller)
        assertEquals("group", before.getString("target_type"))
        assertEquals(groupId, before.getString("target_id"))
        json(patch(before.getString("revision"), JSONObject().put("waifu_char_delay", 700), caller))
        assertEquals(groupSettings.copy(charDelay = 700), preferences.loadSettingsForPrompt(ActivePrompt.CharacterGroup(groupId)))
        assertEquals(cardSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
        assertEquals(activeSettings, liveSettings())
    }
    @Test fun invalidFieldsEmptyPatchAndBadRevisionNeverPartiallyWrite() = bounded {
        val revision = show().getString("revision")
        for (changes in listOf(
            JSONObject(), JSONObject().put("waifu_char_delay", 199), JSONObject().put("waifu_char_delay", "400"),
            JSONObject().put("enable_waifu_mode", true).put("unknown_field", true),
            JSONObject().put("waifu_custom_prompt", JSONObject.NULL),
        )) {
            assertFalse(patch(revision, changes).success)
            assertEquals(revision, show().getString("revision"))
        }
        assertFalse(patch("invalid-revision", JSONObject().put("waifu_char_delay", 500)).success)
        assertFalse(execute("set_waifu_settings", target() + mapOf("changes" to "{\"waifu_char_delay\":500}")).success)
        assertEquals(cardSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
    }
    @Test fun duplicateUnexpectedAndMalformedParametersAreRejected() = bounded {
        val revision = show().getString("revision")
        assertFalse(execute("get_waifu_settings", target() + ("unexpected" to "x")).success)
        assertFalse(execute("set_waifu_settings", target() + mapOf("expected_revision" to revision, "changes" to "[]")).success)
        val duplicate = withTimeout(10_000) {
            tools.execute(AITool("get_waifu_settings", listOf(ToolParameter("target_type", "card"), ToolParameter("target_type", "group"))))
        }
        assertFalse(duplicate.success)
        assertEquals(revision, show().getString("revision"))
    }
    @Test fun userUnknownAndInexactTargetsAndRevisionsFromOtherTargetsAreRejected() = bounded {
        val revision = show().getString("revision")
        for (selector in listOf(
            mapOf("target_type" to "user"), target("card", "missing-${UUID.randomUUID()}"),
            target("group", "missing-${UUID.randomUUID()}"), target("card", " $cardId"),
            mapOf("target_type" to "card"), mapOf("target_type" to "current", "target_id" to cardId),
        )) assertFalse(execute("get_waifu_settings", selector).success)
        assertFalse(patch(revision, JSONObject().put("waifu_char_delay", 500), target("group", groupId)).success)
        assertEquals(groupSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterGroup(groupId)))
        assertEquals(revision, show().getString("revision"))
    }
    @Test fun missingCallerConversationOrCardCannotSilentlySelectTheActiveTarget() = bounded {
        val before = show().getString("revision")
        assertFalse(execute("get_waifu_settings", mapOf(
            "__operit_package_chat_id" to "missing-${UUID.randomUUID()}",
            "__operit_package_caller_card_id" to cardId,
        )).success)
        assertFalse(execute("get_waifu_settings", mapOf(
            "__operit_package_caller_card_id" to "missing-${UUID.randomUUID()}",
        )).success)
        assertEquals(before, show().getString("revision"))
        assertEquals(activeSettings, liveSettings())
    }
    @Test fun directPreferenceApisRejectMissingTargetsWithoutCreatingStoredPrefixes() = bounded {
        val missingId = "missing-${UUID.randomUUID()}"
        val missingTargets = listOf(ActivePrompt.CharacterCard(missingId), ActivePrompt.CharacterGroup(missingId))
        for (missing in missingTargets) {
            var readRejected = false
            try { preferences.loadSettingsForPrompt(missing) }
            catch (expected: IllegalArgumentException) { readRejected = true }
            assertTrue("Missing target must not read default settings", readRejected)
            var writeRejected = false
            var transformCalled = false
            try {
                preferences.mutateSettingsForPrompt(missing) {
                    transformCalled = true
                    cardSettings.copy(customPrompt = "不得为不存在目标创建配置")
                }
            } catch (expected: IllegalArgumentException) { writeRejected = true }
            assertTrue("Missing target must not be recreated", writeRejected)
            assertFalse(transformCalled)
        }
        assertFalse(preferences.hasCharacterCardWaifuSettings(missingId))
        assertFalse(preferences.hasCharacterGroupWaifuSettings(missingId))
        assertEquals(activeSettings, liveSettings())
    }
    @Test fun discoveryOnlyOffersCardAndGroupTargetsAndTheNineFields() = bounded {
        val result = execute("list_waifu_targets")
        assertTrue(result.error, result.success)
        val rows = JSONArray((result.result as StringResultData).value)
        val selectors = (0 until rows.length()).map { rows.getJSONObject(it) }
        assertTrue(selectors.none { it.getString("target_type") == "user" })
        assertTrue(selectors.any { it.getString("target_type") == "card" && it.getString("target_id") == cardId })
        assertTrue(selectors.any { it.getString("target_type") == "group" && it.getString("target_id") == groupId })
        assertEquals(9, json(execute("describe_waifu_fields")).getJSONArray("fields").length())
    }
    @Test fun characterActivateFromGroupSwitchesActualTargetAndPreservesGroupSettings() = bounded {
        prompts.setActivePrompt(ActivePrompt.CharacterGroup(groupId))
        val activated = withTimeout(10_000) {
            settingsTools.setActiveCharacterCard(AITool("set_active_character_card", listOf(ToolParameter("character_card_id", cardId))))
        }
        assertTrue(activated.error, activated.success)
        assertEquals(cardId, (activated.result as CharacterCardActivationResultData).activeCharacterCardId)
        assertEquals(ActivePrompt.CharacterCard(cardId), prompts.getActivePrompt())
        assertEquals(cardSettings, liveSettings())
        assertEquals(groupSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterGroup(groupId)))
    }
    @Test fun characterClearWithoutGroupRestoresDefaultTargetLiveSettings() = bounded {
        val defaultPrompt = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        val expected = preferences.loadSettingsForPrompt(defaultPrompt)
        val cleared = withTimeout(10_000) { settingsTools.clearActiveCharacterCard(AITool("clear_active_character_card")) }
        assertTrue(cleared.error, cleared.success)
        assertNull((cleared.result as CharacterCardActivationResultData).activeCharacterCardId)
        assertEquals(defaultPrompt, prompts.getActivePrompt())
        assertEquals(expected, liveSettings())
        assertEquals(activeSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(activeCardId)))
    }
    @Test fun characterClearWhileGroupIsActivePreservesGroupAndItsLiveSettings() = bounded {
        prompts.setActivePrompt(ActivePrompt.CharacterGroup(groupId))
        val cleared = withTimeout(10_000) { settingsTools.clearActiveCharacterCard(AITool("clear_active_character_card")) }
        assertTrue(cleared.error, cleared.success)
        assertNull((cleared.result as CharacterCardActivationResultData).activeCharacterCardId)
        assertEquals(ActivePrompt.CharacterGroup(groupId), prompts.getActivePrompt())
        assertEquals(groupSettings, liveSettings())
        assertEquals(cardSettings, preferences.loadSettingsForPrompt(ActivePrompt.CharacterCard(cardId)))
    }
}
