package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.character.ActivePrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

class ActivePromptManager private constructor(context: Context) {

    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)
    private val userPreferencesManager = UserPreferencesManager.getInstance(context)
    private val themeOperations = ThemeTargetOperationCoordinator()

    val activePromptFlow: Flow<ActivePrompt> =
        combine(
            characterGroupCardManager.observeActiveCharacterGroupId(),
            characterCardManager.observeActiveCharacterCardId()
        ) { groupId, cardId ->
            when {
                !groupId.isNullOrBlank() -> ActivePrompt.CharacterGroup(groupId)
                !cardId.isNullOrBlank() -> ActivePrompt.CharacterCard(cardId)
                else -> ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeThemePreferenceSnapshotFlow: Flow<ThemePreferenceSnapshot> =
        activePromptFlow
            .flatMapLatest { prompt ->
                when (prompt) {
                    is ActivePrompt.CharacterGroup ->
                        userPreferencesManager.observeThemePreferenceSnapshot(
                            characterGroupId = prompt.id,
                        )

                    is ActivePrompt.CharacterCard ->
                        userPreferencesManager.observeThemePreferenceSnapshot(
                            characterCardId = prompt.id,
                        )
                }
            }
            .distinctUntilChanged()

    suspend fun getActivePrompt(): ActivePrompt = activePromptFlow.first()

    suspend fun setActivePrompt(prompt: ActivePrompt) {
        themeOperations.runTransition {
            when (prompt) {
                is ActivePrompt.CharacterGroup -> {
                    characterGroupCardManager.setActiveCharacterGroupCard(prompt.id)
                    characterCardManager.clearActiveCharacterCard()
                }
                is ActivePrompt.CharacterCard -> {
                    characterCardManager.setActiveCharacterCard(prompt.id)
                    characterGroupCardManager.setActiveCharacterGroupCard(null)
                }
            }
        }
    }

    internal suspend fun <T> runThemeTransition(action: suspend () -> T): T {
        return themeOperations.runTransition(action)
    }

    suspend fun mutateActiveThemeForPrompt(
        target: ActivePrompt,
        transform: (ThemePreferenceValues) -> ThemePreferenceValues,
    ) {
        themeOperations.runTransition {
            if (getActivePrompt() != target) return@runTransition
            userPreferencesManager.mutateThemeForPrompt(
                target = target,
                transform = transform,
            )
        }
    }

    suspend fun commitThemeDraft(
        target: ActivePrompt,
        values: ThemePreferenceValues,
    ) {
        themeOperations.runTransition {
            userPreferencesManager.replaceThemeForPrompt(
                target = target,
                values = values,
            )
        }
    }

    /** 草稿合并必须在读取当前持久值的同一笔事务内完成，冲突时不写入。 */
    suspend fun commitThemeDraftChanges(
        target: ActivePrompt,
        baseline: ThemePreferenceValues,
        values: ThemePreferenceValues,
        onThemeSaved: (ThemePreferenceValues) -> Unit = {},
    ): ThemePreferenceValues = themeOperations.runTransition {
        mergeThemeDraftChanges(target, baseline, values, onThemeSaved)
    }

    suspend fun applyThemeDraftChangesToAllCharacterCards(
        target: ActivePrompt.CharacterCard,
        baseline: ThemePreferenceValues,
        values: ThemePreferenceValues,
        onThemeSaved: (ThemePreferenceValues) -> Unit = {},
    ): ThemePreferenceValues = themeOperations.runTransition {
        val merged = mergeThemeDraftChanges(target, baseline, values, onThemeSaved)
        // 当前角色先通过冲突检查，再执行用户明确选择的全角色视觉覆盖。
        applyVisualThemeToAllCharacterCardsLocked(merged)
        merged
    }

    private suspend fun mergeThemeDraftChanges(
        target: ActivePrompt,
        baseline: ThemePreferenceValues,
        values: ThemePreferenceValues,
        onThemeSaved: (ThemePreferenceValues) -> Unit,
    ): ThemePreferenceValues = withContext(NonCancellable) {
        lateinit var merged: ThemePreferenceValues
        userPreferencesManager.mutateThemeForPrompt(target) { current ->
            ThemeDraftMerge.merge(baseline, values, current).also { merged = it }
        }
        // 落盘与会话确认不可被取消拆开，否则取消清理可能删除刚保存的素材。
        onThemeSaved(merged)
        merged
    }

    suspend fun resetThemeDraft(
        target: ActivePrompt,
        values: ThemePreferenceValues,
    ) {
        themeOperations.runTransition {
            userPreferencesManager.resetVisualThemeForPrompt(
                target = target,
                values = values,
            )
        }
    }

    suspend fun applyVisualThemeToAllCharacterCards(values: ThemePreferenceValues) {
        themeOperations.runTransition {
            applyVisualThemeToAllCharacterCardsLocked(values)
        }
    }

    private suspend fun applyVisualThemeToAllCharacterCardsLocked(values: ThemePreferenceValues) {
        val characterCardIds = characterCardManager.getAllCharacterCards()
            .map { it.id }
            .toMutableList()
        if (characterCardIds.none { it == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID }) {
            characterCardIds.add(0, CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        }
        userPreferencesManager.applyVisualThemeToCharacterCards(
            values = values,
            characterCardIds = characterCardIds,
        )
    }

    suspend fun saveAiAvatarForPrompt(target: ActivePrompt, avatarUri: String?) {
        themeOperations.runTransition {
            when (target) {
                is ActivePrompt.CharacterGroup ->
                    userPreferencesManager.saveAiAvatarForCharacterGroup(target.id, avatarUri)

                is ActivePrompt.CharacterCard ->
                    userPreferencesManager.saveAiAvatarForCharacterCard(target.id, avatarUri)
            }
        }
    }

    suspend fun saveCustomChatTitleForPrompt(target: ActivePrompt, title: String?) {
        themeOperations.runTransition {
            when (target) {
                is ActivePrompt.CharacterGroup ->
                    userPreferencesManager.saveCustomChatTitleForCharacterGroup(target.id, title)

                is ActivePrompt.CharacterCard ->
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(target.id, title)
            }
        }
    }

    suspend fun activateForChatBinding(characterCardName: String?, characterGroupId: String?) {
        val normalizedGroupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        if (!normalizedGroupId.isNullOrBlank()) {
            setActivePrompt(ActivePrompt.CharacterGroup(normalizedGroupId))
            return
        }

        val normalizedCardName = characterCardName?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedCardName != null) {
            val targetCard = characterCardManager.findCharacterCardByName(normalizedCardName)
            if (targetCard != null) {
                setActivePrompt(ActivePrompt.CharacterCard(targetCard.id))
                return
            }
        }

        setActivePrompt(ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID))
    }

    suspend fun resolveActiveCardIdForSend(): String {
        return when (val prompt = getActivePrompt()) {
            is ActivePrompt.CharacterCard -> prompt.id
            is ActivePrompt.CharacterGroup -> CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ActivePromptManager? = null

        fun getInstance(context: Context): ActivePromptManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivePromptManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
