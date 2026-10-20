package com.ai.assistance.operit.ui.features.settings.screens.theme

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.components.ColorPickerDialog
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsColorCustomizationSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsFontSection
import com.ai.assistance.operit.ui.features.settings.sections.ThemeSettingsThemeModeSection
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.FileUtils
import kotlinx.coroutines.launch

@Composable
internal fun ThemeSettingsBasicTab(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
) {
    val editorSession = shared.editorSession
    val values by editorSession.values.collectAsState()
    val pickGlobalFont = rememberGlobalFontPicker(
        context = shared.context,
        shared = shared,
    )

    ThemeSettingsThemeModeSection(
        cardColors = cardColors,
        editorSession = editorSession,
        useSystemThemeInput = values.requiredBoolean("use_system_theme"),
        themeModeInput = values.requiredString("theme_mode"),
    )

    ThemeSettingsBasicColorPanel(
        shared = shared,
        cardColors = cardColors,
        values = values,
    )

    ThemeSettingsFontSection(
        cardColors = cardColors,
        context = shared.context,
        editorSession = editorSession,
        useCustomFontInput = values.requiredBoolean("use_custom_font"),
        fontTypeInput = values.requiredString("font_type"),
        systemFontNameInput = values.requiredString("system_font_name"),
        customFontPathInput = values.string("custom_font_path"),
        fontScaleInput = values.requiredFloat("font_scale"),
        onPickFont = pickGlobalFont,
    )
}

@Composable
private fun rememberGlobalFontPicker(
    context: Context,
    shared: ThemeSettingsShared,
): () -> Unit {
    val editorSession = shared.editorSession
    val fontPickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                shared.scope.launch {
                    val extension = FileUtils.getFileExtension(context, uri)?.lowercase()
                    if (extension != null && (extension == "ttf" || extension == "otf" || extension == "ttc")) {
                        val internalUri =
                            FileUtils.copyFileToInternalStorage(context, uri, "custom_font")
                        if (internalUri != null) {
                            AppLogger.d("ThemeSettings", "Font file saved to: $internalUri")
                            val internalUriString = internalUri.toString()
                            editorSession.registerStagedAsset(internalUriString)
                            editorSession.update { current ->
                                current
                                    .withString("custom_font_path", internalUriString)
                                    .withString(
                                        "font_type",
                                        UserPreferencesManager.FONT_TYPE_FILE,
                                    )
                            }
                            OKToast.success(
                                context,
                                context.getString(R.string.font_file_saved, extension),
                                OKToast.LENGTH_SHORT,
                            ).show()
                        } else {
                            OKToast.error(
                                context,
                                context.getString(R.string.font_file_save_failed),
                                OKToast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        OKToast.warning(
                            context,
                            context.getString(R.string.unsupported_font_format),
                            OKToast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    return { fontPickerLauncher.launch("*/*") }
}

@Composable
private fun ThemeSettingsBasicColorPanel(
    shared: ThemeSettingsShared,
    cardColors: CardColors,
    values: ThemePreferenceValues,
) {
    val editorSession = shared.editorSession
    val defaultPrimaryColor = Color.Magenta.toArgb()
    val defaultSecondaryColor = Color.Blue.toArgb()
    val useCustomColorsInput = values.requiredBoolean("use_custom_colors")
    val primaryColorInput = values.int("custom_primary_color") ?: defaultPrimaryColor
    val secondaryColorInput = values.int("custom_secondary_color") ?: defaultSecondaryColor
    val onColorModeInput = values.requiredString("on_color_mode")
    val recentColors by editorSession.recentColorsFlow.collectAsState(initial = emptyList())
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorPickerMode by remember { mutableStateOf("primary") }

    ThemeSettingsColorCustomizationSection(
        cardColors = cardColors,
        editorSession = editorSession,
        useCustomColorsInput = useCustomColorsInput,
        primaryColorInput = primaryColorInput,
        secondaryColorInput = secondaryColorInput,
        onColorModeInput = onColorModeInput,
        onShowColorPicker = {
            currentColorPickerMode = it
            showColorPicker = true
        },
    )

    if (showColorPicker) {
        ColorPickerDialog(
            showColorPicker = showColorPicker,
            currentColorPickerMode = currentColorPickerMode,
            primaryColorInput = primaryColorInput,
            secondaryColorInput = secondaryColorInput,
            cursorUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleUserBubbleColorInput = MaterialTheme.colorScheme.primaryContainer.toArgb(),
            bubbleAiBubbleColorInput = MaterialTheme.colorScheme.surface.toArgb(),
            bubbleUserTextColorInput = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
            bubbleAiTextColorInput = MaterialTheme.colorScheme.onSurface.toArgb(),
            recentColors = recentColors,
            onColorSelected = { primary,
                secondary,
                _,
                _,
                _,
                _,
                _ ->
                updateDraftThemeColor(
                    shared = shared,
                    currentColorPickerMode = currentColorPickerMode,
                    primaryColor = primary,
                    secondaryColor = secondary,
                )
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

private fun updateDraftThemeColor(
    shared: ThemeSettingsShared,
    currentColorPickerMode: String,
    primaryColor: Int?,
    secondaryColor: Int?,
) {
    val selectedColor = primaryColor ?: secondaryColor
    val editorSession = shared.editorSession
    when (currentColorPickerMode) {
        "primary" -> primaryColor?.let { editorSession.setInt("custom_primary_color", it) }
        "secondary" -> secondaryColor?.let { editorSession.setInt("custom_secondary_color", it) }
    }
    selectedColor?.let { color ->
        shared.scope.launch { editorSession.addRecentColor(color) }
    }
}
