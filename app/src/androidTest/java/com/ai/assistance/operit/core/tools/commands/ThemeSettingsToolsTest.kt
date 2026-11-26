package com.ai.assistance.operit.core.tools.commands

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.tools.ThemeSettingsResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.data.character.ActivePrompt
import com.ai.assistance.operit.data.character.CharacterGroupCard
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 只对测试新建角色验证真实 DataStore，结束时恢复激活目标并删除测试资源。 */
@RunWith(AndroidJUnit4::class)
class ThemeSettingsToolsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prompts = ActivePromptManager.getInstance(context)
    private val cards = CharacterCardManager.getInstance(context)
    private val preferences = UserPreferencesManager.getInstance(context)
    private val tools = ThemeSettingsTools(context)
    private lateinit var originalPrompt: ActivePrompt
    private lateinit var cardId: String
    private lateinit var directory: File

    @Before
    fun setUp() = runBlocking {
        originalPrompt = prompts.getActivePrompt()
        directory = File(context.cacheDir, "theme-command-${UUID.randomUUID()}").also { assertTrue(it.mkdirs()) }
        cardId = cards.createCharacterCard(CharacterCard("", "主题命令测试-${UUID.randomUUID()}"))
        preferences.replaceThemeForPrompt(ActivePrompt.CharacterCard(cardId), ThemePreferenceValues.defaultVisual())
    }

    @After
    fun tearDown() {
        runBlocking {
            try {
                if (::originalPrompt.isInitialized) prompts.setActivePrompt(originalPrompt)
            } finally {
                try {
                    if (::cardId.isInitialized) cards.deleteCharacterCard(cardId)
                } finally {
                    if (::directory.isInitialized) directory.deleteRecursively()
                }
            }
        }
    }

    private suspend fun execute(name: String, fields: Map<String, String>) =
        tools.execute(AITool(name, fields.map { ToolParameter(it.key, it.value) }))

    private suspend fun show(): ThemeSettingsResultData {
        val result = execute("get_theme_settings", mapOf("target_type" to "card", "target_id" to cardId))
        assertTrue(result.error, result.success)
        return result.result as ThemeSettingsResultData
    }

    private suspend fun update(revision: String, changes: JSONObject): ToolResult =
        execute("set_theme_settings", mapOf(
            "target_type" to "card", "target_id" to cardId,
            "expected_revision" to revision, "changes" to changes.toString(),
        ))

    @Test
    fun savesAndReadsBackOnlyRequestedAppearanceFields() = runBlocking {
        val before = show()
        val result = update(before.revision, JSONObject().put("font_scale", 1.2).put("show_model_name", true))
        assertTrue(result.error, result.success)
        val saved = result.result as ThemeSettingsResultData
        assertTrue(saved.saved)
        assertTrue(saved.verified)
        assertEquals(cardId, saved.targetId)
        assertEquals(1.2f, saved.values.getValue("font_scale").jsonPrimitive.float, 0f)
        assertTrue(saved.values.getValue("show_model_name").jsonPrimitive.boolean)
        assertEquals(before.values.getValue("background_image_opacity"), saved.values.getValue("background_image_opacity"))
        assertNotEquals(before.revision, saved.revision)
        assertEquals(saved.revision, show().revision)
        assertEquals(originalPrompt, prompts.getActivePrompt())
    }

    @Test
    fun staleRevisionRejectsTheWholePatch() = runBlocking {
        val before = show()
        assertTrue(update(before.revision, JSONObject().put("show_model_name", true)).success)
        val newer = show()
        val rejected = update(before.revision, JSONObject().put("font_scale", 1.4))
        assertFalse(rejected.success)
        assertEquals(newer.revision, show().revision)
    }

    @Test
    fun unsupportedSettingsAndProfileFieldsCannotPartiallyChangeTheTheme() = runBlocking {
        val before = show()
        for (changes in listOf(
            JSONObject().put("font_scale", 1.3).put("app_language", "en"),
            JSONObject().put("global_user_name", "不应写入"),
            JSONObject().put("background_image_uri", "/unreadable/test.png").put("show_model_name", true),
        )) {
            assertFalse(update(before.revision, changes).success)
            assertEquals(before.revision, show().revision)
        }
    }

    @Test
    fun importedAvatarSurvivesOriginalDeletionAndClearReleasesOnlyItsCopy() = runBlocking {
        val source = File(directory, "avatar.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val result = update(show().revision, JSONObject().put("custom_ai_avatar_uri", source.absolutePath))
        assertTrue(result.error, result.success)
        val saved = result.result as ThemeSettingsResultData
        val imported = File(requireNotNull(Uri.parse(saved.values.getValue("custom_ai_avatar_uri").jsonPrimitive.content).path))
        assertNotEquals(source.canonicalFile, imported.canonicalFile)
        assertTrue(source.delete())
        assertTrue(imported.isFile)
        assertTrue(update(saved.revision, JSONObject().put("custom_ai_avatar_uri", JSONObject.NULL)).success)
        assertFalse(imported.exists())
    }

    @Test
    fun currentTargetUsesTrustedCallerWithoutActivatingIt() = runBlocking {
        val result = execute("get_theme_settings", mapOf("__operit_package_caller_card_id" to cardId))
        assertTrue(result.error, result.success)
        assertEquals(cardId, (result.result as ThemeSettingsResultData).targetId)
        assertEquals(originalPrompt, prompts.getActivePrompt())
    }

    @Test
    fun groupConversationUsesItsGroupThemeInsteadOfTheSpeakingCard() = runBlocking {
        val groups = CharacterGroupCardManager.getInstance(context)
        val groupId = groups.createCharacterGroupCard(CharacterGroupCard("", "主题命令组-" + UUID.randomUUID()))
        val dao = AppDatabase.getDatabase(context).chatDao()
        val chat = ChatEntity(title = "主题命令隔离会话", characterGroupId = groupId)
        try {
            dao.insertChat(chat)
            prompts.setActivePrompt(originalPrompt)
            val result = execute("get_theme_settings", mapOf(
                "__operit_package_chat_id" to chat.id,
                "__operit_package_caller_card_id" to cardId,
            ))
            assertTrue(result.error, result.success)
            val values = result.result as ThemeSettingsResultData
            assertEquals("group", values.targetType)
            assertEquals(groupId, values.targetId)
            assertEquals(originalPrompt, prompts.getActivePrompt())
        } finally {
            dao.deleteChat(chat.id)
            prompts.setActivePrompt(originalPrompt)
            groups.deleteCharacterGroupCard(groupId)
        }
    }

    @Test
    fun changingMediaTypeCannotPersistAnImageAsAVideo() = runBlocking {
        val source = File(directory, "background.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try { source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val first = update(show().revision, JSONObject().put("background_image_uri", source.absolutePath))
        assertTrue(first.error, first.success)
        val before = show()
        assertFalse(update(before.revision, JSONObject().put("background_media_type", "video")).success)
        assertEquals(before.revision, show().revision)
    }

    @Test
    fun reEnablingAnUnavailableStoredFontDoesNotPartiallySave() = runBlocking {
        val target = ActivePrompt.CharacterCard(cardId)
        preferences.mutateThemeForPrompt(target) {
            it.withString("font_type", "file").withString("custom_font_path", File(directory, "missing.ttf").absolutePath)
                .withBoolean("use_custom_font", false)
        }
        val before = show()
        assertFalse(update(before.revision, JSONObject().put("use_custom_font", true).put("show_model_name", true)).success)
        assertEquals(before.revision, show().revision)
    }

    @Test
    fun globalNameUsesItsOwnRevisionAndPreservesTheCurrentAvatar() = runBlocking {
        val display = DisplayPreferencesManager.getInstance(context)
        val original = display.globalUserProfile()
        suspend fun profile(): ThemeSettingsResultData {
            val result = execute("get_theme_settings", mapOf("target_type" to "user"))
            assertTrue(result.error, result.success)
            return result.result as ThemeSettingsResultData
        }
        suspend fun patch(revision: String, changes: JSONObject): ToolResult =
            execute("set_theme_settings", mapOf(
                "target_type" to "user", "expected_revision" to revision, "changes" to changes.toString(),
            ))
        try {
            val before = profile()
            val name = "主题命令用户-${UUID.randomUUID()}"
            val result = patch(before.revision, JSONObject().put("global_user_name", name))
            assertTrue(result.error, result.success)
            val saved = result.result as ThemeSettingsResultData
            assertTrue(saved.saved && saved.verified)
            assertEquals(name, saved.values.getValue("global_user_name").jsonPrimitive.content)
            assertEquals(before.values.getValue("global_user_avatar_uri"), saved.values.getValue("global_user_avatar_uri"))
            assertFalse(patch(before.revision, JSONObject().put("global_user_name", "过期写入")).success)
            assertFalse(patch(saved.revision, JSONObject().put("font_scale", 1.4)).success)
            assertEquals(saved.revision, profile().revision)
            assertTrue(patch(saved.revision, JSONObject().put("global_user_name", "   ")).success)
            assertFalse(display.globalUserProfile().containsKey("global_user_name"))
            assertEquals(original["global_user_avatar_uri"], display.globalUserProfile()["global_user_avatar_uri"])
        } finally {
            // 只恢复本测试改过的名字，始终保留当前头像引用。
            display.mutateGlobalUserProfile { current ->
                val name = original["global_user_name"]
                if (name == null) current - "global_user_name" else current + ("global_user_name" to name)
            }
        }
    }

    @Test
    fun unknownTargetAndMissingRevisionFailBeforeWriting() = runBlocking {
        val before = show()
        assertFalse(execute("get_theme_settings", mapOf("target_type" to "card", "target_id" to "missing-${UUID.randomUUID()}")).success)
        assertFalse(execute("set_theme_settings", mapOf("target_type" to "card", "target_id" to cardId, "changes" to "{\"font_scale\":1.4}")).success)
        assertEquals(before.revision, show().revision)
    }
}
