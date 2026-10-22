package com.ai.assistance.operit.ui.features.settings.screens.theme.workbench

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.glass.GlassRegion
import com.ai.assistance.operit.data.preferences.transparency.ThemeTransparencyPreferences
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.settings.components.ChatStyleOption
import com.ai.assistance.operit.ui.features.settings.components.bubble.BubbleImageStyleEditor
import com.ai.assistance.operit.ui.features.settings.components.transparency.BackgroundTransparencySlider
import com.ai.assistance.operit.ui.features.settings.screens.theme.ThemeEditorSession
import com.ai.assistance.operit.ui.features.settings.sections.BubbleFontStyleEditor
import com.ai.assistance.operit.ui.features.settings.sections.NineSliceSourcePreviewCard
import com.ai.assistance.operit.ui.features.settings.sections.glass.GlassParametersEntry
import com.ai.assistance.operit.ui.theme.*

@Composable
internal fun ThemeSettingsChatStyleSection(
    cardColors: CardColors,
    editorSession: ThemeEditorSession,
    chatStyleInput: String,
    inputStyleInput: String,
    bubbleShowAvatarInput: Boolean,
    bubbleWideLayoutEnabledInput: Boolean,
    cursorUserBubbleFollowThemeInput: Boolean,
    cursorUserBubbleLiquidGlassInput: Boolean,
    cursorUserBubbleWaterGlassInput: Boolean,
    bubbleUserBubbleLiquidGlassInput: Boolean,
    bubbleUserBubbleWaterGlassInput: Boolean,
    bubbleAiBubbleLiquidGlassInput: Boolean,
    bubbleAiBubbleWaterGlassInput: Boolean,
    cursorUserBubbleColorInput: Int,
    bubbleUserBubbleColorInput: Int,
    bubbleAiBubbleColorInput: Int,
    bubbleUserTextColorInput: Int,
    bubbleAiTextColorInput: Int,
    bubbleUserUseCustomFontInput: Boolean,
    bubbleUserFontTypeInput: String,
    bubbleUserSystemFontNameInput: String,
    bubbleUserCustomFontPathInput: String?,
    onPickBubbleUserFont: () -> Unit,
    bubbleAiUseCustomFontInput: Boolean,
    bubbleAiFontTypeInput: String,
    bubbleAiSystemFontNameInput: String,
    bubbleAiCustomFontPathInput: String?,
    onPickBubbleAiFont: () -> Unit,
    previewUserAvatarUri: String?,
    previewAiAvatarUri: String?,
    onShowColorPicker: (String) -> Unit,
    bubbleUserUseImageInput: Boolean,
    bubbleAiUseImageInput: Boolean,
    bubbleUserImageUriInput: String?,
    bubbleAiImageUriInput: String?,
    onPickBubbleUserImage: () -> Unit,
    onPickBubbleAiImage: () -> Unit,
    onClearBubbleUserImage: () -> Unit,
    onClearBubbleAiImage: () -> Unit,
    bubbleUserImageCropLeftInput: Float,
    bubbleUserImageCropTopInput: Float,
    bubbleUserImageCropRightInput: Float,
    bubbleUserImageCropBottomInput: Float,
    bubbleUserImageRepeatStartInput: Float,
    bubbleUserImageRepeatEndInput: Float,
    bubbleUserImageRepeatYStartInput: Float,
    bubbleUserImageRepeatYEndInput: Float,
    bubbleUserImageScaleInput: Float,
    bubbleAiImageCropLeftInput: Float,
    bubbleAiImageCropTopInput: Float,
    bubbleAiImageCropRightInput: Float,
    bubbleAiImageCropBottomInput: Float,
    bubbleAiImageRepeatStartInput: Float,
    bubbleAiImageRepeatEndInput: Float,
    bubbleAiImageRepeatYStartInput: Float,
    bubbleAiImageRepeatYEndInput: Float,
    bubbleAiImageScaleInput: Float,
    bubbleImageRenderModeInput: String,
    bubbleUserRoundedCornersEnabledInput: Boolean,
    bubbleAiRoundedCornersEnabledInput: Boolean,
    bubbleUserContentPaddingLeftInput: Float,
    bubbleUserContentPaddingRightInput: Float,
    bubbleAiContentPaddingLeftInput: Float,
    bubbleAiContentPaddingRightInput: Float,
    showInputStyleControls: Boolean = true,
    onAvatarSettings: () -> Unit,
) {
    val values by editorSession.values.collectAsState()
    var opened by remember { mutableStateOf<String?>(null) }
    var nested by remember { mutableStateOf<String?>(null) }
    val previewUserImageStyle =
        remember(
            bubbleUserBubbleLiquidGlassInput,
            bubbleUserBubbleWaterGlassInput,
            bubbleUserUseImageInput,
            bubbleUserImageUriInput,
            bubbleUserImageCropLeftInput,
            bubbleUserImageCropTopInput,
            bubbleUserImageCropRightInput,
            bubbleUserImageCropBottomInput,
            bubbleUserImageRepeatStartInput,
            bubbleUserImageRepeatEndInput,
            bubbleUserImageRepeatYStartInput,
            bubbleUserImageRepeatYEndInput,
            bubbleUserImageScaleInput,
            bubbleImageRenderModeInput,
        ) {
            val imageUri = bubbleUserImageUriInput
            if (
                !bubbleUserBubbleLiquidGlassInput &&
                    !bubbleUserBubbleWaterGlassInput &&
                    bubbleUserUseImageInput &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleUserImageCropLeftInput,
                    cropTopRatio = bubbleUserImageCropTopInput,
                    cropRightRatio = bubbleUserImageCropRightInput,
                    cropBottomRatio = bubbleUserImageCropBottomInput,
                    repeatXStartRatio = bubbleUserImageRepeatStartInput,
                    repeatXEndRatio = bubbleUserImageRepeatEndInput,
                    repeatYStartRatio = bubbleUserImageRepeatYStartInput,
                    repeatYEndRatio = bubbleUserImageRepeatYEndInput,
                    imageScale = bubbleUserImageScaleInput,
                    renderMode = bubbleImageRenderModeInput,
                )
            } else {
                null
            }
        }
    val previewAiImageStyle =
        remember(
            bubbleAiBubbleLiquidGlassInput,
            bubbleAiBubbleWaterGlassInput,
            bubbleAiUseImageInput,
            bubbleAiImageUriInput,
            bubbleAiImageCropLeftInput,
            bubbleAiImageCropTopInput,
            bubbleAiImageCropRightInput,
            bubbleAiImageCropBottomInput,
            bubbleAiImageRepeatStartInput,
            bubbleAiImageRepeatEndInput,
            bubbleAiImageRepeatYStartInput,
            bubbleAiImageRepeatYEndInput,
            bubbleAiImageScaleInput,
            bubbleImageRenderModeInput,
        ) {
            val imageUri = bubbleAiImageUriInput
            if (
                !bubbleAiBubbleLiquidGlassInput &&
                    !bubbleAiBubbleWaterGlassInput &&
                    bubbleAiUseImageInput &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleAiImageCropLeftInput,
                    cropTopRatio = bubbleAiImageCropTopInput,
                    cropRightRatio = bubbleAiImageCropRightInput,
                    cropBottomRatio = bubbleAiImageCropBottomInput,
                    repeatXStartRatio = bubbleAiImageRepeatStartInput,
                    repeatXEndRatio = bubbleAiImageRepeatEndInput,
                    repeatYStartRatio = bubbleAiImageRepeatYStartInput,
                    repeatYEndRatio = bubbleAiImageRepeatYEndInput,
                    imageScale = bubbleAiImageScaleInput,
                    renderMode = bubbleImageRenderModeInput,
                )
            } else {
                null
            }
        }
    val previewChatStyle =
        if (chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE) {
            ChatStyle.BUBBLE
        } else {
            ChatStyle.CURSOR
        }

    val isCursorStyle = previewChatStyle == ChatStyle.CURSOR
    val userBaseColor =
        when {
            isCursorStyle && cursorUserBubbleFollowThemeInput ->
                MaterialTheme.colorScheme.primaryContainer
            isCursorStyle -> Color(cursorUserBubbleColorInput)
            else -> Color(bubbleUserBubbleColorInput)
        }
    val aiBaseColor =
        if (isCursorStyle) MaterialTheme.colorScheme.surface else Color(bubbleAiBubbleColorInput)
    val userOpacityKey =
        if (isCursorStyle) ThemeTransparencyPreferences.CURSOR_USER
        else ThemeTransparencyPreferences.BUBBLE_USER
    val aiOpacityKey =
        if (isCursorStyle) ThemeTransparencyPreferences.CURSOR_AI
        else ThemeTransparencyPreferences.BUBBLE_AI
    val userGlassEnabled =
        if (isCursorStyle) {
            (cursorUserBubbleWaterGlassInput && isWaterGlassSupported()) ||
                (cursorUserBubbleLiquidGlassInput && isLiquidGlassSupported())
        } else {
            (bubbleUserBubbleWaterGlassInput && isWaterGlassSupported()) ||
                (bubbleUserBubbleLiquidGlassInput && isLiquidGlassSupported())
        }
    val aiGlassEnabled =
        !isCursorStyle &&
            ((bubbleAiBubbleWaterGlassInput && isWaterGlassSupported()) ||
                (bubbleAiBubbleLiquidGlassInput && isLiquidGlassSupported()))
    val legacyUserOpacity =
        when {
            userGlassEnabled -> values.requiredFloat("glass_tint_opacity")
            !isCursorStyle && previewUserImageStyle != null -> 1f
            else -> userBaseColor.alpha
        }
    val legacyAiOpacity =
        when {
            isCursorStyle -> 0f
            aiGlassEnabled -> values.requiredFloat("glass_tint_opacity")
            previewAiImageStyle != null -> 1f
            else -> aiBaseColor.alpha
        }
    val userOpacity =
        ThemeTransparencyPreferences.opacity(values, userOpacityKey, legacyUserOpacity)
    val aiOpacity = ThemeTransparencyPreferences.opacity(values, aiOpacityKey, legacyAiOpacity)
    ThemeWorkbenchCard(
        stringResource(R.string.chat_style_title),
        icon = Icons.Default.ChatBubbleOutline,
    ) {
        ThemeWorkbenchSegments(
            options =
                listOf(
                    UserPreferencesManager.CHAT_STYLE_BUBBLE to
                        stringResource(R.string.chat_style_bubble),
                    UserPreferencesManager.CHAT_STYLE_CURSOR to
                        stringResource(R.string.chat_style_cursor),
                ),
            selected = chatStyleInput,
            onSelected = { editorSession.setString("chat_style", it) },
        )
        if (chatStyleInput == UserPreferencesManager.CHAT_STYLE_BUBBLE) {
            ThemeWorkbenchSwitchRow(
                stringResource(R.string.chat_style_bubble_show_avatar),
                bubbleShowAvatarInput,
            ) {
                editorSession.setBoolean("bubble_show_avatar", it)
            }
            ThemeWorkbenchSwitchRow(
                stringResource(R.string.chat_style_bubble_wide_layout),
                bubbleWideLayoutEnabledInput,
                subtitle = stringResource(R.string.theme_workbench_wide_hint),
            ) {
                editorSession.setBoolean("bubble_wide_layout_enabled", it)
            }
        }
    }
    ThemeWorkbenchCard(
        stringResource(R.string.theme_workbench_message_appearance),
        icon = Icons.Default.Palette,
    ) {
        ThemeWorkbenchRow(
            stringResource(R.string.theme_workbench_user_message),
            subtitle =
                stringResource(
                    when {
                        !isCursorStyle && bubbleUserUseImageInput && !userGlassEnabled ->
                            R.string.chat_style_bubble_user_image_title
                        (if (isCursorStyle) cursorUserBubbleWaterGlassInput
                        else bubbleUserBubbleWaterGlassInput) ->
                            R.string.theme_workbench_water_glass
                        (if (isCursorStyle) cursorUserBubbleLiquidGlassInput
                        else bubbleUserBubbleLiquidGlassInput) ->
                            R.string.theme_workbench_liquid_glass
                        else -> R.string.theme_workbench_default
                    }
                ),
            leadingContent = {
                ThemeWorkbenchColorPair(
                    userBaseColor,
                    if (isCursorStyle) MaterialTheme.colorScheme.onSurface
                    else Color(bubbleUserTextColorInput),
                )
            },
        ) {
            opened = "user"
        }
        ThemeWorkbenchDivider()
        ThemeWorkbenchRow(
            stringResource(R.string.theme_workbench_ai_message),
            subtitle =
                stringResource(
                    when {
                        !isCursorStyle && bubbleAiUseImageInput && !aiGlassEnabled ->
                            R.string.chat_style_bubble_ai_image_title
                        !isCursorStyle && bubbleAiBubbleWaterGlassInput ->
                            R.string.theme_workbench_water_glass
                        !isCursorStyle && bubbleAiBubbleLiquidGlassInput ->
                            R.string.theme_workbench_liquid_glass
                        else -> R.string.theme_workbench_default
                    }
                ),
            leadingContent = {
                ThemeWorkbenchColorPair(
                    aiBaseColor,
                    if (isCursorStyle) MaterialTheme.colorScheme.onSurface
                    else Color(bubbleAiTextColorInput),
                )
            },
        ) {
            opened = "ai"
        }
        ThemeWorkbenchDivider()
        ThemeWorkbenchRow(
            stringResource(R.string.theme_workbench_avatar_identity),
            leading = Icons.Default.AccountCircle,
            onClick = onAvatarSettings,
        )
    }
    if (opened == "user")
        ThemeWorkbenchDialog(
            stringResource(R.string.theme_workbench_user_message),
            {
                opened = null
                nested = null
            },
        ) {
            ThemeWorkbenchCard(
                stringResource(R.string.theme_workbench_colors_text),
                icon = Icons.Default.Palette,
            ) {
                if (!isCursorStyle) {
                    ThemeWorkbenchColorRow(
                        stringResource(R.string.theme_workbench_background_color),
                        Color(bubbleUserBubbleColorInput),
                    ) {
                        onShowColorPicker("bubbleUserBubble")
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchColorRow(
                        stringResource(R.string.theme_workbench_text_color),
                        Color(bubbleUserTextColorInput),
                    ) {
                        onShowColorPicker("bubbleUserText")
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchRow(
                        stringResource(R.string.theme_workbench_message_font),
                        value =
                            themeWorkbenchFontLabel(
                                bubbleUserUseCustomFontInput,
                                bubbleUserFontTypeInput,
                                bubbleUserSystemFontNameInput,
                                bubbleUserCustomFontPathInput,
                                message = true,
                            ),
                    ) {
                        nested = "font"
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchSwitchRow(
                        stringResource(R.string.chat_style_bubble_rounded_corners),
                        bubbleUserRoundedCornersEnabledInput,
                    ) {
                        editorSession.setBoolean("bubble_rounded_corners_enabled", it)
                    }
                }
                if (isCursorStyle) {
                    ThemeWorkbenchSwitchRow(
                        stringResource(R.string.chat_style_cursor_user_follow_theme),
                        cursorUserBubbleFollowThemeInput,
                    ) {
                        editorSession.setBoolean("cursor_user_bubble_follow_theme", it)
                    }
                    if (!cursorUserBubbleFollowThemeInput)
                        ThemeWorkbenchColorRow(
                            stringResource(R.string.chat_style_cursor_user_bubble_color),
                            Color(cursorUserBubbleColorInput),
                        ) {
                            onShowColorPicker("cursorUserBubble")
                        }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    BackgroundTransparencySlider(
                        stringResource(R.string.theme_user_background_transparency),
                        userOpacity,
                    ) {
                        editorSession.setFloat(userOpacityKey, it)
                    }
                }
            }
            ThemeWorkbenchCard(
                stringResource(R.string.theme_workbench_background_texture),
                icon = Icons.Default.BlurOn,
            ) {
                ThemeWorkbenchTextureEditor(
                    editorSession,
                    (if (isCursorStyle) "cursor_user_bubble" else "bubble_user_bubble"),
                    (if (isCursorStyle) cursorUserBubbleLiquidGlassInput
                    else bubbleUserBubbleLiquidGlassInput),
                    (if (isCursorStyle) cursorUserBubbleWaterGlassInput
                    else bubbleUserBubbleWaterGlassInput),
                )
                ThemeWorkbenchDivider()
                GlassParametersEntry(editorSession, GlassRegion.USER_MESSAGE)
            }
            if (!isCursorStyle)
                ThemeWorkbenchCard(
                    stringResource(R.string.theme_workbench_image_bubble),
                    icon = Icons.Default.Image,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        BubbleImageStyleEditor(
                            title =
                                stringResource(id = R.string.chat_style_bubble_user_image_title),
                            enabled =
                                !bubbleUserBubbleLiquidGlassInput &&
                                    !bubbleUserBubbleWaterGlassInput &&
                                    bubbleUserUseImageInput,
                            switchEnabled =
                                !bubbleUserBubbleLiquidGlassInput &&
                                    !bubbleUserBubbleWaterGlassInput,
                            description =
                                stringResource(id = R.string.chat_style_bubble_image_desc),
                            onEnabledChange = {
                                editorSession.setBoolean("bubble_user_use_image", it)
                            },
                            imageUri = bubbleUserImageUriInput,
                            onPickImage = onPickBubbleUserImage,
                            onClearImage = onClearBubbleUserImage,
                            cropLeft = bubbleUserImageCropLeftInput,
                            onCropLeftChange = {
                                editorSession.setFloat(
                                    "bubble_user_image_crop_left",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropTop = bubbleUserImageCropTopInput,
                            onCropTopChange = {
                                editorSession.setFloat(
                                    "bubble_user_image_crop_top",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropRight = bubbleUserImageCropRightInput,
                            onCropRightChange = {
                                editorSession.setFloat(
                                    "bubble_user_image_crop_right",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropBottom = bubbleUserImageCropBottomInput,
                            onCropBottomChange = {
                                editorSession.setFloat(
                                    "bubble_user_image_crop_bottom",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            repeatXStart = bubbleUserImageRepeatStartInput,
                            repeatXEnd = bubbleUserImageRepeatEndInput,
                            repeatYStart = bubbleUserImageRepeatYStartInput,
                            repeatYEnd = bubbleUserImageRepeatYEndInput,
                            onStretchRegionChange = { horizontal, vertical ->
                                editorSession.update { values ->
                                    values
                                        .withFloat(
                                            "bubble_user_image_repeat_start",
                                            horizontal.start,
                                        )
                                        .withFloat(
                                            "bubble_user_image_repeat_end",
                                            horizontal.endInclusive,
                                        )
                                        .withFloat(
                                            "bubble_user_image_repeat_y_start",
                                            vertical.start,
                                        )
                                        .withFloat(
                                            "bubble_user_image_repeat_y_end",
                                            vertical.endInclusive,
                                        )
                                }
                            },
                            imageScale = bubbleUserImageScaleInput,
                            onImageScaleChange = {
                                editorSession.setFloat(
                                    "bubble_user_image_scale",
                                    it.coerceIn(0.2f, 3f),
                                )
                            },
                            contentPaddingLeft = bubbleUserContentPaddingLeftInput,
                            contentPaddingRight = bubbleUserContentPaddingRightInput,
                            onContentPaddingChange = { left, right ->
                                editorSession.update { values ->
                                    values
                                        .withFloat("bubble_content_padding_left", left)
                                        .withFloat("bubble_content_padding_right", right)
                                }
                            },
                        )
                        if (previewUserImageStyle != null)
                            NineSliceSourcePreviewCard(
                                stringResource(R.string.chat_style_preview_user_source_title),
                                previewUserImageStyle,
                            )
                        Text(
                            stringResource(R.string.chat_style_bubble_image_render_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                    R.string.chat_style_bubble_image_render_mode_nine_patch to
                                        UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                                    R.string.chat_style_bubble_image_render_mode_tiled to
                                        UserPreferencesManager
                                            .BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                                )
                                .forEach { (label, mode) ->
                                    ChatStyleOption(
                                        stringResource(label),
                                        bubbleImageRenderModeInput == mode,
                                        Modifier.weight(1f),
                                    ) {
                                        editorSession.setString("bubble_image_render_mode", mode)
                                    }
                                }
                        }
                    }
                }
        }
    if (opened == "user" && nested == "font")
        ThemeWorkbenchDialog(stringResource(R.string.theme_font_settings), { nested = null }) {
            Column {
                BubbleFontStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_user_font_title),
                    useCustomFont = bubbleUserUseCustomFontInput,
                    onUseCustomFontChange = {
                        editorSession.setBoolean("bubble_user_use_custom_font", it)
                    },
                    fontType = bubbleUserFontTypeInput,
                    onFontTypeChange = { editorSession.setString("bubble_user_font_type", it) },
                    systemFontName = bubbleUserSystemFontNameInput,
                    onSystemFontNameChange = {
                        editorSession.setString("bubble_user_system_font_name", it)
                    },
                    customFontPath = bubbleUserCustomFontPathInput,
                    onPickFont = onPickBubbleUserFont,
                    onClearFont = {
                        editorSession.setOptionalString("bubble_user_custom_font_path", null)
                    },
                )
            }
        }
    if (opened == "ai")
        ThemeWorkbenchDialog(
            stringResource(R.string.theme_workbench_ai_message),
            {
                opened = null
                nested = null
            },
        ) {
            ThemeWorkbenchCard(
                stringResource(R.string.theme_workbench_colors_text),
                icon = Icons.Default.Palette,
            ) {
                if (!isCursorStyle) {
                    ThemeWorkbenchColorRow(
                        stringResource(R.string.theme_workbench_background_color),
                        Color(bubbleAiBubbleColorInput),
                    ) {
                        onShowColorPicker("bubbleAiBubble")
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchColorRow(
                        stringResource(R.string.theme_workbench_text_color),
                        Color(bubbleAiTextColorInput),
                    ) {
                        onShowColorPicker("bubbleAiText")
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchRow(
                        stringResource(R.string.theme_workbench_message_font),
                        value =
                            themeWorkbenchFontLabel(
                                bubbleAiUseCustomFontInput,
                                bubbleAiFontTypeInput,
                                bubbleAiSystemFontNameInput,
                                bubbleAiCustomFontPathInput,
                                message = true,
                            ),
                    ) {
                        nested = "font"
                    }
                    ThemeWorkbenchDivider()
                    ThemeWorkbenchSwitchRow(
                        stringResource(R.string.chat_style_bubble_rounded_corners),
                        bubbleAiRoundedCornersEnabledInput,
                    ) {
                        editorSession.setBoolean("bubble_ai_rounded_corners_enabled", it)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    BackgroundTransparencySlider(
                        stringResource(R.string.theme_ai_background_transparency),
                        aiOpacity,
                    ) {
                        editorSession.setFloat(aiOpacityKey, it)
                    }
                }
            }
            if (!isCursorStyle) {
                ThemeWorkbenchCard(
                    stringResource(R.string.theme_workbench_background_texture),
                    icon = Icons.Default.BlurOn,
                ) {
                    ThemeWorkbenchTextureEditor(
                        editorSession,
                        "bubble_ai_bubble",
                        bubbleAiBubbleLiquidGlassInput,
                        bubbleAiBubbleWaterGlassInput,
                    )
                    ThemeWorkbenchDivider()
                    GlassParametersEntry(editorSession, GlassRegion.AI_MESSAGE)
                }
            }
            if (!isCursorStyle)
                ThemeWorkbenchCard(
                    stringResource(R.string.theme_workbench_image_bubble),
                    icon = Icons.Default.Image,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        BubbleImageStyleEditor(
                            title = stringResource(id = R.string.chat_style_bubble_ai_image_title),
                            enabled =
                                !bubbleAiBubbleLiquidGlassInput &&
                                    !bubbleAiBubbleWaterGlassInput &&
                                    bubbleAiUseImageInput,
                            switchEnabled =
                                !bubbleAiBubbleLiquidGlassInput && !bubbleAiBubbleWaterGlassInput,
                            description =
                                if (
                                    bubbleAiBubbleLiquidGlassInput || bubbleAiBubbleWaterGlassInput
                                ) {
                                    stringResource(
                                        id = R.string.chat_style_bubble_ai_image_disabled_by_glass
                                    )
                                } else {
                                    stringResource(id = R.string.chat_style_bubble_image_desc)
                                },
                            onEnabledChange = {
                                editorSession.setBoolean("bubble_ai_use_image", it)
                            },
                            imageUri = bubbleAiImageUriInput,
                            onPickImage = onPickBubbleAiImage,
                            onClearImage = onClearBubbleAiImage,
                            cropLeft = bubbleAiImageCropLeftInput,
                            onCropLeftChange = {
                                editorSession.setFloat(
                                    "bubble_ai_image_crop_left",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropTop = bubbleAiImageCropTopInput,
                            onCropTopChange = {
                                editorSession.setFloat(
                                    "bubble_ai_image_crop_top",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropRight = bubbleAiImageCropRightInput,
                            onCropRightChange = {
                                editorSession.setFloat(
                                    "bubble_ai_image_crop_right",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            cropBottom = bubbleAiImageCropBottomInput,
                            onCropBottomChange = {
                                editorSession.setFloat(
                                    "bubble_ai_image_crop_bottom",
                                    it.coerceIn(0f, 0.45f),
                                )
                            },
                            repeatXStart = bubbleAiImageRepeatStartInput,
                            repeatXEnd = bubbleAiImageRepeatEndInput,
                            repeatYStart = bubbleAiImageRepeatYStartInput,
                            repeatYEnd = bubbleAiImageRepeatYEndInput,
                            onStretchRegionChange = { horizontal, vertical ->
                                editorSession.update { values ->
                                    values
                                        .withFloat("bubble_ai_image_repeat_start", horizontal.start)
                                        .withFloat(
                                            "bubble_ai_image_repeat_end",
                                            horizontal.endInclusive,
                                        )
                                        .withFloat("bubble_ai_image_repeat_y_start", vertical.start)
                                        .withFloat(
                                            "bubble_ai_image_repeat_y_end",
                                            vertical.endInclusive,
                                        )
                                }
                            },
                            imageScale = bubbleAiImageScaleInput,
                            onImageScaleChange = {
                                editorSession.setFloat(
                                    "bubble_ai_image_scale",
                                    it.coerceIn(0.2f, 3f),
                                )
                            },
                            contentPaddingLeft = bubbleAiContentPaddingLeftInput,
                            contentPaddingRight = bubbleAiContentPaddingRightInput,
                            onContentPaddingChange = { left, right ->
                                editorSession.update { values ->
                                    values
                                        .withFloat("bubble_ai_content_padding_left", left)
                                        .withFloat("bubble_ai_content_padding_right", right)
                                }
                            },
                        )
                        if (previewAiImageStyle != null)
                            NineSliceSourcePreviewCard(
                                stringResource(R.string.chat_style_preview_ai_source_title),
                                previewAiImageStyle,
                            )
                        Text(
                            stringResource(R.string.chat_style_bubble_image_render_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                    R.string.chat_style_bubble_image_render_mode_nine_patch to
                                        UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_NINE_PATCH,
                                    R.string.chat_style_bubble_image_render_mode_tiled to
                                        UserPreferencesManager
                                            .BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
                                )
                                .forEach { (label, mode) ->
                                    ChatStyleOption(
                                        stringResource(label),
                                        bubbleImageRenderModeInput == mode,
                                        Modifier.weight(1f),
                                    ) {
                                        editorSession.setString("bubble_image_render_mode", mode)
                                    }
                                }
                        }
                    }
                }
        }
    if (opened == "ai" && nested == "font")
        ThemeWorkbenchDialog(stringResource(R.string.theme_font_settings), { nested = null }) {
            Column {
                BubbleFontStyleEditor(
                    title = stringResource(id = R.string.chat_style_bubble_ai_font_title),
                    useCustomFont = bubbleAiUseCustomFontInput,
                    onUseCustomFontChange = {
                        editorSession.setBoolean("bubble_ai_use_custom_font", it)
                    },
                    fontType = bubbleAiFontTypeInput,
                    onFontTypeChange = { editorSession.setString("bubble_ai_font_type", it) },
                    systemFontName = bubbleAiSystemFontNameInput,
                    onSystemFontNameChange = {
                        editorSession.setString("bubble_ai_system_font_name", it)
                    },
                    customFontPath = bubbleAiCustomFontPathInput,
                    onPickFont = onPickBubbleAiFont,
                    onClearFont = {
                        editorSession.setOptionalString("bubble_ai_custom_font_path", null)
                    },
                )
            }
        }
}
